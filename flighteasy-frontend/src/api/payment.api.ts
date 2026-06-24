import api from "@/lib/axios.ts";

export const paymentApi = {
    createPaymentLink: (pnrCode: string, returnUrl: string) =>
        api.post<{
        paymentUrl: string;
        txnRef: string;
        amount: number;
        expiresAt: string;
        }>("/v1/payments/vnpay/create", {
            pnrCode,
            returnUrl,
        }),

    getPaymentStatus: (pnr: string) =>
        api.get<{
        pnrCode: string;
        status: string;
        amount: number;
        bankCode: string;
        paidAt: string;
        }>(`/v1/paymnets/status/${pnr}`),
};