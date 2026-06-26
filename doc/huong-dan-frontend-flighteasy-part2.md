# Hướng dẫn Frontend FlightEasy — Phần 2: Admin Panel

> **Tiếp theo:** [Phần 1](huong-dan-frontend-flighteasy__1_.md) đã cover Module 0–8 (Auth, Search, Booking, Payment, Dashboard cơ bản)  
> **Phần này bổ sung:** Admin Layout + Sidebar + các trang quản lý còn thiếu

---

## Mục lục

| Module | Nội dung |
|--------|----------|
| A1 | Admin Layout + Sidebar navigation |
| A2 | Airports Management (Danh sách + Tạo mới) |
| A3 | Airlines Management (Danh sách + Tạo mới) |
| A4 | Flights Management (Danh sách + Tạo mới + Cập nhật status) |
| A5 | Cập nhật admin.api.ts + App.tsx routes |

---

## Module A1 — Admin Layout & Sidebar

### `src/layouts/AdminLayout.tsx`

```tsx
import { useState } from "react";
import { Link, useLocation, useNavigate, Outlet } from "react-router-dom";
import {
  LayoutDashboard, Plane, MapPin, Briefcase, BookOpen,
  ChevronLeft, ChevronRight, LogOut, Menu, X,
} from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { authApi } from "@/api/auth.api";
import toast from "react-hot-toast";

const NAV_ITEMS = [
  { path: "/admin", label: "Dashboard", icon: LayoutDashboard, exact: true },
  { path: "/admin/flights", label: "Chuyến bay", icon: Plane },
  { path: "/admin/airports", label: "Sân bay", icon: MapPin },
  { path: "/admin/airlines", label: "Hãng bay", icon: Briefcase },
  { path: "/admin/bookings", label: "Booking", icon: BookOpen },
];

export default function AdminLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = async () => {
    try { await authApi.logout(); } catch { }
    logout();
    toast.success("Đã đăng xuất");
    navigate("/login");
  };

  const isActive = (item: typeof NAV_ITEMS[0]) =>
    item.exact
      ? location.pathname === item.path
      : location.pathname.startsWith(item.path);

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
```

---

## Module A2 — Airports Management

### A2.1 Thêm vào `src/api/admin.api.ts`

```ts
// Thêm các method này vào adminApi object:

// Airport
createAirport: (data: {
  iataCode: string;
  name: string;
  city: string;
  country: string;
  countryCode: string;
  timezone: string;
}) => api.post("/v1/admin/airports", data),

// Airline
getAirlines: () => api.get("/v1/admin/airlines"),

createAirline: (data: {
  iataCode: string;
  name: string;
  country?: string;
  logoUrl?: string;
}) => api.post("/v1/admin/airlines", data),

// Flight
getAdminFlights: (params?: { page?: number; size?: number; status?: string }) =>
  api.get("/v1/admin/flights", { params }),

createFlight: (data: {
  flightNumber: string;
  airlineId: number;
  aircraftTypeId?: number;
  originIata: string;
  destinationIata: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  terminal?: string;
  gate?: string;
  flightClasses: Array<{
    classType: string;
    basePrice: number;
    totalSeats: number;
    baggageAllowanceKg: number;
    isRefundable: boolean;
  }>;
}) => api.post("/v1/admin/flights", data),

updateFlightStatus: (id: number, status: string) =>
  api.patch(`/v1/admin/flights/${id}/status`, { status }),
```

### A2.2 `src/pages/admin/AirportsPage.tsx`

