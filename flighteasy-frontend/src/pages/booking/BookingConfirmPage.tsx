import {useParams} from "react-router-dom";
import {useQuery} from "@tanstack/react-query";
import {bookingApi} from "@/api/booking.api.ts";
import {format, parseISO} from "date-fns";
import {vi} from "date-fns/locale";
import {CheckCircle, Clock, Plane} from "lucide-react";
import {paymentApi} from "@/api/payment.api.ts";

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN", {style: "currency", currency: "VND"}).format(amount);

export default function BookingConfirmPage() {
    const {pnr} = useParams<{pnr: string}>();

    const {data, isLoading} = useQuery({
        queryKey: ["booking", pnr],
        queryFn: () => bookingApi.getBooking(pnr!),
        enabled: !!pnr,
    });

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
            </div>
        );
    }

    const booking = data?.data;
    if (!booking) return null;

    const expiresAt = booking.expireAt ? parseISO(booking.expireAt) : null;

    const handlePay = async () => {
        try {
            const returnUrl = `${window.location.origin}/payment/reesult`;
            const res = await paymentApi.createPaymentLink(booking.pnrCode, returnUrl);
            window.location.href = res.data.paymentUrl;
        } catch {

        }
    };

    return (
        <div className="max-w-2xl mx-auto px-4 py-12">
            {/* Success header */}
            <div className="text-center mb-8">
                <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
                <h1 className="text-2xl font-bold text-gray-900">Đặt vé thành công!</h1>
                <p className="text-gray-500 mt-1">Mã đặt chỗ của bạn:</p>
                <div className="mt-3 inline-block bg-blue-50 border border-blue-200 rounded-xl px-8 py-3">
                    <span className="text-3xl font-bold tracking-widest text-blue-700">{booking.pnrCode}</span>
                </div>
            </div>

            {/* Countdown if pending */}
            {booking.status === "PENDING" && expiresAt && (
                <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6 flex items-center gap-3">
                    <Clock className="w-5 h-5 text-amber-600 flex-shrink-0" />
                    <div>
                        <p className="text-sm font-medium text-amber-800">Vui lòng thanh toán trước:</p>
                        <p className="text-sm text-amber-700">
                            {format(expiresAt, "HH:mm - dd/MM/yyyy", { locale: vi })}
                        </p>
                    </div>
                </div>
            )}

            {/* Flight info */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
                <h2 className="font-semibold text-gray-800 mb-4 flex items-center gap-2">
                    <Plane className="w-4 h-4" />
                    Thông tin chuyến bay
                </h2>
                <div className="space-y-3 text-sm">
                    <div className="flex justify-between">
                        <span className="text-gray-500">Chuyến bay</span>
                        <span className="font-medium">{booking.flight.flightNumber}</span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-gray-500">Hành trình</span>
                        <span className="font-medium">{booking.flight.from} → {booking.flight.to}</span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-gray-500">Khởi hành</span>
                        <span className="font-medium">
              {format(parseISO(booking.flight.departureTime), "HH:mm - dd/MM/yyyy", { locale: vi })}
            </span>
                    </div>
                </div>
            </div>

            {/* Passengers */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
                <h2 className="font-semibold text-gray-800 mb-4">Hành khách</h2>
                <div className="space-y-2">
                    {booking.passengers.map((p, i) => (
                        <div key={i} className="flex items-center justify-between text-sm py-2 border-b border-gray-50 last:border-0">
                            <span className="font-medium text-gray-800">{p.name}</span>
                            <span className="text-blue-700 font-medium">Ghế {p.seat}</span>
                        </div>
                    ))}
                </div>
            </div>

            {/* Pricing */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
                <h2 className="font-semibold text-gray-800 mb-4">Chi phí</h2>
                <div className="space-y-2 text-sm">
                    <div className="flex justify-between">
                        <span className="text-gray-500">Giá vé</span>
                        <span>{formatCurrency(booking.pricing.subtotal)}</span>
                    </div>
                    <div className="flex justify-between">
                        <span className="text-gray-500">Phí dịch vụ</span>
                        <span>{formatCurrency(booking.pricing.serviceFee)}</span>
                    </div>
                    <div className="flex justify-between font-bold text-base pt-2 border-t border-gray-100 mt-2">
                        <span>Tổng cộng</span>
                        <span className="text-blue-700">{formatCurrency(booking.pricing.totalPrice)}</span>
                    </div>
                </div>
            </div>

            {/* Pay button */}
            {booking.status === "PENDING" && (
                <button
                    onClick={handlePay}
                    className="w-full bg-blue-700 text-white py-3.5 rounded-xl font-semibold text-lg hover:bg-blue-800 transition-colors"
                >
                    Thanh toán ngay — {formatCurrency(booking.pricing.totalPrice)}
                </button>
            )}
        </div>
    )
}