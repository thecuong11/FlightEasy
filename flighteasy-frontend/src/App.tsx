import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { useAuthStore } from "@/store/authStore";

import LoginPage from "@/pages/auth/LoginPage.tsx";
import RegisterPage from "@/pages/auth/RegisterPage.tsx";
import SearchPage from "@/pages/flight/SearchPage.tsx";
import SearchResultsPage from "@/pages/flight/SearchResultsPage.tsx";
import BookingPage from "@/pages/booking/BookingPage.tsx";
import BookingConfirmPage from "@/pages/booking/BookingConfirmPage.tsx";
import MyBookingsPage from "@/pages/booking/MyBookingsPage.tsx";
import PaymentResultPage from "@/pages/payment/PaymentResultPage.tsx";
import DashboardPage from "@/pages/admin/DashboardPage.tsx";
import BookingsManagePage from "@/pages/admin/BookingsManagePage.tsx";
import Navbar from "@/components/layout/Navbar.tsx";
import AdminLayout from "@/layouts/AdminLayout.tsx";
import AirportsPage from "@/pages/admin/AirportsPage.tsx";
import AirlinesPage from "@/pages/admin/AirlinesPage.tsx";
import FlightsPage from "@/pages/admin/FlightsPage.tsx";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000
    },
  },
});

function PrivateRoute({children}: {children: React.ReactNode}) {
  const {isAuthenticated} = useAuthStore();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace/>;
}

function AdminRoute({children}: {children: React.ReactNode}) {
  const {user, isAuthenticated} = useAuthStore();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (user?.role !== "ROLE_ADMIN") return <Navigate to="/" replace />;
  return <>{children}</>;
}

function MainLayout({ children }: { children: React.ReactNode }) {
    return (
        <>
            <Navbar />
            {children}
        </>
    );
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
                  <Route path="/" element={<MainLayout><SearchPage /></MainLayout>} />
                  <Route path="/search/results" element={<MainLayout><SearchResultsPage /></MainLayout>} />

                  {/* Payment result — cần public vì VNPay redirect về */}
                  <Route path="/payment/result" element={<MainLayout><PaymentResultPage /></MainLayout>} />

                  {/* Private — cần đăng nhập */}
                  <Route
                      path="/booking"
                      element={
                          <PrivateRoute><MainLayout><BookingPage /></MainLayout></PrivateRoute>
                      }
                  />
                  <Route
                      path="/booking/confirm/:pnr"
                      element={
                          <PrivateRoute><MainLayout><BookingConfirmPage /></MainLayout></PrivateRoute>
                      }
                  />
                  <Route
                      path="/bookings"
                      element={
                          <PrivateRoute><MainLayout><MyBookingsPage /></MainLayout></PrivateRoute>
                      }
                  />

                  {/* Admin only */}
                  <Route
                      path="/admin"
                      element={
                          <AdminRoute><AdminLayout /></AdminRoute>
                      }
                  />
                  <Route index element={<DashboardPage />} />
                  <Route path="bookings" element={<BookingsManagePage />} />
                  <Route path="airports" element={<AirportsPage />} />
                  <Route path="airlines" element={<AirlinesPage />} />
                  <Route path="flights" element={<FlightsPage />} />
              </Routes>
          </BrowserRouter>
      </QueryClientProvider>
  );
}