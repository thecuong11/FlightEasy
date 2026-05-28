package com.flighteasy.controller;

import org.apache.commons.codec.binary.Hex;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/test")
public class TestPaymentController {

    @GetMapping("/payment")
    public ResponseEntity<?> testPayment(
            @RequestParam("orderId") String orderId
    ) {
        long amount = 100_000_000L;
        String tmnCode = "2W31R0WS";
        String hashSecret = "OLPNWRYT1PMKAW82ESIZBYMRFZE199N3";

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", orderId);
        params.put("vnp_OrderInfo", "Thanh toan ve may bay - Manhnk test");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", "http://localhost:8080/api/v1/payments/vnpay/return");
        params.put("vnp_IpAddr", "127.0.0.1");
        params.put("vnp_CreateDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusHours(10).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        String hashData = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String securityHash = hmacSha512(hashSecret, hashData);
        params.put("vnp_SecureHash", securityHash);

        String queryString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String response = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html" + "?" + queryString;
        return ResponseEntity.ok().body(Collections.singletonMap("response", response));
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

}
