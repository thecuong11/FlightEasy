# Hướng dẫn Implement Frontend — FlightEasy

> **Stack:** React 18 + Vite + TypeScript + Tailwind CSS + React Query + Axios  
> **Tương ứng với:** BE Spring Boot đã implement (Module 01–08)  
> **Base URL BE:** `http://localhost:8080`

---

## Mục lục

| Module | Nội dung |
|--------|----------|
| 0 | Khởi tạo project & cấu trúc thư mục |
| 1 | Cài đặt & cấu hình (Tailwind v4, Vite, Axios, Zustand) |
| 2 | Auth (Login, Register, Logout) |
| 3 | Flight Search (Tìm chuyến bay) |
| 4 | Booking & Seat Selection + **MyBookingsPage** |
| 5 | Payment VNPay |
| 6 | Email (tự động — chỉ xem log) |
| 7 | Admin Dashboard + **BookingsManagePage** |
| 8 | App.tsx & Route Setup (Token Blacklist interceptor) |

---

## Module 0 — Khởi tạo & Cấu trúc thư mục

### 0.1 Vị trí đặt FE trong project

Đặt FE **song song** với BE (không nằm trong BE):

```
FlightEasy/
├── flighteasy-backend/          ← Spring Boot project (đã làm)
│   ├── src/
│   ├── pom.xml
│   └── ...
└── flighteasy-frontend/         ← Tạo mới ở đây
    ├── src/
    ├── package.json
    └── ...
```

### 0.2 Tạo project React + Vite + TypeScript

```bash
# Vào thư mục cha (ngang hàng với BE)
cd FlightEasy

# Tạo project với Vite
npm create vite@latest flighteasy-frontend -- --template react-ts

cd flighteasy-frontend

# Cài đặt tất cả dependencies cần thiết
npm install

# Cài thêm các package cần dùng
npm install \
  axios \
  @tanstack/react-query \
  react-router-dom \
  react-hook-form \
  @hookform/resolvers \
  zod \
  date-fns \
  lucide-react \
  react-hot-toast \
  clsx \
  tailwind-merge

# Cài Tailwind CSS
npm install -D \
  tailwindcss \
  postcss \
  autoprefixer \
  @types/node

# Init Tailwind
npx tailwindcss init -p
```

### 0.3 Cấu trúc thư mục cuối cùng

```
flighteasy-frontend/
├── public/
│   └── vite.svg
├── src/
│   ├── api/                     ← Tất cả axios API calls
│   │   ├── auth.api.ts
│   │   ├── flight.api.ts
│   │   ├── booking.api.ts
│   │   ├── payment.api.ts
│   │   └── admin.api.ts
│   ├── components/              ← UI components tái sử dụng
│   │   ├── ui/
│   │   │   ├── Button.tsx
│   │   │   ├── Input.tsx
│   │   │   ├── Badge.tsx
│   │   │   └── Spinner.tsx
│   │   ├── layout/
│   │   │   ├── Navbar.tsx
│   │   │   ├── Footer.tsx
│   │   │   └── AdminLayout.tsx
│   │   └── flight/
│   │       ├── FlightCard.tsx
│   │       ├── SeatMap.tsx
│   │       └── PassengerForm.tsx
│   ├── hooks/                   ← Custom hooks
│   │   ├── useAuth.ts
│   │   └── useDebounce.ts
│   ├── pages/                   ← Các trang chính
│   │   ├── auth/
│   │   │   ├── LoginPage.tsx
│   │   │   └── RegisterPage.tsx
│   │   ├── flight/
│   │   │   ├── SearchPage.tsx
│   │   │   └── SearchResultsPage.tsx
│   │   ├── booking/
│   │   │   ├── BookingPage.tsx
│   │   │   ├── BookingConfirmPage.tsx
│   │   │   └── MyBookingsPage.tsx
│   │   ├── payment/
│   │   │   └── PaymentResultPage.tsx
│   │   └── admin/
│   │       ├── DashboardPage.tsx
│   │       └── BookingsManagePage.tsx
│   ├── store/                   ← Zustand global state
│   │   └── authStore.ts
│   ├── types/                   ← TypeScript types/interfaces
│   │   ├── auth.types.ts
│   │   ├── flight.types.ts
│   │   ├── booking.types.ts
│   │   └── admin.types.ts
│   ├── lib/
│   │   └── axios.ts             ← Axios instance + interceptors
│   ├── App.tsx
│   ├── main.tsx
│   └── index.css
├── .env.local                   ← Biến môi trường
├── tailwind.config.js
├── tsconfig.json
├── vite.config.ts
└── package.json
```

---

## Module 1 — Cài đặt & Cấu hình

### 1.1 Tailwind CSS v4 — Không dùng `tailwind.config.js`

> **Tailwind v4 thay đổi hoàn toàn so với v3:** không có `tailwind.config.js`, không có `postcss.config.js`, không có `@tailwind` directives.

Cài đặt:

```bash
npm install -D tailwindcss@next @tailwindcss/vite
# Xoá nếu đã cài (không cần nữa):
# npm uninstall postcss autoprefixer tailwindcss
```

### 1.2 `src/index.css`

```css
@import "tailwindcss";

@theme {
  --color-primary-50: #eff6ff;
  --color-primary-100: #dbeafe;
  --color-primary-500: #3b82f6;
  --color-primary-600: #2563eb;
  --color-primary-700: #1d4ed8;
  --color-primary-800: #1e40af;
}

@layer base {
  body {
    background-color: #f9fafb;
    color: #111827;
  }
}
```

### 1.3 `vite.config.ts` — proxy để tránh CORS khi dev

```ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
```

> **Quan trọng:** Nhờ proxy này, FE gọi `/api/...` sẽ được forward sang BE `localhost:8080/api/...` — tránh lỗi CORS khi dev.

### 1.4 `.env.local`

```env
VITE_API_URL=http://localhost:8080
VITE_APP_NAME=FlightEasy
```