```tsx
import { useState } from "react";
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
  timezone: z.string().min(1, "Vui lòng nhập timezone"),
});

type AirportForm = z.infer<typeof airportSchema>;

function Modal({ open, onClose, children }: { open: boolean; onClose: () => void; children: React.ReactNode }) {
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

  const { data, isLoading } = useQuery({
    queryKey: ["airports"],
    queryFn: () => flightApi.getAirports(),
  });

  const { register, handleSubmit, reset, formState: { errors } } = useForm<AirportForm>({
    resolver: zodResolver(airportSchema),
    defaultValues: { timezone: "Asia/Ho_Chi_Minh", countryCode: "VN", country: "Vietnam" },
  });

  const createMutation = useMutation({
    mutationFn: (data: AirportForm) => adminApi.createAirport(data),
    onSuccess: () => {
      toast.success("Đã tạo sân bay");
      queryClient.invalidateQueries({ queryKey: ["airports"] });
      reset();
      setShowForm(false);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Tạo thất bại");
    },
  });

  const airports = data?.data || [];

  const fields = [
    { name: "iataCode" as const, label: "Mã IATA (3 ký tự)", placeholder: "SGN" },
    { name: "name" as const, label: "Tên sân bay", placeholder: "Tân Sơn Nhất International Airport" },
    { name: "city" as const, label: "Thành phố", placeholder: "Hồ Chí Minh" },
    { name: "country" as const, label: "Quốc gia", placeholder: "Vietnam" },
    { name: "countryCode" as const, label: "Mã quốc gia (2 ký tự)", placeholder: "VN" },
    { name: "timezone" as const, label: "Timezone", placeholder: "Asia/Ho_Chi_Minh" },
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
```

---

## Module A3 — Airlines Management

### `src/pages/admin/AirlinesPage.tsx`

```tsx
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
  logoUrl: z.string().url("URL logo không hợp lệ").optional().or(z.literal("")),
});

type AirlineForm = z.infer<typeof airlineSchema>;

export default function AirlinesPage() {
  const [showForm, setShowForm] = useState(false);
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin-airlines"],
    queryFn: () => adminApi.getAirlines(),
  });

  const { register, handleSubmit, reset, formState: { errors } } = useForm<AirlineForm>({
    resolver: zodResolver(airlineSchema),
    defaultValues: { country: "Vietnam" },
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
    },
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
```

---

## Module A4 — Flights Management

### `src/pages/admin/FlightsPage.tsx`

