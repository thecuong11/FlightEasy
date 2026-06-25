import {useState} from "react";
import {useQuery, useMutation, useQueryClient} from "@tanstack/react-query";
import {adminApi} from "@/api/admin.api.ts";
import {format, parseISO} from "date-fns";
import {vi} from "date-fns/locale";
import {Search, XCircle} from "lucide-react";
import toast from "react-hot-toast";

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN",{style: "currency", currency: "VND"}).format(amount);

const STATUS_COLORS: Record<string, string> = {
    CONFIRMED: "bg-green-100 text-green-700",
    PENDING: "bg-amber-100 text-amber-700",
    CANCELLED: "bg-red-100 text-red-700",
    EXPIRED: "bg-gray-100 text-gray-500"
}

export default function BookingsManagePage() {
    const queryClient = useQueryClient();
    const [statusFilter, setStatusFilter] = useState("");
    const [page, setPage] = useState(0);
    const [cancelTarget, setCancelTarget] = useState<string | null>(null);
    const [cancelReason, setCancelReason] = useState("");

    const {data, isLoading} = useQuery({
        queryKey: ["admin-bookings", statusFilter, page],
        queryFn: () =>
            adminApi.getAllBookings({status: statusFilter || undefined, page, size: 20}),
    });

    const cancelMutation = useMutation({
        mutationFn: ({pnr, reason}: {pnr: string; reason: string}) =>
            adminApi.cancelBooking(pnr, reason),
        onSuccess: () => {
            toast.success("Đã hủy booking");
            setCancelTarget(null);
            setCancelReason("");
            queryClient.invalidateQueries({queryKey: ["admin-bookings"]});
        }
    });

    const bookings = data?.data?.content ?? [];
    const totalPages = data?.data?.totalPages ?? 0;

    return (
        <div className="p-6 max-w-7xl mx-auto">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Quản lý Booking</h1>

            {/* Filters */}
            <div className="flex items-center gap-3 mb-6">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
                    <select
                        value={statusFilter}
                        onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
                        className="pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                        <option value="">Tất cả trạng thái</option>
                        <option value="PENDING">Chờ thanh toán</option>
                        <option value="CONFIRMED">Đã xác nhận</option>
                        <option value="CANCELLED">Đã hủy</option>
                        <option value="EXPIRED">Hết hạn</option>
                    </select>
                </div>
            </div>

            {/* Table */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-gray-50 border-b border-gray-100">
                    <tr>
                        {["PNR", "Hành khách", "Hành trình", "Ngày bay", "Tổng tiền", "Trạng thái", "Thao tác"].map((h) => (
                            <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                                {h}
                            </th>
                        ))}
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                    {isLoading ? (
                        <tr>
                            <td colSpan={7} className="text-center py-12 text-gray-400">Đang tải...</td>
                        </tr>
                    ) : bookings.length === 0 ? (
                        <tr>
                            <td colSpan={7} className="text-center py-12 text-gray-400">Không có booking nào</td>
                        </tr>
                    ) : (
                        bookings.map((b: any) => (
                            <tr key={b.pnrCode} className="hover:bg-gray-50 transition-colors">
                                <td className="px-4 py-3 font-bold tracking-widest text-blue-700">{b.pnrCode}</td>
                                <td className="px-4 py-3 text-gray-800">
                                    {b.passengers?.[0]?.name ?? "—"}
                                    {b.passengers?.length > 1 && (
                                        <span className="text-gray-400 text-xs ml-1">+{b.passengers.length - 1}</span>
                                    )}
                                </td>
                                <td className="px-4 py-3 text-gray-600">
                                    {b.flight?.from} → {b.flight?.to}
                                </td>
                                <td className="px-4 py-3 text-gray-600">
                                    {b.flight?.departureTime
                                        ? format(parseISO(b.flight.departureTime), "dd/MM/yyyy HH:mm", { locale: vi })
                                        : "—"}
                                </td>
                                <td className="px-4 py-3 font-medium text-gray-900">
                                    {formatCurrency(b.pricing?.totalPrice ?? 0)}
                                </td>
                                <td className="px-4 py-3">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[b.status] ?? "bg-gray-100 text-gray-600"}`}>
                      {b.status}
                    </span>
                                </td>
                                <td className="px-4 py-3">
                                    {(b.status === "PENDING" || b.status === "CONFIRMED") && (
                                        <button
                                            onClick={() => setCancelTarget(b.pnrCode)}
                                            className="flex items-center gap-1 text-red-500 hover:text-red-700 text-xs font-medium"
                                        >
                                            <XCircle className="w-3 h-3" />
                                            Hủy
                                        </button>
                                    )}
                                </td>
                            </tr>
                        ))
                    )}
                    </tbody>
                </table>
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
                <div className="flex justify-center gap-2 mt-4">
                    <button
                        disabled={page === 0}
                        onClick={() => setPage((p) => p - 1)}
                        className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
                    >
                        ← Trước
                    </button>
                    <span className="px-3 py-1.5 text-sm text-gray-600">
            Trang {page + 1} / {totalPages}
          </span>
                    <button
                        disabled={page >= totalPages - 1}
                        onClick={() => setPage((p) => p + 1)}
                        className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
                    >
                        Sau →
                    </button>
                </div>
            )}

            {/* Cancel Dialog */}
            {cancelTarget && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
                    <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md">
                        <h2 className="text-lg font-bold text-gray-900 mb-2">Hủy booking {cancelTarget}</h2>
                        <p className="text-sm text-gray-500 mb-4">
                            Hành động này không thể hoàn tác. Nhập lý do hủy:
                        </p>
                        <textarea
                            value={cancelReason}
                            onChange={(e) => setCancelReason(e.target.value)}
                            placeholder="Ví dụ: Khách yêu cầu hủy, trùng lịch..."
                            rows={3}
                            className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-red-400 resize-none"
                        />
                        <div className="flex gap-3 mt-4">
                            <button
                                onClick={() => { setCancelTarget(null); setCancelReason(""); }}
                                className="flex-1 px-4 py-2.5 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50"
                            >
                                Không hủy
                            </button>
                            <button
                                disabled={cancelMutation.isPending}
                                onClick={() => cancelMutation.mutate({ pnr: cancelTarget, reason: cancelReason })}
                                className="flex-1 px-4 py-2.5 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-60"
                            >
                                {cancelMutation.isPending ? "Đang hủy..." : "Xác nhận hủy"}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}