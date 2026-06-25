import {useQuery, useMutation, useQueryClient} from "@tanstack/react-query";
import {useNavigate} from "react-router-dom";
import {bookingApi} from "@/api/booking.api.ts";
import type {BookingResponse} from "@/types/booking.types.ts";
import {format, parseISO} from "date-fns";
import {vi} from "date-fns/locale";
import {Plane, Clock, CheckCircle, XCircle} from "lucide-react";
import toast from "react-hot-toast";
import React from "react";

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN", {style: "currency", currency: "VND"}).format(amount);

const StatusBadge = ({status}: {status: string}) => {
    const map: Record<string, { label: string; cls: string; icon: React.ReactNode }> = {
        CONFIRMED: {
            label: "Đã xác nhận",
            cls: "bg-green-100 text-green-700",
            icon: <CheckCircle className="w-3 h-3" />
        },
        PENDING: {
            label: "Chờ thanh toán",
            cls: "bg-amber-100 text-amber-700",
            icon: <Clock className="w-3 h-3" />
        },
        CANCELLED: {
            label: "Đã hủy",
            cls: "bg-red-100 text-red-700",
            icon: <XCircle className="w-3 h-3" />
        }
    };

    const s = map[status] ?? {label: status, cls: "bg-gray-100 text-gray-700", icon: null};
    return (
        <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${s.cls}`}>
      {s.icon}
            {s.label}
    </span>
    );
};

export default function MyBookingsPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const {data, isLoading} = useQuery({
        queryKey: ["my-bookings"],
        queryFn: () => bookingApi.getMyBookings()
    });

    const cancelMutation = useMutation({
        mutationFn: (pnr: string) => bookingApi.cancelBooking(pnr),
        onSuccess: () => {
            toast.success("Đã hủy booking thành công");
            queryClient.invalidateQueries({queryKey: ["my-bookings"]});
        }
    });

    const bookings: BookingResponse[] = data?.data ?? [];

    if (isLoading) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
            </div>
        );
    }

    return (
        <div className="max-w-3xl mx-auto px-4 py-8">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Lịch sử đặt vé</h1>

            {bookings.length === 0 ? (
                <div className="text-center py-16 text-gray-500">
                    <Plane className="w-12 h-12 mx-auto mb-3 text-gray-300" />
                    <p>Bạn chưa có booking nào.</p>
                </div>
            ) : (
                <div className="space-y-4">
                    {bookings.map((booking) => (
                        <div
                            key={booking.pnrCode}
                            className="bg-white rounded-xl shadow-sm border border-gray-100 p-5"
                        >
                            <div className="flex items-start justify-between mb-3">
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                    <span className="font-bold text-lg tracking-widest text-blue-700">
                      {booking.pnrCode}
                    </span>
                                        <StatusBadge status={booking.status} />
                                    </div>
                                    <p className="text-sm text-gray-500">
                                        {booking.flight.from} → {booking.flight.to} •{" "}
                                        {format(parseISO(booking.flight.departureTime), "HH:mm - dd/MM/yyyy", {
                                            locale: vi,
                                        })}
                                    </p>
                                </div>
                                <p className="text-lg font-bold text-gray-900">
                                    {formatCurrency(booking.pricing.totalPrice)}
                                </p>
                            </div>

                            <div className="flex items-center gap-2 mt-4">
                                <button
                                    onClick={() => navigate(`/booking/confirm/${booking.pnrCode}`)}
                                    className="text-sm text-blue-600 hover:underline font-medium"
                                >
                                    Xem chi tiết
                                </button>

                                {booking.status === "PENDING" && (
                                    <>
                                        <span className="text-gray-300">|</span>
                                        <button
                                            onClick={() =>
                                                navigate(
                                                    `/payment?pnr=${booking.pnrCode}&amount=${booking.pricing.totalPrice}`
                                                )
                                            }
                                            className="text-sm text-green-600 hover:underline font-medium"
                                        >
                                            Thanh toán ngay
                                        </button>
                                        <span className="text-gray-300">|</span>
                                        <button
                                            onClick={() => {
                                                if (window.confirm(`Hủy booking ${booking.pnrCode}?`)) {
                                                    cancelMutation.mutate(booking.pnrCode);
                                                }
                                            }}
                                            disabled={cancelMutation.isPending}
                                            className="text-sm text-red-500 hover:underline font-medium disabled:opacity-50"
                                        >
                                            Hủy booking
                                        </button>
                                    </>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}