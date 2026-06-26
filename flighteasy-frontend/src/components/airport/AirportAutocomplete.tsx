import { useState, useRef, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { MapPin } from "lucide-react";
import { flightApi } from "@/api/flight.api";

interface Airport {
    iataCode: string;
    name: string;
    city: string;
    country: string;
}

interface Props {
    placeholder?: string;
    value: string;
    onChange: (iataCode: string) => void;
}

export default function AirportAutocomplete({ placeholder, value, onChange }: Props) {
    const [inputValue, setInputValue] = useState(value ?? "");
    const [open, setOpen] = useState(false);
    const wrapperRef = useRef<HTMLDivElement>(null);

    const { data } = useQuery({
        queryKey: ["airports"],
        queryFn: () => flightApi.getAirports(),
        staleTime: Infinity, // airports hiếm thay đổi
    });

    const airports: Airport[] = data?.data || [];

    const filtered = inputValue.length >= 1
        ? airports.filter((a)  => {
            const q = inputValue.toLowerCase();
            return (
                a.iataCode.toLowerCase().includes(q) ||
                a.name.toLowerCase().includes(q) ||
                a.city.toLowerCase().includes(q)
            );
        }).slice(0, 6)
        : [];

    // Đóng dropdown khi click ra ngoài
    useEffect(() => {
        const handler = (e: MouseEvent) => {
            if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener("mousedown", handler);
        return () => document.removeEventListener("mousedown", handler);
    }, []);

    // Sync value từ ngoài (ví dụ swap airports)
    useEffect(() => {
        if (value !== inputValue) {
            const airport = airports.find((a) => a.iataCode === value);
            setInputValue(airport ? `${airport.iataCode} - ${airport.city}` : value);
        }
    }, [value]);

    const handleSelect = (airport: Airport) => {
        setInputValue(`${airport.iataCode} - ${airport.city}`);
        onChange(airport.iataCode);
        setOpen(false);
    };

    return (
        <div ref={wrapperRef} className="relative">
            <input
                value={inputValue}
                onChange={(e) => {
                    setInputValue(e.target.value);
                    setOpen(true);
                    // Nếu xóa hết thì clear value
                    if (!e.target.value) onChange("");
                }}
                onFocus={() => setOpen(true)}
                placeholder={placeholder}
                className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            />

            {open && filtered.length > 0 && (
                <div className="absolute z-50 mt-1 w-full bg-white border border-gray-200 rounded-xl shadow-lg overflow-hidden">
                    {filtered.map((airport) => (
                        <button
                            key={airport.iataCode}
                            type="button"
                            onClick={() => handleSelect(airport)}
                            className="w-full flex items-center gap-3 px-4 py-3 hover:bg-blue-50 transition-colors text-left"
                        >
                            <MapPin className="w-4 h-4 text-blue-500 shrink-0" />
                            <div>
                                <span className="font-bold text-gray-900 text-sm">{airport.iataCode}</span>
                                <span className="text-gray-500 text-sm"> — {airport.city}</span>
                                <p className="text-xs text-gray-400">{airport.name}</p>
                            </div>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
}