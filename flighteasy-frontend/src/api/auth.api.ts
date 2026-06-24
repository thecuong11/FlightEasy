import api from "@/lib/axios";
import  type {AuthResponse} from "@/types/auth.types.ts";

export const  authApi = {
    register: (data: {
        fullName: string;
        email: string;
        password: string;
    })=> api.post<AuthResponse>("/v1/auth/register", data),

    login: (data: {email: string; password: string}) =>
        api.post<AuthResponse>("/v1/auth/login", data),

    logout: () => api.post("/v1/auth/logout"),

    refresh: () => api.post<AuthResponse>("/v1/auth/refresh"),

    forgotPassword: (email: string) =>
        api.post("v1/auth/forgot-password", {email}),

    resetPassword: (token: string, newPassword: string)=>
        api.post("v1/auth/reset-password", {token, newPassword}),
};