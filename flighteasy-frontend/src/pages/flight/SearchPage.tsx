import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {useForm} from "react-hook-form";
import { Calendar, Users, ArrowRightLeft} from "lucide-react";
import {format} from "date-fns";
import AirportAutocomplete from "@/components/airport/AirportAutocomplete.tsx";

interface SearchForm {
    from: string;
    to: string;
    departDate: string;
    returnDate?: string;
    adults: number;
    children: number;
    infants: number;
    classType: string;
    tripType: "one-way" | "round-trip";
}

export default function SearchPage() {
    const navigate = useNavigate();
    const [tripType, setTripType] = useState<"one-way" | "round-trip">("one-way");

    const {register, handleSubmit, setValue, watch} = useForm<SearchForm>({
        defaultValues: {
            adults: 1,
            children: 0,
            infants: 0,
            classType: "ECONOMY",
            tripType: "one-way",
            from: "",
            to: ""
        },
    });

    const swapAirports = () => {
        const from = watch("from");
        const to = watch("to");
        setValue("from", to);
        setValue("to", from);
    };

    const onSubmit = (data: SearchForm) => {
        const params = new URLSearchParams({
            from: data.from,
            to: data.to,
            departDate: data.departDate,
            adults: String(data.adults),
            children: String(data.children),
            infants: String(data.infants),
            classType: data.classType,
        });
        if (tripType === "round-trip" && data.returnDate) {
            params.set("returnDate", data.returnDate);
        }
        navigate(`/search/results?${params.toString()}`);
    };

    const today = format(new Date(), "yyy-MM-dd");

    return (
        <div className="min-h-screen bg-gradient-to-b from-blue-700 to-blue-900">
            {/* Hero */}
            <div className="pt-16 pb-32 text-center text-white px-4">
                <h1 className="text-4xl font-bold mt-2 mb-3">Đặt vé máy bay dễ dàng</h1>
                <p className="text-blue-200">Tìm kiếm hàng nghìn chuyến bay với giá tốt nhất</p>
            </div>

            {/* Search box */}
            <div className="max-w-4xl mx-auto px-4 -mt-20">
                <div className="bg-white rounded-2xl shadow-2xl p-6">
                    {/* Trip type tabs */}
                    <div className="flex gap-4 mb-6">
                        {["one-way", "round-trip"].map((type) => (
                            <button
                                key={type}
                                onClick={() => setTripType(type as "one-way" | "round-trip")}
                                className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                                    tripType === type
                                        ? "bg-blue-700 text-white"
                                        : "text-gray-600 hover:bg-gray-100"
                                }`}
                            >
                                {type === "one-way" ? "Một chiều" : "Khứ hồi"}
                            </button>
                        ))}
                    </div>

                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                        {/* From / To */}
                        <div className="flex items-center gap-2">
                            <div className="flex-1">
                                <label className="block text-xs font-medium text-gray-500 mb-1">Từ</label>
                                <AirportAutocomplete
                                    placeholder="SGN - Hồ Chí Minh"
                                    value={watch("from")}
                                    onChange={(val) => setValue("from", val)}
                                />
                            </div>

                            <button
                                type="button"
                                onClick={swapAirports}
                                className="mt-5 p-2 rounded-full border border-gray-200 hover:bg-gray-50 transition-colors"
                            >
                                <ArrowRightLeft className="w-4 h-4 text-gray-500" />
                            </button>

                            <div className="flex-1">
                                <label className="block text-xs font-medium text-gray-500 mb-1">Đến</label>
                                <AirportAutocomplete
                                    placeholder="HAN - Hà Nội"
                                    value={watch("to")}
                                    onChange={(val) => setValue("to", val)}
                                />
                            </div>
                        </div>

                        {/* Dates */}
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">
                                    <Calendar className="inline w-3 h-3 mr-1" />
                                    Ngày đi
                                </label>
                                <input
                                    {...register("departDate", { required: true })}
                                    type="date"
                                    min={today}
                                    className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>
                            {tripType === "round-trip" && (
                                <div>
                                    <label className="block text-xs font-medium text-gray-500 mb-1">
                                        <Calendar className="inline w-3 h-3 mr-1" />
                                        Ngày về
                                    </label>
                                    <input
                                        {...register("returnDate")}
                                        type="date"
                                        min={today}
                                        className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                    />
                                </div>
                            )}
                        </div>

                        {/* Passengers & class */}
                        <div className="grid grid-cols-4 gap-3">
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">
                                    <Users className="inline w-3 h-3 mr-1" />
                                    Người lớn
                                </label>
                                <select
                                    {...register("adults")}
                                    className="w-full px-3 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
                                        <option key={n} value={n}>{n}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">Trẻ em</label>
                                <select
                                    {...register("children")}
                                    className="w-full px-3 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    {[0, 1, 2, 3, 4].map((n) => (
                                        <option key={n} value={n}>{n}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">Em bé</label>
                                <select
                                    {...register("infants")}
                                    className="w-full px-3 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    {[0, 1, 2].map((n) => (
                                        <option key={n} value={n}>{n}</option>
                                    ))}
                                </select>
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-gray-500 mb-1">Hạng vé</label>
                                <select
                                    {...register("classType")}
                                    className="w-full px-3 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    <option value="ECONOMY">Phổ thông</option>
                                    <option value="BUSINESS">Thương gia</option>
                                    <option value="FIRST_CLASS">Hạng nhất</option>
                                </select>
                            </div>
                        </div>

                        <button
                            type="submit"
                            className="w-full bg-blue-700 text-white py-3.5 rounded-xl font-semibold text-lg hover:bg-blue-800 transition-colors"
                        >
                            Tìm chuyến bay
                        </button>
                    </form>
                </div>
            </div>
        </div>
    );
}