```tsx
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useForm, useFieldArray } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { adminApi } from "@/api/admin.api";
import { flightApi } from "@/api/flight.api";
import { format, parseISO } from "date-fns";
import { Plus, X, Plane, ChevronDown } from "lucide-react";
import toast from "react-hot-toast";

const flightClassSchema = z.object({
  classType: z.enum(["ECONOMY", "BUSINESS", "FIRST"]),
  basePrice: z.coerce.number().min(1, "Giá phải > 0"),
  totalSeats: z.coerce.number().min(1, "Số ghế phải > 0"),
  baggageAllowanceKg: z.coerce.number().min(0),
  isRefundable: z.boolean(),
});

const flightSchema = z.object({
  flightNumber: z.string().min(2, "Số hiệu chuyến bay không hợp lệ"),
  airlineId: z.coerce.number().min(1, "Vui lòng chọn hãng bay"),
  originIata: z.string().length(3, "Mã sân bay đi phải đúng 3 ký tự").toUpperCase(),
  destinationIata: z.string().length(3, "Mã sân bay đến phải đúng 3 ký tự").toUpperCase(),
  departureTime: z.string().min(1, "Vui lòng chọn giờ khởi hành"),
  arrivalTime: z.string().min(1, "Vui lòng chọn giờ đến"),
  durationMinutes: z.coerce.number().min(1, "Thời gian bay phải > 0"),
  terminal: z.string().optional(),
  gate: z.string().optional(),
  flightClasses: z.array(flightClassSchema).min(1, "Cần ít nhất 1 hạng ghế"),
});

type FlightForm = z.infer<typeof flightSchema>;

const STATUS_COLORS: Record<string, string> = {
  SCHEDULED: "bg-blue-100 text-blue-700",
  BOARDING: "bg-amber-100 text-amber-700",
  DEPARTED: "bg-purple-100 text-purple-700",
  ARRIVED: "bg-green-100 text-green-700",
  DELAYED: "bg-orange-100 text-orange-700",
  CANCELLED: "bg-red-100 text-red-700",
};

const STATUS_OPTIONS = ["SCHEDULED", "BOARDING", "DELAYED", "DEPARTED", "ARRIVED", "CANCELLED"];

const formatCurrency = (n: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(n);

export default function FlightsPage() {
  const [showForm, setShowForm] = useState(false);
  const [statusTarget, setStatusTarget] = useState<{ id: number; current: string } | null>(null);
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const { data: airportsData } = useQuery({
    queryKey: ["airports"],
    queryFn: () => flightApi.getAirports(),
    staleTime: Infinity,
  });

  const { data: airlinesData } = useQuery({
    queryKey: ["admin-airlines"],
    queryFn: () => adminApi.getAirlines(),
    staleTime: Infinity,
  });

  const { data, isLoading } = useQuery({
    queryKey: ["admin-flights", page],
    queryFn: () => adminApi.getAdminFlights({ page, size: 15 }),
  });

  const { register, handleSubmit, reset, control, watch, formState: { errors } } = useForm<FlightForm>({
    resolver: zodResolver(flightSchema),
    defaultValues: {
      flightClasses: [
        { classType: "ECONOMY", basePrice: 1000000, totalSeats: 150, baggageAllowanceKg: 23, isRefundable: false },
      ],
    },
  });

  const { fields, append, remove } = useFieldArray({ control, name: "flightClasses" });

  const createMutation = useMutation({
    mutationFn: (data: FlightForm) => adminApi.createFlight(data),
    onSuccess: () => {
      toast.success("Đã tạo chuyến bay");
      queryClient.invalidateQueries({ queryKey: ["admin-flights"] });
      reset();
      setShowForm(false);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Tạo thất bại");
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      adminApi.updateFlightStatus(id, status),
    onSuccess: () => {
      toast.success("Đã cập nhật trạng thái");
      queryClient.invalidateQueries({ queryKey: ["admin-flights"] });
      setStatusTarget(null);
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Cập nhật thất bại");
    },
  });

  const flights = data?.data?.content ?? data?.data ?? [];
  const totalPages = data?.data?.totalPages ?? 1;
  const airports: any[] = airportsData?.data || [];
  const airlines: any[] = airlinesData?.data || [];

  // Tự tính durationMinutes khi thay đổi giờ
  const depTime = watch("departureTime");
  const arrTime = watch("arrivalTime");

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chuyến bay</h1>
          <p className="text-gray-500 text-sm mt-1">Quản lý lịch bay</p>
        </div>
        <button
          onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-800"
        >
          <Plus className="w-4 h-4" />
          Tạo chuyến bay
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
                <th className="text-left px-4 py-3 font-medium text-gray-500">Số hiệu</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Hành trình</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Khởi hành</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Hạ cánh</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Trạng thái</th>
                <th className="text-left px-4 py-3 font-medium text-gray-500">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {flights.map((flight: any) => (
                <tr key={flight.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center text-xs font-bold text-blue-700">
                        {flight.airline?.iataCode || flight.airlineCode}
                      </div>
                      <span className="font-medium text-gray-900">{flight.flightNumber}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-gray-700">
                    {flight.origin?.iataCode || flight.originIata} →{" "}
                    {flight.destination?.iataCode || flight.destinationIata}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {flight.departureTime
                      ? format(parseISO(flight.departureTime), "dd/MM HH:mm")
                      : "—"}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {flight.arrivalTime
                      ? format(parseISO(flight.arrivalTime), "dd/MM HH:mm")
                      : "—"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[flight.status] ?? "bg-gray-100 text-gray-600"}`}>
                      {flight.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => setStatusTarget({ id: flight.id, current: flight.status })}
                      className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 font-medium"
                    >
                      Đổi trạng thái
                      <ChevronDown className="w-3 h-3" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex justify-center gap-2 p-4 border-t border-gray-50">
            <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50">
              ← Trước
            </button>
            <span className="px-3 py-1.5 text-sm text-gray-600">
              Trang {page + 1} / {totalPages}
            </span>
            <button disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}
              className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50">
              Sau →
            </button>
          </div>
        )}
      </div>

      {/* Modal đổi trạng thái */}
      {statusTarget && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
            <h2 className="font-bold text-gray-900 mb-4">Cập nhật trạng thái chuyến bay</h2>
            <p className="text-sm text-gray-500 mb-4">
              Hiện tại: <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[statusTarget.current]}`}>{statusTarget.current}</span>
            </p>
            <div className="grid grid-cols-2 gap-2">
              {STATUS_OPTIONS.filter((s) => s !== statusTarget.current).map((status) => (
                <button
                  key={status}
                  onClick={() => updateStatusMutation.mutate({ id: statusTarget.id, status })}
                  disabled={updateStatusMutation.isPending}
                  className={`px-3 py-2 rounded-lg text-xs font-medium border border-gray-200 hover:border-blue-300 hover:bg-blue-50 transition-colors disabled:opacity-60`}
                >
                  {status}
                </button>
              ))}
            </div>
            <button
              onClick={() => setStatusTarget(null)}
              className="w-full mt-4 px-4 py-2 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50"
            >
              Hủy
            </button>
          </div>
        </div>
      )}

      {/* Modal tạo chuyến bay */}
      {showForm && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4 py-6 overflow-y-auto">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl my-auto">
            <div className="flex items-center justify-between p-5 border-b border-gray-100">
              <h2 className="font-bold text-gray-900">Tạo chuyến bay mới</h2>
              <button onClick={() => { setShowForm(false); reset(); }} className="p-1 rounded-lg hover:bg-gray-100">
                <X className="w-4 h-4 text-gray-500" />
              </button>
            </div>

            <form onSubmit={handleSubmit((data) => createMutation.mutate(data))} className="p-5 space-y-5">
              {/* Thông tin cơ bản */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Số hiệu chuyến bay</label>
                  <input {...register("flightNumber")} placeholder="VJ123"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  {errors.flightNumber && <p className="text-red-500 text-xs mt-1">{errors.flightNumber.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Hãng bay</label>
                  <select {...register("airlineId")}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">-- Chọn hãng bay --</option>
                    {airlines.map((a: any) => (
                      <option key={a.id} value={a.id}>{a.name} ({a.iataCode})</option>
                    ))}
                  </select>
                  {errors.airlineId && <p className="text-red-500 text-xs mt-1">{errors.airlineId.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Sân bay đi</label>
                  <select {...register("originIata")}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">-- Chọn sân bay đi --</option>
                    {airports.map((a: any) => (
                      <option key={a.iataCode} value={a.iataCode}>{a.iataCode} — {a.city}</option>
                    ))}
                  </select>
                  {errors.originIata && <p className="text-red-500 text-xs mt-1">{errors.originIata.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Sân bay đến</label>
                  <select {...register("destinationIata")}
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">-- Chọn sân bay đến --</option>
                    {airports.map((a: any) => (
                      <option key={a.iataCode} value={a.iataCode}>{a.iataCode} — {a.city}</option>
                    ))}
                  </select>
                  {errors.destinationIata && <p className="text-red-500 text-xs mt-1">{errors.destinationIata.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Giờ khởi hành</label>
                  <input {...register("departureTime")} type="datetime-local"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  {errors.departureTime && <p className="text-red-500 text-xs mt-1">{errors.departureTime.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Giờ đến</label>
                  <input {...register("arrivalTime")} type="datetime-local"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  {errors.arrivalTime && <p className="text-red-500 text-xs mt-1">{errors.arrivalTime.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Thời gian bay (phút)</label>
                  <input {...register("durationMinutes")} type="number" placeholder="120"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  {errors.durationMinutes && <p className="text-red-500 text-xs mt-1">{errors.durationMinutes.message}</p>}
                </div>

                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Terminal</label>
                  <input {...register("terminal")} placeholder="T1"
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>

              {/* Hạng ghế */}
              <div>
                <div className="flex items-center justify-between mb-3">
                  <label className="text-xs font-medium text-gray-600">Hạng ghế</label>
                  <button
                    type="button"
                    onClick={() => append({ classType: "BUSINESS", basePrice: 3000000, totalSeats: 20, baggageAllowanceKg: 30, isRefundable: true })}
                    className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800 font-medium"
                  >
                    <Plus className="w-3 h-3" /> Thêm hạng
                  </button>
                </div>

                {fields.map((field, index) => (
                  <div key={field.id} className="border border-gray-200 rounded-xl p-4 mb-3 relative">
                    {fields.length > 1 && (
                      <button type="button" onClick={() => remove(index)}
                        className="absolute top-2 right-2 p-1 rounded hover:bg-gray-100">
                        <X className="w-3 h-3 text-gray-400" />
                      </button>
                    )}
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Hạng</label>
                        <select {...register(`flightClasses.${index}.classType`)}
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                          <option value="ECONOMY">Economy</option>
                          <option value="BUSINESS">Business</option>
                          <option value="FIRST">First Class</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Giá cơ bản (VND)</label>
                        <input {...register(`flightClasses.${index}.basePrice`)} type="number"
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Tổng ghế</label>
                        <input {...register(`flightClasses.${index}.totalSeats`)} type="number"
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Hành lý (kg)</label>
                        <input {...register(`flightClasses.${index}.baggageAllowanceKg`)} type="number"
                          className="w-full px-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div className="flex items-center gap-2 col-span-2">
                        <input {...register(`flightClasses.${index}.isRefundable`)} type="checkbox" id={`refund-${index}`}
                          className="rounded" />
                        <label htmlFor={`refund-${index}`} className="text-xs text-gray-600">Cho phép hoàn vé</label>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => { setShowForm(false); reset(); }}
                  className="flex-1 px-4 py-2 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50">
                  Hủy
                </button>
                <button type="submit" disabled={createMutation.isPending}
                  className="flex-1 px-4 py-2 bg-blue-700 text-white rounded-lg text-sm font-medium hover:bg-blue-800 disabled:opacity-60">
                  {createMutation.isPending ? "Đang tạo..." : "Tạo chuyến bay"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
```

