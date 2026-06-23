import {create} from "zustand";
import {persist} from "zustand/middleware";
import type {User} from "@/types/auth.types";

interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    setUser: (user: User | null) => void;
    setToken: (token: string) => void;
    logout: () => void;
}

export const useAuthstore = create<AuthState>() (
    persist(
        (set) => ({
            user: null,
            isAuthenticated: false,
            setUser: (user) => set({user, isAuthenticated: !!user}),
            setToken: (token) => {
                localStorage.setItem("access_token", token);
            },
            logout: () => {
                localStorage.removeItem("access_token");
                set({user: null, isAuthenticated: false});
            },
        }),
        {
            name: "auth-storage",
            partialize: (state) => ({user: state.user, isAuthenticated: state.isAuthenticated}),
        }
    )
);