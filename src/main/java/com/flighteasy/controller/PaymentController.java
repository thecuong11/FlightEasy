package com.flighteasy.controller;

import com.flighteasy.dto.CreatePaymentRequest;
import com.flighteasy.dto.CreatePaymentResponse;
import com.flighteasy.dto.PaymentStatusResponse;
import com.flighteasy.service.PaymentService;
import com.flighteasy.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final VNPayService vnPayService;

    @PostMapping("/vnpay/create")
    public ResponseEntity<CreatePaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getHeader("X-Forwarded-For");
        if (clientIp == null) {
            clientIp = httpRequest.getRemoteAddr();
        }
        return ResponseEntity.ok(paymentService.createPaymentLink(request, clientIp));
    }

    @GetMapping("/vnpay/ipn")
    public ResponseEntity<String> handleIPN(@RequestParam Map<String, String> params) {
        log.info("VNPay IPN received: txnRef={}", params.get("vnp_TxnRef"));
        String response = vnPayService.processIPN(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<Map<String, Object>> handleReturn(@RequestParam Map<String, String> params) {
        boolean isValid = vnPayService.verifyReturnUrl(params);
        String responseCode = params.get("vnp_ResponseCode");

        Map<String, Object> result = Map.of(
                "isValid", isValid,
                "responseCode", responseCode,
                "success", "00".equals(responseCode) && isValid,
                "txnRef", params.getOrDefault("vnp_TxnRef", "")
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/status/{pnr}")
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable String pnr) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(pnr));
    }
}
