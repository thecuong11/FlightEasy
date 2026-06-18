package com.flighteasy.scheduler;

import com.flighteasy.entity.Payment;
import com.flighteasy.enums.PaymentStatus;
import com.flighteasy.repository.PaymentRepository;
import com.flighteasy.service.VNPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {
    private final PaymentRepository paymentRepository;
    private final VNPayService vnPayService;

    @Scheduled(fixedDelay = 300_000)
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        List<Payment> stuck = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        for (Payment payment : stuck) {
            try {
                Map<String, Object> result = vnPayService.queryTransactionStatus(payment);
                String responseCode = (String) result.get("vnp_responseCode");
                String tranStatus = (String) result.get("vnp_TransactionStatus");

                if ("00".equals(responseCode) && "00".equals(tranStatus)) {
                    vnPayService.confirmFromReconciliation(payment, result);

                } else if ("01".equals(tranStatus)) {
                    log.info("Payment {} vẫn đang xử lý (transStatus=01), kiểm tra lại sau", payment.getVnpTxnRef());
                } else {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                    log.info("Payment {} được xác nhận FAILED qua reconciliation (responseCode={})", payment.getVnpTxnRef(), responseCode);
                }
            } catch (Exception e) {
                log.error("Reconciliation lỗi cho payment {}: {}" , payment.getVnpTxnRef(), e.getMessage());
            }
        }
    }
}
