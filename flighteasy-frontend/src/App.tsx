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