### 1.5 `src/lib/axios.ts` — Axios instance + Token Interceptor (Module 08)

```ts
import axios from "axios";
import toast from "react-hot-toast";

const api = axios.create({
  baseURL: "/api",        // Dùng proxy của Vite
  withCredentials: true,  // Gửi cookie (refresh_token) tự động
  headers: {
    "Content-Type": "application/json",
  },
});

// ─── REQUEST INTERCEPTOR — tự động gắn Access Token ───────────────────────
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("access_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ─── RESPONSE INTERCEPTOR — tự động refresh khi 401 ──────────────────────
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token!);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const originalRequest = error.config;

    // Token hết hạn hoặc bị blacklist → thử refresh
    if (error.response?.status === 401 && !originalRequest._retry) {
      // Nếu đang refresh rồi → queue lại
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Gọi /auth/refresh — cookie refresh_token gửi tự động nhờ withCredentials
        const { data } = await api.post("/v1/auth/refresh");
        const newToken = data.accessToken;
        localStorage.setItem("access_token", newToken);
        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        // Refresh cũng fail → đăng xuất
        processQueue(refreshError, null);
        localStorage.removeItem("access_token");
        localStorage.removeItem("user");
        window.location.href = "/login";
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // Lỗi khác → hiển thị toast
    const message =
      error.response?.data?.message ||
      error.response?.data?.error ||
      "Đã có lỗi xảy ra";

    if (error.response?.status !== 401) {
      toast.error(message);
    }

    return Promise.reject(error);
  }
);

export default api;
```

### 1.6 `src/types/` — TypeScript Interfaces

```ts
// auth.types.ts
export interface User {
  id: number;
  email: string;
  fullName: string;
  role: "ROLE_USER" | "ROLE_ADMIN";
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  user: User;
}

// flight.types.ts
export interface Airport {
  id: number;
  iataCode: string;
  name: string;
  city: string;
  country: string;
}

export interface FlightSearchResult {
  id: number;
  flightNumber: string;
  airlineCode: string;
  airlineName: string;
  airlineLogoUrl: string;
  originIata: string;
  originCity: string;
  destinationIata: string;
  destinationCity: string;
  departureTime: string;
  arrivalTime: string;
  durationMinutes: number;
  pricePerPerson: number;
  totalPrice: number;
  availableSeats: number;
  baggageAllowanceKg: number;
  isRefundable: boolean;
  status: string;
  tags: string[];
}

export interface FlightSearchResponse {
  meta: {
    from: string;
    to: string;
    departDate: string;
    adults: number;
    children: number;
    infants: number;
    classType: string;
  };
  flights: FlightSearchResult[];
  priceRange: { min: number; max: number };
  availableFilters: {
    airlines: string[];
    durationRange: { min: number; max: number };
  };
}

// booking.types.ts
export interface BookingResponse {
  pnrCode: string;
  status: string;
  expiresAt: string;
  flight: {
    flightNumber: string;
    from: string;
    to: string;
    departureTime: string;
  };
  passengers: Array<{
    name: string;
    seat: string;
    idNumber: string;
  }>;
  pricing: {
    subtotal: number;
    serviceFee: number;
    totalPrice: number;
    currency: string;
  };
  paymentDeadline: string;
}

export interface SeatInfo {
  seatNumber: string;
  position: string;
  isAvailable: boolean;
  extraFee: number;
  isExtraLegroom: boolean;
}

export interface SeatRow {
  rowNumber: number;
  seats: SeatInfo[];
}

export interface SeatMapResponse {
  firstClass: SeatRow[];
  business: SeatRow[];
  economy: SeatRow[];
}

// admin.types.ts
export interface DashboardKPIResponse {
  date: string;
  todayRevenue: number;
  yesterdayRevenue: number;
  revenueGrowthPercent: number;
  todayBookings: number;
  confirmedBookings: number;
  pendingBookings: number;
  cancelledBookings: number;
  conversionRate: number;
  totalFlights: number;
  delayedFlights: number;
  cancelledFlights: number;
  avgTicketPrice: number;
  updatedAt: string;
}
```

### 1.7 `src/store/authStore.ts` — Zustand global state

```ts
import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "@/types/auth.types";

// Cài thêm: npm install zustand

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  setUser: (user: User | null) => void;
  setToken: (token: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,
      setUser: (user) => set({ user, isAuthenticated: !!user }),
      setToken: (token) => {
        localStorage.setItem("access_token", token);
      },
      logout: () => {
        localStorage.removeItem("access_token");
        set({ user: null, isAuthenticated: false });
      },
    }),
    {
      name: "auth-storage",
      partialize: (state) => ({ user: state.user, isAuthenticated: state.isAuthenticated }),
    }
  )
);
```

---

## Module 2 — Auth Pages

### 2.1 `src/api/auth.api.ts`

```ts
import api from "@/lib/axios";
import type { AuthResponse } from "@/types/auth.types";

export const authApi = {
  register: (data: {
    fullName: string;
    email: string;
    password: string;
  }) => api.post<AuthResponse>("/v1/auth/register", data),

  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse>("/v1/auth/login", data),

  logout: () => api.post("/v1/auth/logout"),

  refresh: () => api.post<AuthResponse>("/v1/auth/refresh"),

  forgotPassword: (email: string) =>
    api.post("/v1/auth/forgot-password", { email }),

  resetPassword: (token: string, newPassword: string) =>
    api.post("/v1/auth/reset-password", { token, newPassword }),
};
```

### 2.2 `src/pages/auth/LoginPage.tsx`

