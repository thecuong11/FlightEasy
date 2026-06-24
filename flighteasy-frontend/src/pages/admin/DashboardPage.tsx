import {useQuery} from "@tanstack/react-query";
import {adminApi} from "@/api/admin.api.ts";
import type {DashboardKPIResponse} from "@/types/admin.types.ts";
import {
    TrendingUp, TrendingDown, Plane, Calendar,
    DollarSign, Users, AlertTriangle, CheckCircle
} from "lucide-react";


const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
    }).format(amount);

function KPICard({
    title,
    value,
    sub,
    icon: Icon,
    trend,
    color = "blue",
 }: {
    title: string;
    value: string;
    sub?: string;
    icon: React.ElementType;
    trend?: number;
    color?: "blue" | "green" | "amber" | "red";
}) {
    const colorMap = {
        blue: "bg-blue-50 text-blue-700",
        green: "bg-green-50 text-green-700",
        amber: "bg-amber-50 text-amber-700",
        red: "bg-red-50 text-red-700"
    };

    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
            <div className="flex items-start justify-between">
                <div>
                    <p className="text-sm text-gray-500">{title}</p>
                    <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
                    {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
                    {trend !== undefined && (
                        <div className={`flex items-center gap-1 mt-2 text-xs font-medium ${trend >= 0 ? "text-green-600" : "text-red-600"}`}>
                            {trend >= 0
                                ? <TrendingUp className="w-3 h-3" />
                                : <TrendingDown className="w-3 h-3" />
                            }
                            {Math.abs(trend).toFixed(1)}% so với hôm qua
                        </div>
                    )}
                </div>
                <div className={`p-3 rounded-xl ${colorMap[color]}`}>
                    <Icon className="w-5 h-5" />
                </div>
            </div>
        </div>
    );
}

export default function DashboardPage() {
    const {data, isLoading} = useQuery({
        queryKey: ["admin-kpis"],
        queryFn: () => adminApi.getDashboardKPIs(),
        refetchInterval: 5 * 60 * 1000
    });

    const kpis: DashboardKPIResponse | undefined = data?.data;

    const handleExport = async () => {
        const today = new Date().toISOString().split("T")[0];
        const monthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
            .toISOString()
            .split("T")[0];

        const res = await adminApi.exportReport(monthAgo, today);
        const url = URL.createObjectURL(res.data);
        const a = document.createElement("a");
        a.href = url;
        a.download = `bao-cao-${today}.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
    };

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
            </div>
        );
    }

    return (
        <div className="p-6 max-w-7xl mx-auto">
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
                    <p className="text-gray-500 text-sm mt-1">
                        {kpis?.date
                            ? new Date(kpis.date).toLocaleDateString("vi-VN", {
                                weekday: "long",
                                year: "numeric",
                                month: "long",
                                day: "numeric",
                            })
                            : "Hôm nay"}
                    </p>
                </div>
                <button
                    onClick={handleExport}
                    className="bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-800 transition-colors flex items-center gap-2"
                >
                    Xuất báo cáo
                </button>
            </div>

            {/* KPI Grid */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
                <KPICard
                    title="Doanh thu hôm nay"
                    value={formatCurrency(kpis?.todayRevenue || 0)}
                    sub={`Hôm qua: ${formatCurrency(kpis?.yesterdayRevenue || 0)}`}
                    icon={DollarSign}
                    trend={kpis?.revenueGrowthPercent}
                    color="green"
                />
                <KPICard
                    title="Booking hôm nay"
                    value={String(kpis?.todayBookings || 0)}
                    sub={`Tỷ lệ xác nhận: ${kpis?.conversionRate?.toFixed(1)}%`}
                    icon={Calendar}
                    color="blue"
                />
                <KPICard
                    title="Chuyến bay hôm nay"
                    value={String(kpis?.totalFlights || 0)}
                    sub={`Trễ: ${kpis?.delayedFlights || 0} | Huỷ: ${kpis?.cancelledFlights || 0}`}
                    icon={Plane}
                    color="amber"
                />
                <KPICard
                    title="Giá vé trung bình"
                    value={formatCurrency(kpis?.avgTicketPrice || 0)}
                    icon={Users}
                    color="blue"
                />
            </div>

            {/* Booking Status */}
            <div className="grid grid-cols-3 gap-4">
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
                    <div className="flex items-center gap-3">
                        <CheckCircle className="w-8 h-8 text-green-500" />
                        <div>
                            <p className="text-xl font-bold text-gray-900">{kpis?.confirmedBookings || 0}</p>
                            <p className="text-sm text-gray-500">Đã xác nhận</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
                    <div className="flex items-center gap-3">
                        <AlertTriangle className="w-8 h-8 text-amber-500" />
                        <div>
                            <p className="text-xl font-bold text-gray-900">{kpis?.pendingBookings || 0}</p>
                            <p className="text-sm text-gray-500">Chờ thanh toán</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
                    <div className="flex items-center gap-3">
                        <AlertTriangle className="w-8 h-8 text-red-500" />
                        <div>
                            <p className="text-xl font-bold text-gray-900">{kpis?.cancelledBookings || 0}</p>
                            <p className="text-sm text-gray-500">Đã hủy</p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}