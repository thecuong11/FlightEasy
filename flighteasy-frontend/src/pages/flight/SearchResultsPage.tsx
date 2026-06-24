import {useSearchParams, useNavigate} from "react-router-dom";
import {useQuery} from "@tanstack/react-query";
import {flightApi} from "@/api/flight.api.ts";
import type {FlightSearchResult} from "@/types/flight.types.ts";
import {format, parseISO} from "date-fns";
import {vi} from "date-fns/locale";
import {Clock, Luggage, ArrowRight} from "lucide-react";
import {ArrowLeft} from "lucide-react";

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat("vi-VN", {style: "currency", currency: "VND"}).format(amount);

const formatDuration = (minutes: number) => {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return `${h}g ${m}p`;
};

function FlightCard({
    flight,
    onSelect,
    } : {
    flight: FlightSearchResult;
    onSelect: () => void;
}) {
    return (
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 hover:shadow-md transition-shadow">
            <div className="flex items-center justify-between">
                {/* Airline */}
                <div className="flex items-center gap-3 w-36">
                    <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center text-blue-700 font-bold text-sm">
                        {flight.airlineCode}
                    </div>
                    <div>
                        <p className="text-sm font-medium text-gray-900">{flight.airlineName}</p>
                        <p className="text-xs text-gray-400">{flight.flightNumber}</p>
                    </div>
                </div>

                {/* Route */}
                <div className="flex-1 flex items-center justify-center gap-6">
                    <div className="text-center">
                        <p className="text-2xl font-bold text-gray-900">
                            {format(parseISO(flight.departureTime), "HH:mm")}
                        </p>
                        <p className="text-sm font-medium text-gray-600">{flight.originIata}</p>
                        <p className="text-xs text-gray-400">{flight.originCity}</p>
                    </div>

                    <div className="flex flex-col items-center">
                        <p className="text-xs text-gray-400 flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {formatDuration(flight.durationMinutes)}
                        </p>
                        <div className="flex items-center gap-1 my-1">
                            <div className="w-16 h-px bg-gray-300" />
                            <ArrowRight className="w-3 h-3 text-gray-400" />
                        </div>
                        <p className="text-xs text-gray-400">Thẳng</p>
                    </div>

                    <div className="text-center">
                        <p className="text-2xl font-bold text-gray-900">
                            {format(parseISO(flight.arrivalTime), "HH:mm")}
                        </p>
                        <p className="text-sm font-medium text-gray-600">{flight.destinationIata}</p>
                        <p className="text-xs text-gray-400">{flight.destinationCity}</p>
                    </div>
                </div>

                {/* Tags */}
                <div className="flex flex-col items-start gap-1 w-24">
                    {flight.tags.includes("CHEAPEST") && (
                        <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full font-medium">
              Rẻ nhất
            </span>
                    )}
                    {flight.tags.includes("FASTEST") && (
                        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full font-medium">
              Nhanh nhất
            </span>
                    )}
                </div>

                {/* Price & CTA */}
                <div className="text-right ml-4">
                    <p className="text-xs text-gray-400 flex items-center justify-end gap-1">
                        <Luggage className="w-3 h-3" />
                        {flight.baggageAllowanceKg}kg
                    </p>
                    <p className="text-2xl font-bold text-blue-700 mt-1">
                        {formatCurrency(flight.pricePerPerson)}
                    </p>
                    <p className="text-xs text-gray-400 mb-2">/người</p>
                    <button
                        onClick={onSelect}
                        className="bg-blue-700 text-white px-5 py-2 rounded-lg text-sm font-medium hover:bg-blue-800 transition-colors"
                    >
                        Chọn
                    </button>
                </div>
            </div>
        </div>
    );
}

export default function SearchResultsPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    const queryParams = {
        from: searchParams.get("from") || "",
        to: searchParams.get("to") || "",
        departDate: searchParams.get("departDate") || "",
        adults: Number(searchParams.get("adults") || 1),
        children: Number(searchParams.get("children") || 0),
        infants: Number(searchParams.get("infants") || 0),
        classType: searchParams.get("classType") || "ECONOMY",
    };

    const {data, isLoading, isError} = useQuery({
        queryKey: ["flights", queryParams],
        queryFn: () => flightApi.searchFlights(queryParams),
        enabled: !!queryParams.from && !!queryParams.to && !!queryParams.departDate,
    });

    const flights = data?.data.flights || [];

    const handleSelect = (flight: FlightSearchResult) => {
        navigate(`/booking?flightClassId=${flight.id}&${searchParams.toString()}`);
    };

    return (
        <div className="max-w-4xl mx-auto px-4 py-8">
            <button
                onClick={() => navigate(-1)}
                className="mb-4 flex items-center gap-2 rounded-lg border px-3 py-2 hover:bg-gray-50"
            >
                <ArrowLeft size={18} />
                Quay lại
            </button>
            {/* Summary */}
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-900">
                    {queryParams.from} → {queryParams.to}
                </h1>
                <p className="text-gray-500 text-sm mt-1">
                    {queryParams.departDate
                        ? format(parseISO(queryParams.departDate), "EEEE, dd MMMM yyyy", { locale: vi })
                        : ""}{" "}
                    • {queryParams.adults} người lớn • {queryParams.classType}
                </p>
            </div>

            {isLoading && (
                <div className="text-center py-16 text-gray-500">Đang tìm chuyến bay...</div>
            )}

            {isError && (
                <div className="text-center py-16 text-red-500">
                    Không thể tải dữ liệu. Vui lòng thử lại.
                </div>
            )}

            {!isLoading && flights.length === 0 && (
                <div className="text-center py-16 text-gray-500">
                    Không tìm thấy chuyến bay phù hợp.
                </div>
            )}

            <div className="space-y-3">
                {flights.map((flight) => (
                    <FlightCard
                        key={flight.id}
                        flight={flight}
                        onSelect={() => handleSelect(flight)}
                    />
                ))}
            </div>
        </div>
    );
}