```tsx
import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { Plane } from "lucide-react";
import { authApi } from "@/api/auth.api";
import { useAuthStore } from "@/store/authStore";

const loginSchema = z.object({
  email: z.string().email("Email không hợp lệ"),
  password: z.string().min(1, "Vui lòng nhập mật khẩu"),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const navigate = useNavigate();
  const { setUser, setToken } = useAuthStore();
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (data: LoginForm) => {
    setLoading(true);
    try {
      const res = await authApi.login(data);
      setToken(res.data.accessToken);
      setUser(res.data.user);
      toast.success("Đăng nhập thành công!");
      navigate(res.data.user.role === "ROLE_ADMIN" ? "/admin" : "/");
    } catch {
      // Lỗi đã được toast bởi axios interceptor
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-md p-8">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-2 text-blue-700">
            <Plane className="w-8 h-8" />
            <span className="text-2xl font-bold">FlightEasy</span>
          </div>
          <p className="mt-2 text-gray-500 text-sm">Đăng nhập vào tài khoản của bạn</p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <input
              {...register("email")}
              type="email"
              placeholder="you@example.com"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            {errors.email && (
              <p className="text-red-500 text-xs mt-1">{errors.email.message}</p>
            )}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu</label>
            <input
              {...register("password")}
              type="password"
              placeholder="••••••••"
              className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
            {errors.password && (
              <p className="text-red-500 text-xs mt-1">{errors.password.message}</p>
            )}
          </div>

          <div className="flex justify-end">
            <Link to="/forgot-password" className="text-sm text-blue-600 hover:underline">
              Quên mật khẩu?
            </Link>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-700 text-white py-2.5 rounded-lg font-medium hover:bg-blue-800 transition-colors disabled:opacity-60"
          >
            {loading ? "Đang đăng nhập..." : "Đăng nhập"}
          </button>
        </form>

        <p className="text-center text-sm text-gray-500 mt-6">
          Chưa có tài khoản?{" "}
          <Link to="/register" className="text-blue-600 hover:underline font-medium">
            Đăng ký ngay
          </Link>
        </p>
      </div>
    </div>
  );
}
```

### 2.3 `src/pages/auth/RegisterPage.tsx`

```tsx
import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import toast from "react-hot-toast";
import { Plane } from "lucide-react";
import { authApi } from "@/api/auth.api";
import { useAuthStore } from "@/store/authStore";

const registerSchema = z
  .object({
    fullName: z.string().min(2, "Tên phải có ít nhất 2 ký tự"),
    email: z.string().email("Email không hợp lệ"),
    password: z
      .string()
      .regex(
        /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/,
        "Mật khẩu phải có chữ hoa, chữ thường, số và ít nhất 8 ký tự"
      ),
    confirmPassword: z.string(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: "Mật khẩu không khớp",
    path: ["confirmPassword"],
  });

type RegisterForm = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const navigate = useNavigate();
  const { setUser, setToken } = useAuthStore();
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

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
      // Toast được xử lý bởi interceptor
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
  );
}
```

---

## Module 3 — Flight Search

### 3.1 `src/api/flight.api.ts`

```ts
import api from "@/lib/axios";
import type { FlightSearchResponse, SeatMapResponse } from "@/types/flight.types";

export interface SearchParams {
  from: string;
  to: string;
  departDate: string;
  adults?: number;
  children?: number;
  infants?: number;
  classType?: string;
  sortBy?: string;
  minPrice?: number;
  maxPrice?: number;
  airlines?: string;
  page?: number;
  size?: number;
}

export const flightApi = {
  searchFlights: (params: SearchParams) =>
    api.get<FlightSearchResponse>("/v1/flights/search", { params }),

  searchRoundTrip: (params: SearchParams & { returnDate: string }) =>
    api.get("/v1/flights/search/round-trip", { params }),

  getAirports: () => api.get("/v1/airports"),

  getFlightById: (id: number) => api.get(`/v1/flights/${id}`),

  getSeatMap: (flightId: number) =>
    api.get<SeatMapResponse>(`/v1/flights/${flightId}/seats`),
};
```

### 3.2 `src/pages/flight/SearchPage.tsx`

```tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { Plane, Calendar, Users, ArrowRightLeft } from "lucide-react";
import { format } from "date-fns";

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

  const { register, handleSubmit, setValue, watch } = useForm<SearchForm>({
    defaultValues: {
      adults: 1,
      children: 0,
      infants: 0,
      classType: "ECONOMY",
      tripType: "one-way",
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

  const today = format(new Date(), "yyyy-MM-dd");

  return (
    <div className="min-h-screen bg-gradient-to-b from-blue-700 to-blue-900">
      {/* Hero */}
      <div className="pt-16 pb-32 text-center text-white px-4">
        <div className="inline-flex items-center gap-2 mb-4">
          <Plane className="w-8 h-8" />
          <span className="text-3xl font-bold">FlightEasy</span>
        </div>
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
                <input
                  {...register("from", { required: true })}
                  placeholder="SGN - Hồ Chí Minh"
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
                  maxLength={3}
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
                <input
                  {...register("to", { required: true })}
                  placeholder="HAN - Hà Nội"
                  className="w-full px-4 py-3 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 uppercase"
                  maxLength={3}
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
```

### 3.3 `src/pages/flight/SearchResultsPage.tsx`

```tsx
import { useSearchParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { flightApi } from "@/api/flight.api";
import type { FlightSearchResult } from "@/types/flight.types";
import { format, parseISO } from "date-fns";
import { vi } from "date-fns/locale";
import { Clock, Luggage, ArrowRight } from "lucide-react";

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);

const formatDuration = (minutes: number) => {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${h}g ${m}p`;
};

