import { useSearchParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { paymentApi } from "@/api/payment.api";
import { CheckCircle, XCircle, Loader } from "lucide-react";

export default function PaymentResultPage() {
    const [searchParam] = useSearchParams();
    const navigate = useNavigate();
    const responseCode = searchParam.get("vnp_ResponseCode");
    const  txnRef = searchParam.get("vnp_TxnRef");
    const pnrCode = txnRef?.split("-")[0];
    const isSuccess = responseCode === "00";

    const {data, isLoading} = useQuery({
        queryKey: ["payment-status", pnrCode],
        queryFn: () => paymentApi.getPaymentStatus(pnrCode!),
        enabled: !!pnrCode,
        refetchInterval: isSuccess ? 2000 : false,
        refetchIntervalInBackground: false,
    });

    const status = data?.data.status;

    return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
            <div className="bg-white rounded-2xl shadow-xl p-10 text-center max-w-md w-full">
                {isLoading ? (
                    <Loader className="w-16 h-16 text-blue-500 mx-auto animate-spin" />
                ) : isSuccess && status === "SUCCESS" ? (
                    <>
                        <CheckCircle className="w-20 h-20 text-green-500 mx-auto mb-4" />
                        <h1 className="text-2xl font-bold text-gray-900">Thanh toán thành công!</h1>
                        <p className="text-gray-500 mt-2 mb-6">
                            Vé của bạn đã được xác nhận. Kiểm tra email để xem chi tiết.
                        </p>
                        <div className="bg-blue-50 rounded-xl p-4 mb-6">
                            <p className="text-sm text-gray-500">Mã đặt chỗ</p>
                            <p className="text-2xl font-bold tracking-widest text-blue-700">{pnrCode}</p>
                        </div>
                        <button
                            onClick={() => navigate(`/booking/confirm/${pnrCode}`)}
                            className="w-full bg-blue-700 text-white py-3 rounded-xl font-medium hover:bg-blue-800 transition-colors"
                        >
                            Xem chi tiết vé
                        </button>
                    </>
                ) : (
                    <>
                        <XCircle className="w-20 h-20 text-red-500 mx-auto mb-4" />
                        <h1 className="text-2xl font-bold text-gray-900">Thanh toán thất bại</h1>
                        <p className="text-gray-500 mt-2 mb-6">
                            Giao dịch không thành công. Bạn có thể thử lại hoặc chọn phương thức khác.
                        </p>
                        <button
                            onClick={() => navigate(`/booking/confirm/${pnrCode}`)}
                            className="w-full bg-blue-700 text-white py-3 rounded-xl font-medium hover:bg-blue-800 transition-colors"
                        >
                            Thử lại
                        </button>
                    </>
                )}
            </div>
        </div>
    );
}