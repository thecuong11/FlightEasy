import { useState} from "react";
import {Link, useLocation, useNavigate, Outlet} from "react-router-dom";
import {
    LayoutDashboard, Plane, MapPin, Briefcase, BookOpen,
    ChevronLeft, ChevronRight, LogOut, Menu} from "lucide-react";
import {useAuthStore} from "@/store/authStore.ts";
import {authApi} from "@/api/auth.api.ts";
import toast from "react-hot-toast";

const NAV_ITEMS = [
    {path: "/admin", label: "Dashboard", icon: LayoutDashboard, exact: true},
    {path: "/admin/flights", label: "Chuyến bay", icon: Plane},
    {path: "/admin/airports", label: "Sân bay", icon: MapPin},
    {path: "/admin/airlines", label: "Hãng bay", icon: Briefcase},
    {path: "/admin/bookings", label: "Booking", icon: BookOpen}
];

export default function AdminLayout() {
    const location = useLocation();
    const navigate = useNavigate();
    const {user, logout} = useAuthStore();
    const [collapsed, setCollapsed] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);

    const handleLogout = async () => {
        try {
            await authApi.logout();
        } catch {

        }
        logout();
        toast.success("Đã đăng xuất");
        navigate("/login");
    };

    const isActive = (item: typeof NAV_ITEMS[0]) =>
        item.exact ? location.pathname ===item.path : location.pathname.startsWith(item.path);

    const SidebarContent = () => (
        <div className="flex flex-col h-full">
            {/* Logo */}
            <div className={`flex items-center gap-3 px-4 py-5 border-b border-gray-100 ${collapsed ? "justify-center" : ""}`}>
                <div className="w-8 h-8 bg-blue-700 rounded-lg flex items-center justify-center shrink-0">
                    <Plane className="w-4 h-4 text-white" />
                </div>
                {!collapsed && (
                    <div>
                        <p className="font-bold text-gray-900 text-sm leading-tight">FlightEasy</p>
                        <p className="text-xs text-gray-400">Admin Panel</p>
                    </div>
                )}
            </div>

            {/* Nav */}
            <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
                {NAV_ITEMS.map((item) => {
                    const active = isActive(item);
                    return (
                        <Link
                            key={item.path}
                            to={item.path}
                            onClick={() => setMobileOpen(false)}
                            title={collapsed ? item.label : undefined}
                            className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors
                ${active
                                ? "bg-blue-700 text-white"
                                : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                            }
                ${collapsed ? "justify-center" : ""}
              `}
                        >
                            <item.icon className="w-4 h-4 shrink-0" />
                            {!collapsed && item.label}
                        </Link>
                    );
                })}
            </nav>

            {/* User + Logout */}
            <div className={`p-3 border-t border-gray-100 ${collapsed ? "flex justify-center" : ""}`}>
                {!collapsed && (
                    <div className="px-3 py-2 mb-1">
                        <p className="text-xs font-medium text-gray-900 truncate">{user?.fullName}</p>
                        <p className="text-xs text-gray-400 truncate">{user?.email}</p>
                    </div>
                )}
                <button
                    onClick={handleLogout}
                    title={collapsed ? "Đăng xuất" : undefined}
                    className={`flex items-center gap-2 px-3 py-2 rounded-lg text-sm text-red-500 hover:bg-red-50 w-full transition-colors
            ${collapsed ? "justify-center" : ""}
          `}
                >
                    <LogOut className="w-4 h-4 shrink-0" />
                    {!collapsed && "Đăng xuất"}
                </button>
            </div>
        </div>
    );

    return (
        <div className="flex h-screen bg-gray-50 overflow-hidden">
            {/* Desktop Sidebar */}
            <aside
                className={`hidden md:flex flex-col bg-white border-r border-gray-100 shadow-sm transition-all duration-300
          ${collapsed ? "w-16" : "w-56"}
        `}
            >
                <SidebarContent />
                {/* Collapse toggle */}
                <button
                    onClick={() => setCollapsed((c) => !c)}
                    className="absolute left-0 top-20 translate-x-full bg-white border border-gray-200 rounded-r-lg p-1 shadow-sm hover:bg-gray-50"
                    style={{ left: collapsed ? "3.5rem" : "13.5rem" }}
                >
                    {collapsed
                        ? <ChevronRight className="w-3 h-3 text-gray-400" />
                        : <ChevronLeft className="w-3 h-3 text-gray-400" />
                    }
                </button>
            </aside>

            {/* Mobile overlay */}
            {mobileOpen && (
                <div
                    className="md:hidden fixed inset-0 bg-black/40 z-40"
                    onClick={() => setMobileOpen(false)}
                />
            )}

            {/* Mobile Sidebar */}
            <aside
                className={`md:hidden fixed left-0 top-0 bottom-0 w-56 bg-white shadow-xl z-50 transform transition-transform duration-300
          ${mobileOpen ? "translate-x-0" : "-translate-x-full"}
        `}
            >
                <SidebarContent />
            </aside>

            {/* Main */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Top bar */}
                <header className="bg-white border-b border-gray-100 h-14 flex items-center px-4 gap-3 shrink-0">
                    <button
                        onClick={() => setMobileOpen(true)}
                        className="md:hidden p-2 rounded-lg hover:bg-gray-100"
                    >
                        <Menu className="w-5 h-5 text-gray-500" />
                    </button>
                    <h2 className="text-sm font-semibold text-gray-600">
                        {NAV_ITEMS.find((n) => isActive(n))?.label ?? "Admin"}
                    </h2>
                </header>

                {/* Page content */}
                <main className="flex-1 overflow-y-auto p-6">
                    <Outlet />
                </main>
            </div>
        </div>
    );
}