import api from "@/lib/axios";
import type {FlightSearchResponse} from "@/types/flight.types.ts";
import type {SeatMapResponse} from "@/types/booking.types.ts";

export interface SearchParams {
    from: string;
    to: string;
    departDate: string;
    adults?: number;
    children?: number;
    infants?: number;
    classType?: string;
    sortBy?: string;
    minPrice?: number;
    maxPrice?: number;
    airlines?: string;
    page?: number;
    size?: number;
}

export const flightApi = {
    searchFlights: (params: SearchParams) =>
        api.get<FlightSearchResponse>("/v1/flights/search", {params}),

    searchRoundTrip: (params: SearchParams & {returnDate: string}) =>
        api.get("/v1/flights/search/round-trip", {params}),

    getAirports: () => api.get("/v1/airports"),

    getFlightById: (id: number) => api.get(`/v1/flights/${id}`),

    getSeatMap: (flightId: number) => api.get<SeatMapResponse>(`/v1/flights/${flightId}/seats`),
};