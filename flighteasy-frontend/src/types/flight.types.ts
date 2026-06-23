export interface FlightSearchResult {
    id: number;
    flightNumber: string;
    airlineCode: string;
    airlineName: string;
    airlineLogoUrl: string;
    originIata: string;
    originCity: string;
    destinationIata: string;
    destinationCity: string;
    departureTime: string;
    arrivalTime: string;
    durationMinutes: number;
    pricePerPerson: number;
    totalPrice: number;
    availableSeats: number;
    baggageAllowanceKg: number;
    isRefundable: boolean;
    status: string;
    tags: string[];
}

export interface FlightSearchResponse {
    meta: {
        from: string;
        to: string;
        departDate: string;
        adults: number;
        children: number;
        infants: number;
        classType: string;
    };
    flight: FlightSearchResult[];
    priceRange: {min: number; max: number};
    availableFilters: {
        airlines: string[];
        durationRange: {min: number; max: number};
    };
}