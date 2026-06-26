import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { adminApi } from "@/api/admin.api";
import { Plus, X, Briefcase } from "lucide-react";
import toast from "react-hot-toast";

const airlineSchema = z.object({
    iataCode: z.string().length(2, "Mã IATA hãng bay phải đúng 2 ký tự").toUpperCase(),
    name: z.string().min(2, "Tên hãng bay quá ngắn"),
    country: z.string().optional(),
    logoUrl: z.string().url("URL logo không hợp lệ").optional().or(z.literal(""))
});

type AirlineForm = z.infer<typeof airlineSchema>;

export default function AirlinesPage() {
    const [showForm, setShowForm] = useState(false);
    const queryClient = useQueryClient();

    const {data, isLoading} = useQuery({
        queryKey: ["admin-airlines"],
        queryFn: () => adminApi.getAirlines()
    });

    const {register, handleSubmit, reset, formState: {errors}} = useForm<AirlineForm>({
        resolver: zodResolver(airlineSchema),
        defaultValues: {country: "VietNam"}
    });

    const createMutation = useMutation({
        mutationFn: (data: AirlineForm) => adminApi.createAirline(data),
        onSuccess: () => {
            toast.success("Đã tạo hãng bay");
            queryClient.invalidateQueries({ queryKey: ["admin-airlines"] });
            reset();
            setShowForm(false);
        },
        onError: (err: any) => {
            toast.error(err.response?.data?.message || "Tạo thất bại");
        }
    });

    const airlines = data?.data || [];

    return (
        <div>
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Hãng bay</h1>
                    <p className="text-gray-500 text-sm mt-1">{airlines.length} hãng bay</p>
                </div>
                <button
                    onClick={() => setShowForm(true)}
                    className="flex items-center gap-2 bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-800"
                >
                    <Plus className="w-4 h-4" />
                    Thêm hãng bay
                </button>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                {isLoading ? (
                    <div className="flex items-center justify-center h-40">
                        <div className="animate-spin w-6 h-6 border-4 border-blue-600 border-t-transparent rounded-full" />
                    </div>
                ) : (
                    <table className="w-full text-sm">
                        <thead>
                        <tr className="bg-gray-50 border-b border-gray-100">
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Hãng bay</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">IATA</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Quốc gia</th>
                            <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái</th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-50">
                        {airlines.map((airline: any) => (
                            <tr key={airline.id} className="hover:bg-gray-50">
                                <td className="px-4 py-3">
                                    <div className="flex items-center gap-3">
                                        {airline.logoUrl ? (
                                            <img src={airline.logoUrl} alt={airline.name} className="w-8 h-8 rounded object-contain" />
                                        ) : (
                                            <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center">
                                                <Briefcase className="w-4 h-4 text-blue-600" />
                                            </div>
                                        )}
                                        <span className="font-medium text-gray-900">{airline.name}</span>
                                    </div>
                                </td>
                                <td className="px-4 py-3 font-bold text-blue-700">{airline.iataCode}</td>
                                <td className="px-4 py-3 text-gray-600">{airline.country || "—"}</td>
                                <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded-full text-xs font-medium ${airline.isActive ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                      {airline.isActive ? "Hoạt động" : "Ngừng"}
                    </span>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Modal */}
            {showForm && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
                    <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
                        <div className="flex items-center justify-between p-5 border-b border-gray-100">
                            <h2 className="font-bold text-gray-900">Thêm hãng bay mới</h2>
                            <button onClick={() => { setShowForm(false); reset(); }} className="p-1 rounded-lg hover:bg-gray-100">
                                <X className="w-4 h-4 text-gray-500" />
                            </button>
                        </div>
                        <form onSubmit={handleSubmit((data) => createMutation.mutate(data))} className="p-5 space-y-4">
                            {[
                                { name: "iataCode" as const, label: "Mã IATA (2 ký tự)", placeholder: "VJ" },
                                { name: "name" as const, label: "Tên hãng bay", placeholder: "VietJet Air" },
                                { name: "country" as const, label: "Quốc gia", placeholder: "Vietnam" },
                                { name: "logoUrl" as const, label: "URL Logo (tuỳ chọn)", placeholder: "https://..." },
                            ].map((f) => (
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
                                <button type="button" onClick={() => { setShowForm(false); reset(); }}
                                        className="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50">
                                    Hủy
                                </button>
                                <button type="submit" disabled={createMutation.isPending}
                                        className="flex-1 px-4 py-2 bg-blue-700 text-white rounded-lg text-sm font-medium hover:bg-blue-800 disabled:opacity-60">
                                    {createMutation.isPending ? "Đang tạo..." : "Tạo hãng bay"}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}