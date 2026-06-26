import axios from "axios";
import toast from "react-hot-toast";

const api = axios.create({
    baseURL: "api/",
    withCredentials: true,
    headers: {
        "Content-Type": "application/json",
    },
});

api.interceptors.request.use((config) => {
    const token = localStorage.getItem("access_token");
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

let isRefreshing = false;
let failedQueue: Array<{
    resolve: (token: string) => void;
    reject: (err: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null) => {
    failedQueue.forEach(({resolve, reject}) => {
        if (error) reject(error);
        else resolve(token!);
    });
    failedQueue = [];
};

api.interceptors.response.use(
    (res) => res,
    async (error) => {
        const originalRequest = error.config;
        const isLoginEndpoint = originalRequest.url?.includes("/v1/auth/login");

        if (error.response?.status === 401 && !originalRequest._retry && !isLoginEndpoint) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({resolve, reject});
                }).then((token) => {
                    originalRequest.headers.Authorization = `Bearer ${token}`;
                    return api(originalRequest);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const {data} = await api.post("/v1/auth/refresh");
                const newToken = data.accessToken;
                localStorage.setItem("access_token", newToken);
                processQueue(null,  newToken);
                originalRequest.headers.Authorization = `Bearer ${newToken}`;
                return api(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                localStorage.removeItem("access_token");
                localStorage.removeItem("user");
                window.location.href = "/login";
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        const message = error.response?.data?.message ||
            error.response?.data?.error ||
            "Đã có lỗi xảy ra";

        if (error.response?.status !== 401) {
            toast.error(message);
        }
        return Promise.reject(error);
    }
);

export default api;