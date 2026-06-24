import api from "@/lib/axios";
import type {DashboardKPIResponse} from "@/types/admin.types.ts";

export const adminApi = {
    getDashboardKPIs: () => api.get<DashboardKPIResponse>("/v1/admin/dashboard/kpis"),

    getRevenueChart: (period: "WEEKLY" | "MONTHLY" | "YEARLY") =>
        api.get("v1/admin/dashboard/revenue-chart", {params: {period}}),

    getTopRoutes: (limit = 10) =>
        api.get("/v1/admin/dashboard/top-routes", {params: {limit}}),

    getAllBookings: (params: {status?: string; page?: number; size?: number}) =>
        api.get("/v1/admin/bookings", {params}),

    cancelBooking: (pnr: string, reason?: string) =>
        api.patch(`/v1/admin/bookings/${pnr}/cancel`, null, {params: {reason}}),

    exportReport: (fromDate: string, toDate: string) =>
        api.post(
            "/v1/admin/reports/export",
            {fromDate, toDate, type: "REVENUE"},
            {responseType: "blob"}
        ),
};