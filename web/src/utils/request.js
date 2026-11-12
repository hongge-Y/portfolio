import axios from "axios";
import { toast } from "./toast";
const TOKEN_KEY = "portfolio_token";
const getToken = () => localStorage.getItem(TOKEN_KEY) || "";
const setToken = (t) => localStorage.setItem(TOKEN_KEY, t);
const clearToken = () => localStorage.removeItem(TOKEN_KEY);
const http = axios.create({ baseURL: "/api", timeout: 1e4 });
http.interceptors.request.use((cfg) => {
  const t = getToken();
  if (t) cfg.headers.Authorization = `Bearer ${t}`;
  return cfg;
});
http.interceptors.response.use(
  (res) => {
    const body = res.data;
    if (body && typeof body.code === "number" && body.code !== 200) {
      if (body.code === 401) {
        clearToken();
        if (!location.hash.startsWith("#/login")) location.hash = "#/login";
      }
      if (!res.config.silentToast) toast(body.message || "请求失败", "error");
      return Promise.reject(new Error(body.message));
    }
    return body.data;
  },
  (err) => {
    if (!err?.config?.silentToast) {
      toast(err?.message || "网络异常，请检查后端是否启动", "error");
    }
    return Promise.reject(err);
  }
);
function request(config) {
  return http.request(config);
}
var request_default = http;
export {
  clearToken,
  request_default as default,
  getToken,
  request,
  setToken
};
