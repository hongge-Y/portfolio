import { request } from "@/utils/request";
const authApi = {
  /** 密码登录（明文，脚本/冒烟用） */
  loginByPassword: (account, password) => request({
    url: "/v1/auth/login/password",
    method: "post",
    data: { account, password },
    silentToast: true
  }),
  /** 密码登录（RSA 密文，登录页用） */
  loginBySign: (account, payload, captcha) => request({
    url: "/v1/auth/login/password",
    method: "post",
    data: { account, ...payload, captchaId: captcha?.id, captchaCode: captcha?.code },
    silentToast: true
  }),
  /** 手机/邮箱验证码登录 */
  loginByCode: (target, type, code) => request({
    url: "/v1/auth/login/code",
    method: "post",
    data: { target, type, code },
    silentToast: true
  }),
  captcha: () => request({ url: "/v1/auth/captcha", method: "get", silentToast: true }),
  captchaState: (account) => request({ url: "/v1/auth/captcha-state", method: "get", params: { account }, silentToast: true }),
  publicKey: () => request({ url: "/v1/auth/public-key", method: "get", silentToast: true }),
  sendCode: (data) => request({ url: "/v1/auth/code/send", method: "post", data, silentToast: true }),
  me: () => request({ url: "/v1/auth/me", method: "get" }),
  /** 学生自助注册 */
  register: (data) => request({ url: "/v1/auth/register", method: "post", data, silentToast: true }),
  /** 注册页班级选项（公开接口，只返回班级名） */
  classes: () => request({ url: "/v1/public/classes", method: "get", silentToast: true }),
  health: () => request({ url: "/health", method: "get" })
};
export {
  authApi
};