---

## Module A5 — Cập nhật App.tsx

### Thêm routes Admin dùng `AdminLayout`

```tsx
// Thêm imports
import AdminLayout from "@/layouts/AdminLayout";
import AirportsPage from "@/pages/admin/AirportsPage";
import AirlinesPage from "@/pages/admin/AirlinesPage";
import FlightsPage from "@/pages/admin/FlightsPage";

// Thay toàn bộ Admin routes bằng nested routes dùng AdminLayout:
<Route
  path="/admin"
  element={
    <AdminRoute>
      <AdminLayout />
    </AdminRoute>
  }
>
  <Route index element={<DashboardPage />} />
  <Route path="bookings" element={<BookingsManagePage />} />
  <Route path="airports" element={<AirportsPage />} />
  <Route path="airlines" element={<AirlinesPage />} />
  <Route path="flights" element={<FlightsPage />} />
</Route>
```

> **Lưu ý:** `AdminLayout` dùng `<Outlet />` từ React Router, nên các page admin render tự động vào đúng vị trí trong layout. Không cần bọc `MainLayout` cho admin routes vì `AdminLayout` đã có sidebar + header riêng.

---

## Thêm endpoints BE còn thiếu

BE hiện chưa có `GET /v1/admin/airlines` và `GET /v1/admin/flights`. Thêm vào `AdminController`:

```java
// Inject thêm
private final FlightService flightService;
private final AirlineRepository airlineRepository; // hoặc AirlineService

@GetMapping("/airlines")
public ResponseEntity<List<Airline>> getAllAirlines() {
    return ResponseEntity.ok(airlineRepository.findByIsActiveTrue());
}

@PostMapping("/airlines")
public ResponseEntity<Airline> createAirline(@Valid @RequestBody Airline airline) {
    return ResponseEntity.status(201).body(flightService.createAirline(airline));
}

@GetMapping("/flights")
public ResponseEntity<Page<FlightResponse>> getAllFlights(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "15") int size,
        @RequestParam(required = false) String status) {
    return ResponseEntity.ok(flightService.getAllFlights(status, page, size));
}
```

Và thêm `getAllFlights` + `createAirline` vào `FlightService`.

---

## Cấu trúc thư mục Admin sau khi hoàn thiện

```
src/
├── layouts/
│   └── AdminLayout.tsx          ← Sidebar + Header layout
├── pages/
│   └── admin/
│       ├── DashboardPage.tsx    ← Đã có (Module 7)
│       ├── BookingsManagePage.tsx ← Đã có (Module 7)
│       ├── AirportsPage.tsx     ← Mới (Module A2)
│       ├── AirlinesPage.tsx     ← Mới (Module A3)
│       └── FlightsPage.tsx      ← Mới (Module A4)
```

---

## Checklist test Admin Panel

| Trang | Test case |
|-------|-----------|
| `/admin` | KPI cards load, nút Export Excel hoạt động |
| `/admin/airports` | Danh sách sân bay, tạo mới SGN/HAN thành công |
| `/admin/airlines` | Danh sách hãng bay, tạo VJ/VN thành công |
| `/admin/flights` | Tạo chuyến bay với ít nhất 1 hạng Economy |
| `/admin/flights` | Đổi trạng thái SCHEDULED → BOARDING |
| `/admin/bookings` | Lọc theo status, hủy booking với lý do |
| Sidebar | Click menu qua lại giữa các trang mượt |
| Phân quyền | User thường truy cập `/admin` → redirect `/` |