function FlightCard({
  flight,
  onSelect,
}: {
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

  const { data, isLoading, isError } = useQuery({
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
```

---

## Module 4 — Booking & Seat Selection

### 4.1 `src/api/booking.api.ts`

```ts
import api from "@/lib/axios";
import type { BookingResponse, SeatMapResponse } from "@/types/booking.types";

export interface CreateBookingPayload {
  flightClassId: number;
  contactEmail: string;
  contactPhone: string;
  passengers: Array<{
    firstName: string;
    lastName: string;
    dateOfBirth: string;
    gender: string;
    nationality: string;
    idType: string;
    idNumber: string;
    passengerType: string;
    seatId?: number;
    mealPreference?: string;
  }>;
  selectedSeatIds: number[];
}

export const bookingApi = {
  createBooking: (payload: CreateBookingPayload) =>
    api.post<BookingResponse>("/v1/bookings", payload),

  getBooking: (pnr: string) => api.get<BookingResponse>(`/v1/bookings/${pnr}`),

  getMyBookings: () => api.get<BookingResponse[]>("/v1/bookings/my"),

  cancelBooking: (pnr: string) =>
    api.delete<{ pnrCode: string; refundAmount: number; cancelledAt: string }>(
      `/v1/bookings/${pnr}`
    ),

  getSeatMap: (flightId: number) =>
    api.get<SeatMapResponse>(`/v1/flights/${flightId}/seats`),
};
```

### 4.2 `src/pages/booking/BookingPage.tsx`

```tsx
import { useState } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { useForm, useFieldArray } from "react-hook-form";
import { useMutation } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { User, Phone, Mail } from "lucide-react";
import { bookingApi } from "@/api/booking.api";
import { useAuthStore } from "@/store/authStore";
import type { CreateBookingPayload } from "@/api/booking.api";

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
  const { user } = useAuthStore();
  const flightClassId = Number(searchParams.get("flightClassId"));
  const adults = Number(searchParams.get("adults") || 1);

  const { register, handleSubmit, control } = useForm<BookingForm>({
    defaultValues: {
      contactEmail: user?.email || "",
      passengers: Array.from({ length: adults }, () => ({
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

  const { fields } = useFieldArray({ control, name: "passengers" });

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
  );
}
```

### 4.3 `src/pages/booking/BookingConfirmPage.tsx`

```tsx
import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { bookingApi } from "@/api/booking.api";
import { format, parseISO } from "date-fns";
import { vi } from "date-fns/locale";
import { CheckCircle, Clock, Plane } from "lucide-react";

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);

export default function BookingConfirmPage() {
  const { pnr } = useParams<{ pnr: string }>();
  const navigate = useNavigate();

  const { data, isLoading } = useQuery({
    queryKey: ["booking", pnr],
    queryFn: () => bookingApi.getBooking(pnr!),
    enabled: !!pnr,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  const booking = data?.data;
  if (!booking) return null;

  const expiresAt = booking.expiresAt ? parseISO(booking.expiresAt) : null;

  return (
    <div className="max-w-2xl mx-auto px-4 py-12">
      {/* Success header */}
      <div className="text-center mb-8">
        <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
        <h1 className="text-2xl font-bold text-gray-900">Đặt vé thành công!</h1>
        <p className="text-gray-500 mt-1">Mã đặt chỗ của bạn:</p>
        <div className="mt-3 inline-block bg-blue-50 border border-blue-200 rounded-xl px-8 py-3">
          <span className="text-3xl font-bold tracking-widest text-blue-700">{booking.pnrCode}</span>
        </div>
      </div>

      {/* Countdown if pending */}
      {booking.status === "PENDING" && expiresAt && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6 flex items-center gap-3">
          <Clock className="w-5 h-5 text-amber-600 flex-shrink-0" />
          <div>
            <p className="text-sm font-medium text-amber-800">Vui lòng thanh toán trước:</p>
            <p className="text-sm text-amber-700">
              {format(expiresAt, "HH:mm - dd/MM/yyyy", { locale: vi })}
            </p>
          </div>
        </div>
      )}

      {/* Flight info */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
        <h2 className="font-semibold text-gray-800 mb-4 flex items-center gap-2">
          <Plane className="w-4 h-4" />
          Thông tin chuyến bay
        </h2>
        <div className="space-y-3 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-500">Chuyến bay</span>
            <span className="font-medium">{booking.flight.flightNumber}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Hành trình</span>
            <span className="font-medium">{booking.flight.from} → {booking.flight.to}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Khởi hành</span>
            <span className="font-medium">
              {format(parseISO(booking.flight.departureTime), "HH:mm - dd/MM/yyyy", { locale: vi })}
            </span>
          </div>
        </div>
      </div>

      {/* Passengers */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-4">
        <h2 className="font-semibold text-gray-800 mb-4">Hành khách</h2>
        <div className="space-y-2">
          {booking.passengers.map((p, i) => (
            <div key={i} className="flex items-center justify-between text-sm py-2 border-b border-gray-50 last:border-0">
              <span className="font-medium text-gray-800">{p.name}</span>
              <span className="text-blue-700 font-medium">Ghế {p.seat}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Pricing */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 mb-6">
        <h2 className="font-semibold text-gray-800 mb-4">Chi phí</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between">
            <span className="text-gray-500">Giá vé</span>
            <span>{formatCurrency(booking.pricing.subtotal)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-500">Phí dịch vụ</span>
            <span>{formatCurrency(booking.pricing.serviceFee)}</span>
          </div>
          <div className="flex justify-between font-bold text-base pt-2 border-t border-gray-100 mt-2">
            <span>Tổng cộng</span>
            <span className="text-blue-700">{formatCurrency(booking.pricing.totalPrice)}</span>
          </div>
        </div>
      </div>

      {/* Pay button */}
      {booking.status === "PENDING" && (
        <button
          onClick={() =>
            navigate(`/payment?pnr=${booking.pnrCode}&amount=${booking.pricing.totalPrice}`)
          }
          className="w-full bg-blue-700 text-white py-3.5 rounded-xl font-semibold text-lg hover:bg-blue-800 transition-colors"
        >
          Thanh toán ngay — {formatCurrency(booking.pricing.totalPrice)}
        </button>
      )}
    </div>
  );
}
```

### 4.4 `src/pages/booking/MyBookingsPage.tsx`

```tsx
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { bookingApi } from "@/api/booking.api";
import type { BookingResponse } from "@/types/booking.types";
import { format, parseISO } from "date-fns";
import { vi } from "date-fns/locale";
import { Plane, Clock, CheckCircle, XCircle, AlertTriangle } from "lucide-react";
import toast from "react-hot-toast";

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);

const StatusBadge = ({ status }: { status: string }) => {
  const map: Record<string, { label: string; cls: string; icon: React.ReactNode }> = {
    CONFIRMED: {
      label: "Đã xác nhận",
      cls: "bg-green-100 text-green-700",
      icon: <CheckCircle className="w-3 h-3" />,
    },
    PENDING: {
      label: "Chờ thanh toán",
      cls: "bg-amber-100 text-amber-700",
      icon: <Clock className="w-3 h-3" />,
    },
    CANCELLED: {
      label: "Đã hủy",
      cls: "bg-red-100 text-red-700",
      icon: <XCircle className="w-3 h-3" />,
    },
  };
  const s = map[status] ?? { label: status, cls: "bg-gray-100 text-gray-700", icon: null };
  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium ${s.cls}`}>
      {s.icon}
      {s.label}
    </span>
  );
};

export default function MyBookingsPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["my-bookings"],
    queryFn: () => bookingApi.getMyBookings(),
  });

  const cancelMutation = useMutation({
    mutationFn: (pnr: string) => bookingApi.cancelBooking(pnr),
    onSuccess: () => {
      toast.success("Đã hủy booking thành công");
      queryClient.invalidateQueries({ queryKey: ["my-bookings"] });
    },
  });

  const bookings: BookingResponse[] = data?.data ?? [];

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Lịch sử đặt vé</h1>

      {bookings.length === 0 ? (
        <div className="text-center py-16 text-gray-500">
          <Plane className="w-12 h-12 mx-auto mb-3 text-gray-300" />
          <p>Bạn chưa có booking nào.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {bookings.map((booking) => (
            <div
              key={booking.pnrCode}
              className="bg-white rounded-xl shadow-sm border border-gray-100 p-5"
            >
              <div className="flex items-start justify-between mb-3">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-bold text-lg tracking-widest text-blue-700">
                      {booking.pnrCode}
                    </span>
                    <StatusBadge status={booking.status} />
                  </div>
                  <p className="text-sm text-gray-500">
                    {booking.flight.from} → {booking.flight.to} •{" "}
                    {format(parseISO(booking.flight.departureTime), "HH:mm - dd/MM/yyyy", {
                      locale: vi,
                    })}
                  </p>
                </div>
                <p className="text-lg font-bold text-gray-900">
                  {formatCurrency(booking.pricing.totalPrice)}
                </p>
              </div>

              <div className="flex items-center gap-2 mt-4">
                <button
                  onClick={() => navigate(`/booking/confirm/${booking.pnrCode}`)}
                  className="text-sm text-blue-600 hover:underline font-medium"
                >
                  Xem chi tiết
                </button>

                {booking.status === "PENDING" && (
                  <>
                    <span className="text-gray-300">|</span>
                    <button
                      onClick={() =>
                        navigate(
                          `/payment?pnr=${booking.pnrCode}&amount=${booking.pricing.totalPrice}`
                        )
                      }
                      className="text-sm text-green-600 hover:underline font-medium"
                    >
                      Thanh toán ngay
                    </button>
                    <span className="text-gray-300">|</span>
                    <button
                      onClick={() => {
                        if (window.confirm(`Hủy booking ${booking.pnrCode}?`)) {
                          cancelMutation.mutate(booking.pnrCode);
                        }
                      }}
                      disabled={cancelMutation.isPending}
                      className="text-sm text-red-500 hover:underline font-medium disabled:opacity-50"
                    >
                      Hủy booking
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

---

## Module 5 — Payment VNPay

### 5.1 `src/api/payment.api.ts`

```ts
import api from "@/lib/axios";

export const paymentApi = {
  createPaymentLink: (pnrCode: string, returnUrl: string) =>
    api.post<{
      paymentUrl: string;
      txnRef: string;
      amount: number;
      expiresAt: string;
    }>("/v1/payments/vnpay/create", {
      pnrCode,
      returnUrl,
    }),

  getPaymentStatus: (pnr: string) =>
    api.get<{
      pnrCode: string;
      status: string;
      amount: number;
      bankCode: string;
      paidAt: string;
    }>(`/v1/payments/status/${pnr}`),
};
```

### 5.2 Trang chuyển hướng VNPay

Trong `BookingConfirmPage.tsx`, khi bấm "Thanh toán ngay":

```tsx
// Trong BookingConfirmPage — nút thanh toán:
const handlePay = async () => {
  try {
    const returnUrl = `${window.location.origin}/payment/result`;
    const res = await paymentApi.createPaymentLink(booking.pnrCode, returnUrl);
    // Redirect sang VNPay
    window.location.href = res.data.paymentUrl;
  } catch {
    // Toast đã được xử lý bởi interceptor
  }
};
```

### 5.3 `src/pages/payment/PaymentResultPage.tsx`

```tsx
import { useSearchParams, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { paymentApi } from "@/api/payment.api";
import { CheckCircle, XCircle, Loader } from "lucide-react";

export default function PaymentResultPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const responseCode = searchParams.get("vnp_ResponseCode");
  const txnRef = searchParams.get("vnp_TxnRef");
  // txnRef = "PNRCODE-timestamp"
  const pnrCode = txnRef?.split("-")[0];

  const isSuccess = responseCode === "00";

  // Poll trạng thái từ BE (IPN có thể đến trước return URL)
  const { data, isLoading } = useQuery({
    queryKey: ["payment-status", pnrCode],
    queryFn: () => paymentApi.getPaymentStatus(pnrCode!),
    enabled: !!pnrCode,
    refetchInterval: isSuccess ? 2000 : false, // Poll mỗi 2s nếu success
    refetchIntervalInBackground: false,
  });

  const status = data?.data.status;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
      <div className="bg-white rounded-2xl shadow-xl p-10 text-center max-w-md w-full">
        {isLoading ? (
          <Loader className="w-16 h-16 text-blue-500 mx-auto animate-spin" />
        ) : isSuccess && status === "SUCCESS" ? (
          <>
            <CheckCircle className="w-20 h-20 text-green-500 mx-auto mb-4" />
            <h1 className="text-2xl font-bold text-gray-900">Thanh toán thành công!</h1>
            <p className="text-gray-500 mt-2 mb-6">
              Vé của bạn đã được xác nhận. Kiểm tra email để xem chi tiết.
            </p>
            <div className="bg-blue-50 rounded-xl p-4 mb-6">
              <p className="text-sm text-gray-500">Mã đặt chỗ</p>
              <p className="text-2xl font-bold tracking-widest text-blue-700">{pnrCode}</p>
            </div>
            <button
              onClick={() => navigate(`/booking/confirm/${pnrCode}`)}
              className="w-full bg-blue-700 text-white py-3 rounded-xl font-medium hover:bg-blue-800 transition-colors"
            >
              Xem chi tiết vé
            </button>
          </>
        ) : (
          <>
            <XCircle className="w-20 h-20 text-red-500 mx-auto mb-4" />
            <h1 className="text-2xl font-bold text-gray-900">Thanh toán thất bại</h1>
            <p className="text-gray-500 mt-2 mb-6">
              Giao dịch không thành công. Bạn có thể thử lại hoặc chọn phương thức khác.
            </p>
            <button
              onClick={() => navigate(`/booking/confirm/${pnrCode}`)}
              className="w-full bg-blue-700 text-white py-3 rounded-xl font-medium hover:bg-blue-800 transition-colors"
            >
              Thử lại
            </button>
          </>
        )}
      </div>
    </div>
  );
}
```

---

## Module 7 — Admin Dashboard

### 7.1 `src/api/admin.api.ts`

```ts
import api from "@/lib/axios";
import type { DashboardKPIResponse } from "@/types/admin.types";

export const adminApi = {
  getDashboardKPIs: () => api.get<DashboardKPIResponse>("/v1/admin/dashboard/kpis"),

  getRevenueChart: (period: "WEEKLY" | "MONTHLY" | "YEARLY") =>
    api.get("/v1/admin/dashboard/revenue-chart", { params: { period } }),

  getTopRoutes: (limit = 10) =>
    api.get("/v1/admin/dashboard/top-routes", { params: { limit } }),

  getAllBookings: (params: { status?: string; page?: number; size?: number }) =>
    api.get("/v1/admin/bookings", { params }),

  cancelBooking: (pnr: string, reason?: string) =>
    api.patch(`/v1/admin/bookings/${pnr}/cancel`, null, { params: { reason } }),

  exportReport: (fromDate: string, toDate: string) =>
    api.post(
      "/v1/admin/reports/export",
      { fromDate, toDate, type: "REVENUE" },
      { responseType: "blob" }
    ),
};
```

### 7.2 `src/pages/admin/DashboardPage.tsx`

```tsx
import { useQuery } from "@tanstack/react-query";
import { adminApi } from "@/api/admin.api";
import type { DashboardKPIResponse } from "@/types/admin.types";
import {
  TrendingUp, TrendingDown, Plane, Calendar,
  DollarSign, Users, AlertTriangle, CheckCircle,
} from "lucide-react";

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(amount);

function KPICard({
  title,
  value,
  sub,
  icon: Icon,
  trend,
  color = "blue",
}: {
  title: string;
  value: string;
  sub?: string;
  icon: React.ElementType;
  trend?: number;
  color?: "blue" | "green" | "amber" | "red";
}) {
  const colorMap = {
    blue: "bg-blue-50 text-blue-700",
    green: "bg-green-50 text-green-700",
    amber: "bg-amber-50 text-amber-700",
    red: "bg-red-50 text-red-700",
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-gray-500">{title}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">{value}</p>
          {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
          {trend !== undefined && (
            <div className={`flex items-center gap-1 mt-2 text-xs font-medium ${trend >= 0 ? "text-green-600" : "text-red-600"}`}>
              {trend >= 0
                ? <TrendingUp className="w-3 h-3" />
                : <TrendingDown className="w-3 h-3" />
              }
              {Math.abs(trend).toFixed(1)}% so với hôm qua
            </div>
          )}
        </div>
        <div className={`p-3 rounded-xl ${colorMap[color]}`}>
          <Icon className="w-5 h-5" />
        </div>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["admin-kpis"],
    queryFn: () => adminApi.getDashboardKPIs(),
    refetchInterval: 5 * 60 * 1000, // Refresh mỗi 5 phút
  });

  const kpis: DashboardKPIResponse | undefined = data?.data;

  const handleExport = async () => {
    const today = new Date().toISOString().split("T")[0];
    const monthAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split("T")[0];
    const res = await adminApi.exportReport(monthAgo, today);
    const url = URL.createObjectURL(res.data);
    const a = document.createElement("a");
    a.href = url;
    a.download = `bao-cao-${today}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin w-8 h-8 border-4 border-blue-600 border-t-transparent rounded-full" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500 text-sm mt-1">
            {kpis?.date
              ? new Date(kpis.date).toLocaleDateString("vi-VN", {
                  weekday: "long",
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                })
              : "Hôm nay"}
          </p>
        </div>
        <button
          onClick={handleExport}
          className="bg-blue-700 text-white px-4 py-2 rounded-lg text-sm font-medium hover:bg-blue-800 transition-colors flex items-center gap-2"
        >
          Xuất báo cáo
        </button>
      </div>

      {/* KPI Grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <KPICard
          title="Doanh thu hôm nay"
          value={formatCurrency(kpis?.todayRevenue || 0)}
          sub={`Hôm qua: ${formatCurrency(kpis?.yesterdayRevenue || 0)}`}
          icon={DollarSign}
          trend={kpis?.revenueGrowthPercent}
          color="green"
        />
        <KPICard
          title="Booking hôm nay"
          value={String(kpis?.todayBookings || 0)}
          sub={`Tỷ lệ xác nhận: ${kpis?.conversionRate?.toFixed(1)}%`}
          icon={Calendar}
          color="blue"
        />
        <KPICard
          title="Chuyến bay hôm nay"
          value={String(kpis?.totalFlights || 0)}
          sub={`Trễ: ${kpis?.delayedFlights || 0} | Huỷ: ${kpis?.cancelledFlights || 0}`}
          icon={Plane}
          color="amber"
        />
        <KPICard
          title="Giá vé trung bình"
          value={formatCurrency(kpis?.avgTicketPrice || 0)}
          icon={Users}
          color="blue"
        />
      </div>

      {/* Booking Status */}
      <div className="grid grid-cols-3 gap-4">
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
          <div className="flex items-center gap-3">
            <CheckCircle className="w-8 h-8 text-green-500" />
            <div>
              <p className="text-xl font-bold text-gray-900">{kpis?.confirmedBookings || 0}</p>
              <p className="text-sm text-gray-500">Đã xác nhận</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-8 h-8 text-amber-500" />
            <div>
              <p className="text-xl font-bold text-gray-900">{kpis?.pendingBookings || 0}</p>
              <p className="text-sm text-gray-500">Chờ thanh toán</p>
            </div>
          </div>
        </div>
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5">
          <div className="flex items-center gap-3">
            <AlertTriangle className="w-8 h-8 text-red-500" />
            <div>
              <p className="text-xl font-bold text-gray-900">{kpis?.cancelledBookings || 0}</p>
              <p className="text-sm text-gray-500">Đã hủy</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
```

---

### 7.3 `src/pages/admin/BookingsManagePage.tsx`

```tsx
import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "@/api/admin.api";
import { format, parseISO } from "date-fns";
import { vi } from "date-fns/locale";
import { Search, XCircle } from "lucide-react";
import toast from "react-hot-toast";

const formatCurrency = (amount: number) =>
  new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND" }).format(amount);

const STATUS_COLORS: Record<string, string> = {
  CONFIRMED: "bg-green-100 text-green-700",
  PENDING: "bg-amber-100 text-amber-700",
  CANCELLED: "bg-red-100 text-red-700",
  EXPIRED: "bg-gray-100 text-gray-500",
};

export default function BookingsManagePage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState("");
  const [page, setPage] = useState(0);
  const [cancelTarget, setCancelTarget] = useState<string | null>(null);
  const [cancelReason, setCancelReason] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: ["admin-bookings", statusFilter, page],
    queryFn: () =>
      adminApi.getAllBookings({ status: statusFilter || undefined, page, size: 20 }),
  });

  const cancelMutation = useMutation({
    mutationFn: ({ pnr, reason }: { pnr: string; reason: string }) =>
      adminApi.cancelBooking(pnr, reason),
    onSuccess: () => {
      toast.success("Đã hủy booking");
      setCancelTarget(null);
      setCancelReason("");
      queryClient.invalidateQueries({ queryKey: ["admin-bookings"] });
    },
  });

  const bookings = data?.data?.content ?? [];
  const totalPages = data?.data?.totalPages ?? 0;

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Quản lý Booking</h1>

      {/* Filters */}
      <div className="flex items-center gap-3 mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            className="pl-9 pr-4 py-2 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Chờ thanh toán</option>
            <option value="CONFIRMED">Đã xác nhận</option>
            <option value="CANCELLED">Đã hủy</option>
            <option value="EXPIRED">Hết hạn</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 border-b border-gray-100">
            <tr>
              {["PNR", "Hành khách", "Hành trình", "Ngày bay", "Tổng tiền", "Trạng thái", "Thao tác"].map((h) => (
                <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wide">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {isLoading ? (
              <tr>
                <td colSpan={7} className="text-center py-12 text-gray-400">Đang tải...</td>
              </tr>
            ) : bookings.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-12 text-gray-400">Không có booking nào</td>
              </tr>
            ) : (
              bookings.map((b: any) => (
                <tr key={b.pnrCode} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-bold tracking-widest text-blue-700">{b.pnrCode}</td>
                  <td className="px-4 py-3 text-gray-800">
                    {b.passengers?.[0]?.name ?? "—"}
                    {b.passengers?.length > 1 && (
                      <span className="text-gray-400 text-xs ml-1">+{b.passengers.length - 1}</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {b.flight?.from} → {b.flight?.to}
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {b.flight?.departureTime
                      ? format(parseISO(b.flight.departureTime), "dd/MM/yyyy HH:mm", { locale: vi })
                      : "—"}
                  </td>
                  <td className="px-4 py-3 font-medium text-gray-900">
                    {formatCurrency(b.pricing?.totalPrice ?? 0)}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[b.status] ?? "bg-gray-100 text-gray-600"}`}>
                      {b.status}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {(b.status === "PENDING" || b.status === "CONFIRMED") && (
                      <button
                        onClick={() => setCancelTarget(b.pnrCode)}
                        className="flex items-center gap-1 text-red-500 hover:text-red-700 text-xs font-medium"
                      >
                        <XCircle className="w-3 h-3" />
                        Hủy
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
          >
            ← Trước
          </button>
          <span className="px-3 py-1.5 text-sm text-gray-600">
            Trang {page + 1} / {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            className="px-3 py-1.5 text-sm border border-gray-200 rounded-lg disabled:opacity-40 hover:bg-gray-50"
          >
            Sau →
          </button>
        </div>
      )}

      {/* Cancel Dialog */}
      {cancelTarget && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Hủy booking {cancelTarget}</h2>
            <p className="text-sm text-gray-500 mb-4">
              Hành động này không thể hoàn tác. Nhập lý do hủy:
            </p>
            <textarea
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              placeholder="Ví dụ: Khách yêu cầu hủy, trùng lịch..."
              rows={3}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-red-400 resize-none"
            />
            <div className="flex gap-3 mt-4">
              <button
                onClick={() => { setCancelTarget(null); setCancelReason(""); }}
                className="flex-1 px-4 py-2.5 border border-gray-200 rounded-lg text-sm font-medium hover:bg-gray-50"
              >
                Không hủy
              </button>
              <button
                disabled={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate({ pnr: cancelTarget, reason: cancelReason })}
                className="flex-1 px-4 py-2.5 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-60"
              >
                {cancelMutation.isPending ? "Đang hủy..." : "Xác nhận hủy"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
```

---

## Module 8 — App.tsx & Route Setup

### 8.1 `src/App.tsx`

```tsx
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useAuthStore } from "@/store/authStore";

// Pages
import LoginPage from "@/pages/auth/LoginPage";
import RegisterPage from "@/pages/auth/RegisterPage";
import SearchPage from "@/pages/flight/SearchPage";
import SearchResultsPage from "@/pages/flight/SearchResultsPage";
import BookingPage from "@/pages/booking/BookingPage";
import BookingConfirmPage from "@/pages/booking/BookingConfirmPage";
import MyBookingsPage from "@/pages/booking/MyBookingsPage";
import PaymentResultPage from "@/pages/payment/PaymentResultPage";
import DashboardPage from "@/pages/admin/DashboardPage";
import BookingsManagePage from "@/pages/admin/BookingsManagePage";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

// Route bảo vệ — yêu cầu đăng nhập
function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

// Route chỉ dành cho Admin
function AdminRoute({ children }: { children: React.ReactNode }) {
  const { user, isAuthenticated } = useAuthStore();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user?.role !== "ROLE_ADMIN") return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Toaster position="top-right" />
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/" element={<SearchPage />} />
          <Route path="/search/results" element={<SearchResultsPage />} />

          {/* Payment result — cần public vì VNPay redirect về */}
          <Route path="/payment/result" element={<PaymentResultPage />} />

          {/* Private — cần đăng nhập */}
          <Route
            path="/booking"
            element={
              <PrivateRoute>
                <BookingPage />
              </PrivateRoute>
            }
          />
          <Route
            path="/booking/confirm/:pnr"
            element={
              <PrivateRoute>
                <BookingConfirmPage />
              </PrivateRoute>
            }
          />
          <Route
            path="/bookings"
            element={
              <PrivateRoute>
                <MyBookingsPage />
              </PrivateRoute>
            }
          />

          {/* Admin only */}
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <DashboardPage />
              </AdminRoute>
            }
          />
          <Route
            path="/admin/bookings"
            element={
              <AdminRoute>
                <BookingsManagePage />
              </AdminRoute>
            }
          />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
```

### 8.2 `src/main.tsx`

```tsx
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

---

## Chạy song song BE + FE

### Chạy BE (Spring Boot)

```bash
cd flighteasy-backend
./mvnw spring-boot:run
# BE chạy tại: http://localhost:8080
```

### Chạy FE (Vite)

```bash
cd flighteasy-frontend
npm run dev
# FE chạy tại: http://localhost:3000
```

Vite proxy tự forward `/api/*` → `http://localhost:8080/api/*` — không cần cấu hình CORS trong BE.

### Cấu hình CORS trong BE (nếu không dùng proxy)

Nếu deploy production hoặc test không qua proxy, thêm vào `SecurityConfig.java`:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true); // Bắt buộc để gửi cookie
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

Và trong `filterChain`:

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

---

## Cài đặt packages còn thiếu (tóm tắt)

```bash
cd flighteasy-frontend

# Zustand — global state
npm install zustand

# Tất cả packages cần thiết (nếu chưa cài đủ từ đầu)
npm install \
  axios \
  @tanstack/react-query \
  react-router-dom \
  react-hook-form \
  @hookform/resolvers \
  zod \
  date-fns \
  lucide-react \
  react-hot-toast \
  zustand \
  clsx \
  tailwind-merge

# Dev dependencies — Tailwind v4 (không dùng postcss/autoprefixer)
npm install -D \
  tailwindcss@next \
  @tailwindcss/vite \
  @types/node
```

---

## Checklist trước khi chạy

- [ ] BE đang chạy tại `localhost:8080`
- [ ] PostgreSQL & Redis đang chạy
- [ ] `.env.local` đã điền đúng
- [ ] `vite.config.ts` đã cấu hình proxy
- [ ] `@tailwindcss/vite` đã được thêm vào `vite.config.ts` và `@import "tailwindcss"` có trong `index.css`
- [ ] `@` alias hoạt động trong `tsconfig.json`:

```json
// tsconfig.json — thêm vào compilerOptions
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}
```

- [ ] Trang `/payment/result` khớp với `returnUrl` truyền vào VNPay
- [ ] Trang `/payment/result` nằm trong whitelist Security BE (không cần auth)

---

## Lưu ý quan trọng

**Về luồng VNPay:**
- FE tạo link → redirect browser sang VNPay → VNPay redirect về `/payment/result`
- DB chỉ được cập nhật qua IPN (server-to-server), không phải return URL
- Trang `/payment/result` poll `GET /payments/status/{pnr}` để biết kết quả thực sự

**Về Auth Token:**
- Access Token lưu trong `localStorage`
- Refresh Token lưu trong HttpOnly cookie (BE set, FE không đọc được, tự gửi nhờ `withCredentials: true`)
- Axios interceptor tự động refresh khi nhận 401, tự động blacklist khi logout

**Về Admin:**
- Route `/admin` chỉ render khi `user.role === "ROLE_ADMIN"`
- Admin token cũng là JWT bình thường, chỉ khác `role` claim
