package com.flighteasy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flighteasy.entity.Booking;
import com.flighteasy.entity.Payment;
import com.flighteasy.enums.BookingStatus;
import com.flighteasy.enums.PaymentStatus;
import com.flighteasy.event.BookingConfirmedEvent;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.payment-url}")
    private String paymentUrl;

    public String createPaymentUrl(Booking booking, Payment payment, String returnUrl, String ipAddress) {
        long amount = booking.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", payment.getVnpTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan ve may bay - " + booking.getPnrCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress != null ? ipAddress : "127.0.0.1");
        params.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        String hashData = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String securityHash = hmacSha512(hashSecret, hashData);
        params.put("vnp_SecureHash", securityHash);

        String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return paymentUrl + "?" + queryString;
    }

    public String processIPN(Map<String, String> params) {
        String receiveHash = params.get("vnp_SecureHash");
        String responseCode = params.get("vnp_ResponseCode");
        String txnRef = params.get("vnp_TxnRef");
        long amount = Long.parseLong(params.get("vnp_Amount"));

        if (!verifySignature(params, receiveHash)) {
            log.warn("IPN: Invalid signature for txnRef={}", txnRef);
            return buildIPNResponse("97", "Invalid signature");
        }

        Payment payment = paymentRepository.findByVnpTxnRef(txnRef).orElse(null);
        if (payment == null) {
            return buildIPNResponse("01", "Order not found");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return buildIPNResponse("02", "Order already confirmed");
        }

        if (!payment.getAmountVnpay().equals(amount)) {
            log.error("IPN: Amount mismatch. Expected={}, Got={}", payment.getAmountVnpay(), amount);
            return buildIPNResponse("04", "Invalid amount");
        }

        try {
            payment.setRawIpnData(new ObjectMapper().writeValueAsString(params));
        } catch (Exception ignored) {}

        payment.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        payment.setVnpResponseCode(responseCode);
        payment.setVnpBankCode(params.get("vnp_BankCode"));
        payment.setVnpCardType(params.get("vnp_CardType"));

        if ("00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            confirmBooking(payment.getBooking());
            eventPublisher.publishEvent(new BookingConfirmedEvent(payment.getBooking()));
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);

        return buildIPNResponse("00", "Confirm success");
    }

    public boolean verifyReturnUrl(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        return verifySignature(params, receivedHash);
    }

    private boolean verifySignature(Map<String, String> params, String receivedHash) {
        log.info("=====hashSecret===== [{}]", hashSecret);
        log.info("===receivedHash=== [{}]", receivedHash);
        String hashData = params.entrySet().stream()
                .filter(e -> !e.getKey().equals("vnp_SecureHash") && !e.getKey().equals("vnp_SecureHashType"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String expectedHash = hmacSha512(hashSecret, hashData);
        log.info("===expectedHash=== [{}]", expectedHash);

        return expectedHash.equals(receivedHash);
    }

    private String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Hex.encodeHexString(hmacData);
        } catch (Exception ex){
            throw new RuntimeException("Cannot generate HMAC", ex);
        }
    }

    private String buildIPNResponse(String rspCode, String message) {
        return "{\"RspCode\":\"" + rspCode + "\",\"Message\":\"" + message + "\"}";
    }

    private void confirmBooking(Booking booking) {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(LocalDateTime.now());
        booking.setExpiresAt(null);
        bookingRepository.save(booking);
    }
}
