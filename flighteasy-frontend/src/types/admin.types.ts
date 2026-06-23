export interface DashboardKPIResponse {
    date: string;
    todayRevenue: number;
    yesterdayRevenue: number;
    revenueGrowthPercent: number;
    todayBooking: number;
    confirmedBookings: number;
    pendingBookings: number;
    cancelledBookings: number;
    conversionRate: number;
    totalFlights: number;
    delayedFlights: number;
    cancelledFlights: number;
    avgTicketPrice: number;
    updatedAt: string;
}