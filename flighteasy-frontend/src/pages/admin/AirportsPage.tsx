import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { adminApi } from "@/api/admin.api";
import { flightApi } from "@/api/flight.api";
import { Plus, X, MapPin } from "lucide-react";
import toast from "react-hot-toast";

const airportSchema = z.object({
    iataCode: z.string().length(3, "Mã IATA phải đúng 3 ký tự").toUpperCase(),
    name: z.string().min(3, "Tên sân bay quá ngắn"),
    city: z.string().min(2, "Tên thành phố quá ngắn"),
    country: z.string().min(2, "Tên quốc gia quá ngắn"),
    countryCode: z.string().length(2, "Mã quốc gia phải đúng 2 ký tự").toUpperCase(),
    timezone: z.string().min(1, "Vui lòng nhập timezone")
});

type AirportForm = z.infer<typeof airportSchema>;

function Modal({open, onClose, children}: {open: boolean; onClose: () => void; children: React.ReactNode}) {
    if (!open) return null;
    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg">
                <div className="flex items-center justify-between p-5 border-b border-gray-100">
                    <h2 className="font-bold text-gray-900">Thêm sân bay mới</h2>
                    <button onClick={onClose} className="p-1 rounded-lg hover:bg-gray-100">
                        <X className="w-4 h-4 text-gray-500" />
                    </button>
                </div>
                <div className="p-5">{children}</div>
            </div>
        </div>
    );
}

export default function AirportsPage() {
    const [showForm, setShowForm] = useState(false);
    const queryClient = useQueryClient();

    const {data, isLoading} = useQuery({
        queryKey: ["airports"],
        queryFn: () => flightApi.getAirports()
    });

    const {register, handleSubmit, reset, formState: {errors}} = useForm<AirportForm>({
        resolver: zodResolver(airportSchema),
        defaultValues: {timezone: "Asia/Ho_Chi_Minh", countryCode: "VN", country: "VietNam"}
    });

    const createMutation = useMutation({
        mutationFn: (data: AirportForm) => adminApi.createAirport(data),
        onSuccess: () => {
            toast.success("Đã tạo sân bay");
            queryClient.invalidateQueries({queryKey: ["airports"]});
            reset();
            setShowForm(false);
        },
        onError: (err: any) => {
            toast.error(err.response?.data?.message || "Tạo thất bại");
        }
    });

    const airports = data?.data || [];

    const fields = [
        { name: "iataCode" as const, label: "Mã IATA (3 ký tự)", placeholder: "SGN" },
        { name: "name" as const, label: "Tên sân bay", placeholder: "Tân Sơn Nhất International Airport" },
        { name: "city" as const, label: "Thành phố", placeholder: "Hồ Chí Minh" },
        { name: "country" as const, label: "Quốc gia", placeholder: "Vietnam" },
        { name: "countryCode" as const, label: "Mã quốc gia (2 ký tự)", placeholder: "VN" },
        { name: "timezone" as const, label: "Timezone", placeholder: "Asia/Ho_Chi_Minh" }
    ];

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Sân bay</h1>
                    <p className="text-gray-500 text-sm mt-1">{airports.length} sân bay đang hoạt động</p>
                </div>
                <button
                    onClick={() => setShowForm(true)}
                    className="flex items-center gap-2 bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-800"
                >
                    <Plus className="w-4 h-4" />
                    Thêm sân bay
                </button>
            </div>

            {/* Table */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                {isLoading ? (
                    <div className="flex items-center justify-center h-40">
                        <div className="animate-spin w-6 h-6 border-4 border-blue-600 border-t-transparent rounded-full" />
                    </div>
                ) : (
                    <table className="w-full text-sm">
                        <thead>
                        <tr className="bg-gray-50 border-b border-gray-100">
                            <th className="text-left px-4 py-3 font-medium text-gray-500">IATA</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Tên sân bay</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Thành phố</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Quốc gia</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Timezone</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                        {airports.map((airport: any) => (
                            <tr key={airport.iataCode} className="hover:bg-gray-50">
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-2">
                                        <MapPin className="w-4 h-4 text-blue-500" />
                                        <span className="font-bold text-blue-700">{airport.iataCode}</span>
                                    </div>
                                </td>
                                <td className="px-4 py-3 text-gray-700">{airport.name}</td>
                                <td className="px-4 py-3 text-gray-600">{airport.city}</td>
                                <td className="px-4 py-3 text-gray-600">{airport.country}</td>
                                <td className="px-4 py-3 text-gray-500 text-xs">{airport.timezone}</td>
                                <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${airport.isActive ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                      {airport.isActive ? "Hoạt động" : "Ngừng"}
                    </span>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Modal tạo mới */}
            <Modal open={showForm} onClose={() => { setShowForm(false); reset(); }}>
                <form onSubmit={handleSubmit((data) => createMutation.mutate(data))} className="space-y-4">
                    {fields.map((f) => (
                        <div key={f.name}>
                            <label className="block text-xs font-medium text-gray-600 mb-1">{f.label}</label>
                            <input
                                {...register(f.name)}
                                placeholder={f.placeholder}
                                className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            {errors[f.name] && (
                                <p className="text-red-500 text-xs mt-1">{errors[f.name]?.message}</p>
                            )}
                        </div>
                    ))}
                    <div className="flex gap-3 pt-2">
                        <button
                            type="button"
                            onClick={() => { setShowForm(false); reset(); }}
                            className="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50"
                        >
                            Hủy
                        </button>
                        <button
                            type="submit"
                            disabled={createMutation.isPending}
                            className="flex-1 px-4 py-2 bg-blue-700 text-white rounded-lg text-sm font-medium hover:bg-blue-800 disabled:opacity-60"
                        >
                            {createMutation.isPending ? "Đang tạo..." : "Tạo sân bay"}
                        </button>
                    </div>
                </form>
            </Modal>
        </div>
    );
}