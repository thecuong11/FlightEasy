export interface BookingResponse {
    pnrCode: string;
    status: string;
    expireAt: string;
    flight: {
        flightNumber: string;
        from: string;
        to: string;
        departureTime: string;
    };
    passengers: Array<{
        name: string;
        seat: string;
        idNumber: string;
    }>;
    pricing: {
        subtotal: number;
        serviceFee: number;
        totalPrice: number;
        currency: string;
    };
    paymentDeadline: string;
}

export interface SeatInfo {
    seatNumber: string;
    position: string;
    isAvailable: boolean;
    extraFee: number;
    isExtraLegroom: boolean;
}

export interface SeatRow {
    rowNumber: number;
    seats: SeatInfo[];
}

export interface SeatMapResponse {
    firstClass: SeatRow[];
    business: SeatRow[];
    economy: SeatRow[];
}