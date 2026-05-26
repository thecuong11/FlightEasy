package com.flighteasy.service;

import com.flighteasy.dto.CreatePaymentRequest;
import com.flighteasy.dto.CreatePaymentResponse;
import com.flighteasy.dto.PaymentStatusResponse;
import com.flighteasy.entity.Booking;
import com.flighteasy.entity.Payment;
import com.flighteasy.enums.BookingStatus;
import com.flighteasy.enums.PaymentStatus;
import com.flighteasy.exception.custom.InvalidPaymentException;
import com.flighteasy.exception.custom.NotFoundException;
import com.flighteasy.repository.BookingRepository;
import com.flighteasy.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final VNPayService vNPayService;

    @Transactional
    public CreatePaymentResponse createPaymentLink(CreatePaymentRequest request, String clientIp) {
        Booking booking = bookingRepository.findByPnrCode(request.pnrCode())
                .orElseThrow(() -> new RuntimeException("Booking không tồn tại"));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidPaymentException("Booking không ở trạng thái chờ thanh toán");
        }

        if (booking.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidPaymentException("Booking đã hết hạn thanh toán");
        }

        String txnRef = request.pnrCode() + "-" + System.currentTimeMillis();
        long amountVnpay = booking.getTotalPrice().multiply(BigDecimal.valueOf(100)).longValue();

        Payment payment = Payment.builder()
                .booking(booking)
                .amount(booking.getTotalPrice())
                .amountVnpay(amountVnpay)
                .vnpTxnRef(txnRef)
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        String ip =clientIp != null ? clientIp : "127.0.0.1";
        String paymentUrl = vNPayService.createPaymentUrl(booking, payment, request.returnUrl(), ip);

        return new CreatePaymentResponse(paymentUrl, txnRef, booking.getTotalPrice(), booking.getExpiresAt());
    }

    public PaymentStatusResponse getPaymentStatus(String pnrCode) {
        Booking booking = bookingRepository.findByPnrCode(pnrCode)
                .orElseThrow(() -> new NotFoundException("Booking không tồn tại"));

        Payment payment = paymentRepository.findLatestPaymentByBookingId(booking.getId())
                .orElseThrow(() -> new NotFoundException("Chưa c giao dịch thanh toán"));

        return new PaymentStatusResponse(
                pnrCode, payment.getStatus().name(),
                payment.getAmount(), payment.getVnpBankCode(),
                payment.getUpdatedAt()
        );
    }
}
