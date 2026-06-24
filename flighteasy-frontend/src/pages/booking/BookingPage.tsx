import {useSearchParams, useNavigate} from "react-router-dom";
import {useForm, useFieldArray} from "react-hook-form";
import {useMutation} from "@tanstack/react-query";
import toast from "react-hot-toast";
import {User, Mail} from "lucide-react";
import {bookingApi} from "@/api/booking.api.ts";
import {useAuthStore} from "@/store/authStore.ts";
import type {CreateBookingPayload} from "@/api/booking.api.ts";

interface PassengerField {
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    gender: string;
    nationality: string;
    idType: string;
    idNumber: string;
    passengerType: string;
}

interface BookingForm {
    contactEmail: string;
    contactPhone: string;
    passengers: PassengerField[];
}

export default function BookingPage() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const {user} = useAuthStore();
    const flightClassId = Number(searchParams.get("flightClassId"));
    const adults = Number(searchParams.get("adults") || 1);

    const {register, handleSubmit, control} = useForm<BookingForm>({
        defaultValues: {
            contactEmail: user?.email || "",
            passengers: Array.from({length: adults}, () => ({
                firstName: "",
                lastName: "",
                dateOfBirth: "",
                gender: "MALE",
                nationality: "VN",
                idType: "CCCD",
                idNumber: "",
                passengerType: "ADULT",
            })),
        },
    });

    const {fields} = useFieldArray({control, name: "passengers"});

    const mutation = useMutation({
        mutationFn: (payload: CreateBookingPayload) => bookingApi.createBooking(payload),
        onSuccess: (res) => {
            toast.success("Đặt vé thành công!");
            navigate(`/booking/confirm/${res.data.pnrCode}`);
        },
    });

    const onSubmit = (data: BookingForm) => {
        mutation.mutate({
            flightClassId,
            contactEmail: data.contactEmail,
            contactPhone: data.contactPhone,
            passengers: data.passengers,
            selectedSeatIds: [],
        });
    };

    return (
        <div className="max-w-3xl mx-auto px-4 py-8">
            <h1 className="text-2xl font-bold text-gray-900 mb-6">Thông tin hành khách</h1>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                {/* Contact info */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                    <h2 className="font-semibold text-gray-800 mb-4 flex items-center gap-2">
                        <Mail className="w-4 h-4" />
                        Thông tin liên hệ
                    </h2>
                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
                            <input
                                {...register("contactEmail", { required: true })}
                                type="email"
                                className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại</label>
                            <input
                                {...register("contactPhone")}
                                type="tel"
                                className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                        </div>
                    </div>
                </div>

                {/* Passengers */}
                {fields.map((field, index) => (
                    <div key={field.id} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                        <h2 className="font-semibold text-gray-800 mb-4 flex items-center gap-2">
                            <User className="w-4 h-4" />
                            Hành khách {index + 1}
                        </h2>
                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Họ</label>
                                <input
                                    {...register(`passengers.${index}.lastName`, { required: true })}
                                    placeholder="NGUYEN"
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Tên đệm & tên</label>
                                <input
                                    {...register(`passengers.${index}.firstName`, { required: true })}
                                    placeholder="VAN A"
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Ngày sinh</label>
                                <input
                                    {...register(`passengers.${index}.dateOfBirth`, { required: true })}
                                    type="date"
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Giới tính</label>
                                <select
                                    {...register(`passengers.${index}.gender`)}
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    <option value="MALE">Nam</option>
                                    <option value="FEMALE">Nữ</option>
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Loại giấy tờ</label>
                                <select
                                    {...register(`passengers.${index}.idType`)}
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                >
                                    <option value="CCCD">CCCD</option>
                                    <option value="PASSPORT">Hộ chiếu</option>
                                </select>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Số giấy tờ</label>
                                <input
                                    {...register(`passengers.${index}.idNumber`, { required: true })}
                                    className="w-full px-4 py-2.5 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                                />
                            </div>
                        </div>
                    </div>
                ))}

                <button
                    type="submit"
                    disabled={mutation.isPending}
                    className="w-full bg-blue-700 text-white py-3.5 rounded-xl font-semibold text-lg hover:bg-blue-800 transition-colors disabled:opacity-60"
                >
                    {mutation.isPending ? "Đang đặt vé..." : "Xác nhận đặt vé"}
                </button>
            </form>
        </div>
    )
}