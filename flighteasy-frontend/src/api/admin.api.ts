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

    createAirport: (data: {
        iataCode: string;
        name: string;
        city: string;
        country: string;
        countryCode: string;
        timezone: string;
    })=> api.post("/v1/admin/airports", data),

    getAirlines: () => api.get("/v1/admin/airlines"),

    createAirline: (data: {
        iataCode: string;
        name: string;
        country?: string;
        logoUrl?: string;
    })=> api.post("/v1/admin/sirlines", data),

    getAdminFlights: (params?: {page?: number; size?: number; status?: string}) =>
        api.get("/v1/admin/flights", {params}),

    createFlight: (data: {
        flightNumber: string;
        airlineId: number;
        aircraftTypeId?: number;
        originIata: string;
        destinationIata: string;
        departureTime: string;
        arrivalTime: string;
        durationMinutes: number;
        terminal?: string;
        gate?: string;
        flightClasses: Array<{
            classType: string;
            basePrice: number;
            totalSeats: number;
            baggageAllowanceKg: number;
            isRefundable: boolean;
        }>;
    })=> api.post("/v1/admin/flights", data),

    updateFlightStatus: (id: number, status: string) =>
        api.patch(`/v1/admin/flights/${id}/status`, {status}),
};