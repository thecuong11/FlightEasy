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