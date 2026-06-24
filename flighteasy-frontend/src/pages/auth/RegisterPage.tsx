import {useState} from "react";
import {useNavigate, Link} from "react-router-dom";
import {useForm} from "react-hook-form";
import {z} from "zod";
import {zodResolver} from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import {Plane} from "lucide-react";
import {authApi} from "@/api/auth.api.ts";
import {useAuthstore} from "@/store/authStore.ts";
const registerSchema = z
    .object({
    fullName: z.string().min(2, "Tên phải có ít nhất 2 ký tự"),
    email: z.string().email("Email không hợp lệ"),
    password: z.string().regex(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/, "Mật khẩu phải có chữ hoa, chữ thường, s và ít nhâ 8 ký tự"),
    confirmPassword: z.string(),
    })
    .refine((d) => d.password === d.confirmPassword, {
        message: "Mật khẩu không khớp",
        path: ["confirmPassword"],
    });

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
    const navigate = useNavigate();
    const {setUser, setToken} = useAuthstore();
    const [loading, setLoading] = useState(false);

    const {
        register,
        handleSubmit,
        formState: {errors},
    } = useForm<RegisterForm>({resolver: zodResolver(registerSchema)});

    const onSubmit = async (data: RegisterForm) => {
        setLoading(true);
        try {
            const res = await authApi.register({
                fullName: data.fullName,
                email: data.email,
                password: data.password,
            });
            setToken(res.data.accessToken);
            setUser(res.data.user);
            toast.success("Đăng ký thành công!");
            navigate("/");
        } catch {

        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-8">
                <div className="text-center mb-8">
                    <div className="inline-flex items-center gap-2 text-blue-700">
                        <Plane className="w-8 h-8" />
                        <span className="text-2xl font-bold">FlightEasy</span>
                    </div>
                    <p className="mt-2 text-gray-500 text-sm">Tạo tài khoản mới</p>
                </div>

                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                    {[
                        { name: "fullName" as const, label: "Họ và tên", type: "text", placeholder: "Nguyễn Văn A" },
                        { name: "email" as const, label: "Email", type: "email", placeholder: "you@example.com" },
                        { name: "password" as const, label: "Mật khẩu", type: "password", placeholder: "••••••••" },
                        { name: "confirmPassword" as const, label: "Xác nhận mật khẩu", type: "password", placeholder: "••••••••" },
                    ].map((field) => (
                        <div key={field.name}>
                            <label className="block text-sm font-medium text-gray-700 mb-1">{field.label}</label>
                            <input
                                {...register(field.name)}
                                type={field.type}
                                placeholder={field.placeholder}
                                className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                            />
                            {errors[field.name] && (
                                <p className="text-red-500 text-xs mt-1">{errors[field.name]?.message}</p>
                            )}
                        </div>
                    ))}

                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full bg-blue-700 text-white py-2.5 rounded-lg font-medium hover:bg-blue-800 transition-colors disabled:opacity-60"
                    >
                        {loading ? "Đang tạo tài khoản..." : "Đăng ký"}
                    </button>
                </form>

                <p className="text-center text-sm text-gray-500 mt-6">
                    Đã có tài khoản?{" "}
                    <Link to="/login" className="text-blue-600 hover:underline font-medium">
                        Đăng nhập
                    </Link>
                </p>
            </div>
        </div>
    )
}
