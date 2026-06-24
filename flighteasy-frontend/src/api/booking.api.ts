import api from "@/lib/axios.ts";
import type {BookingResponse} from "@/types/booking.types.ts";
import type {SeatMapResponse} from "@/types/booking.types.ts";

export interface CreateBookingPayload {
    flightClassId: number;
    contactEmail: string;
    contactPhone: string;
    passengers: Array<{
        firstName: string;
        lastName: string;
        dateOfBirth: string;
        gender: string;
        nationality: string;
        idType: string;
        idNumber: string;
        passengerType: string;
        seatId?: number;
        mealPreference?: string;
    }>;
    selectedSeatIds: number[];
}

export const bookingApi = {
    createBooking: (payload: CreateBookingPayload) =>
        api.post<BookingResponse>("/v1/bookings", payload),

    getBooking: (pnr: string) => api.get<BookingResponse>(`/v1/nookings/${pnr}`),

    cancelBooking: (pnr: string) =>
        api.delete<{pnrCode: string; refundAmount: number; cancelledAt: string}>(`/v1/bookings/${pnr}`),

    getSeatMap: (flightId: number) =>
        api.get<SeatMapResponse>(`/v1/flights/${flightId}/seats`),
};