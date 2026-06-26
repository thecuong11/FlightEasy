import {useState} from "react";
import {Link, useNavigate} from "react-router-dom";
import {Plane, LogOut, ChevronDown, LayoutDashboard, Ticket} from "lucide-react";
import {useAuthStore} from "@/store/authStore.ts";
import {authApi} from "@/api/auth.api.ts";
import toast from "react-hot-toast";

export default function Navbar() {
    const {user, isAuthenticated, logout} = useAuthStore();
    const navigate = useNavigate();
    const [dropdownOpen, setDropdownOpen] = useState(false);

    const handleLogout = async () => {
        try {
            await authApi.logout();
        } catch {

        } finally {
            logout();
            toast.success("Đăng xuất thành công");
            navigate("/login");
        }
    };

    return (
        <nav className="bg-white border-b border-gray-100 shadow-sm sticky top-0 z-40">
            <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
                {/* Logo */}
                <Link to="/" className="flex items-center gap-2 text-blue-700 font-bold text-lg">
                    <Plane className="w-5 h-5" />
                    FlightEasy
                </Link>

                {/* Right */}
                <div className="flex items-center gap-3">
                    {isAuthenticated && user ? (
                        <div className="relative">
                            <button
                                onClick={() => setDropdownOpen((o) => !o)}
                                className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-gray-50 transition-colors"
                            >
                                <div className="w-8 h-8 rounded-full bg-blue-700 text-white flex items-center justify-center text-sm font-bold">
                                    {user.fullName?.charAt(0).toUpperCase()}
                                </div>
                                <span className="text-sm font-medium text-gray-700">{user.fullName}</span>
                                <ChevronDown className="w-4 h-4 text-gray-400" />
                            </button>

                            {dropdownOpen && (
                                <div className="absolute right-0 mt-1 w-52 bg-white border border-gray-100 rounded-xl shadow-lg overflow-hidden">
                                    {user.role === "ROLE_ADMIN" && (
                                        <Link
                                            to="/admin"
                                            onClick={() => setDropdownOpen(false)}
                                            className="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50"
                                        >
                                            <LayoutDashboard className="w-4 h-4" />
                                            Admin Dashboard
                                        </Link>
                                    )}
                                    <Link
                                        to="/bookings"
                                        onClick={() => setDropdownOpen(false)}
                                        className="flex items-center gap-2 px-4 py-3 text-sm text-gray-700 hover:bg-gray-50"
                                    >
                                        <Ticket className="w-4 h-4" />
                                        Vé của tôi
                                    </Link>
                                    <hr className="border-gray-100" />
                                    <button
                                        onClick={handleLogout}
                                        className="w-full flex items-center gap-2 px-4 py-3 text-sm text-red-500 hover:bg-red-50"
                                    >
                                        <LogOut className="w-4 h-4" />
                                        Đăng xuất
                                    </button>
                                </div>
                            )}
                        </div>
                    ) : (
                        <>
                            <Link to="/login" className="text-sm text-gray-600 hover:text-blue-700 font-medium">
                                Đăng nhập
                            </Link>
                            <Link
                                to="/register"
                                className="text-sm bg-blue-700 text-white px-4 py-2 rounded-lg hover:bg-blue-800 font-medium"
                            >
                                Đăng ký
                            </Link>
                        </>
                    )}
                </div>
            </div>
        </nav>
    )
}