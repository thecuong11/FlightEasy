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
    private final VNPayService vNPayService;

    @Scheduled(fixedDelay = 300_000)
    public void reconcilePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        List<Payment> stuck = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        for (Payment payment : stuck) {
            try {
                Map<String, Object> result = vNPayService.queryTransactionStatus(payment);
                String responseCode = (String) result.get("vnp_responseCode");
                String tranStatus = (String) result.get("vnp_TransactionStatus");

                if ("00".equals(responseCode) && "00".equals(tranStatus)) {
                    Map<String, String> fakeIpnParams = new HashMap<>();
                    fakeIpnParams.put("vnp_TxnRef", payment.getVnpTxnRef());
                    fakeIpnParams.put("vnp_Amount", String.valueOf(payment.getAmountVnpay()));
                    fakeIpnParams.put("vnp_ResponseCode", "00");
                    fakeIpnParams.put("vnp_TransactionNo", (String) result.get("vnp_TransactionNo"));
                    fakeIpnParams.put("vnp_BankCode", (String) result.getOrDefault("vnp_BankCode", ""));

                    vNPayService.confirmFromReconciliation(payment, fakeIpnParams);
                    log.info("Reconciled stuck payment {} -> SUCCESS", payment.getVnpTxnRef());
                } else if ("01".equals(tranStatus)) {

                } else {
                    payment.setStatus(PaymentStatus.FAILED);
                    paymentRepository.save(payment);
                }
            } catch (Exception e) {
                log.error("Reconciliation failed for {}: {}" , payment.getVnpTxnRef(), e.getMessage());
            }
        }
    }
}
