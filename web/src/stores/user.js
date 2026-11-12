import { defineStore } from "pinia";
import { authApi } from "@/api/auth";
import { profileApi } from "@/api/profile";
import { clearToken, getToken, setToken } from "@/utils/request";
import { buildSign } from "@/utils/crypto";
const useUserStore = defineStore("user", {
  state: () => ({
    token: getToken(),
    me: null,
    /** 个人资料（/v1/profile/me）：guideDone / pet / coin 等只在这里有，/auth/me 不带 */
    profile: null,
    profileLoaded: false,
    loading: false
  }),
  getters: {
    isLogin: (s) => !!s.token,
    roles: (s) => s.me?.roles ?? [],
    isMonitor: (s) => s.me?.isMonitor ?? false,
    isParent: (s) => s.me?.isParent ?? false,
    hasPerm: (s) => (perm) => s.me?.permissions?.includes(perm) ?? false,
    /** 是否走完新手引导（后端返回 0/1） */
    guideDone: (s) => (s.profileLoaded ? !!s.profile?.guideDone : null)
  },
  actions: {
    /** 统一落 token + 拉用户上下文，三种登录方式共用 */
    async applyLogin(vo) {
      this.token = vo.accessToken;
      setToken(vo.accessToken);
      await this.fetchMe();
      return vo;
    },
    async login(account, password) {
      this.loading = true;
      try {
        const vo = await authApi.loginByPassword(account, password);
        return await this.applyLogin(vo);
      } finally {
        this.loading = false;
      }
    },
    /** 登录页默认路径：密码用 RSA 加密后再提交，网络里没有明文口令 */
    async loginSecure(account, password, captcha) {
      this.loading = true;
      try {
        const payload = await buildSign(password);
        const vo = await authApi.loginBySign(account, payload, captcha);
        return await this.applyLogin(vo);
      } finally {
        this.loading = false;
      }
    },
    /** 手机 / 邮箱验证码登录 */
    async loginWithCode(target, type, code) {
      this.loading = true;
      try {
        const vo = await authApi.loginByCode(target, type, code);
        return await this.applyLogin(vo);
      } finally {
        this.loading = false;
      }
    },
    async fetchMe() {
      this.me = await authApi.me();
      return this.me;
    },
    /**
     * 拉个人资料。引导判断、光粒/礼盒数都靠它，所以带一个"只拉一次"的缓存：
     * 路由守卫每次导航都会问，但真正请求只发一次。
     */
    async fetchProfile(force = false) {
      if (this.profileLoaded && !force) return this.profile;
      this.profile = await profileApi.me();
      this.profileLoaded = true;
      return this.profile;
    },
    logout() {
      this.token = "";
      this.me = null;
      this.profile = null;
      this.profileLoaded = false;
      clearToken();
    }
  }
});
export {
  useUserStore
};
