<script setup>

import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { authApi } from "@/api/auth";
import { useUserStore } from "@/stores/user";
import { humanMessage } from "@/utils/crypto";
import { toast } from "@/utils/toast";
import { fieldIcon, APP_LOGO, oauthIcon } from "@/utils/icons";
const route = useRoute();
const router = useRouter();
const store = useUserStore();
const TABS = [
  { key: "account", label: "账号登录", icon: fieldIcon("user") },
  { key: "phone", label: "手机登录", icon: fieldIcon("phone") },
  { key: "mail", label: "邮箱登录", icon: fieldIcon("mail") }
];
const THIRD_PARTY = [
  { key: "wechat", label: "微信" },
  { key: "weibo", label: "微博" },
  { key: "alipay", label: "支付宝" }
];
const FOOTER_LINKS = ["帮助中心", "隐私政策", "用户协议"];
const PARTICLES = [
  { id: 1, style: "left: 6%; animation-duration: 15s; animation-delay: 0s; opacity: .45" },
  { id: 2, style: "left: 14%; animation-duration: 21s; animation-delay: -3s; opacity: .3" },
  { id: 3, style: "left: 23%; animation-duration: 17s; animation-delay: -8s; opacity: .5" },
  { id: 4, style: "left: 31%; animation-duration: 24s; animation-delay: -12s; opacity: .25" },
  { id: 5, style: "left: 39%; animation-duration: 19s; animation-delay: -5s; opacity: .4" },
  { id: 6, style: "left: 47%; animation-duration: 26s; animation-delay: -16s; opacity: .3" },
  { id: 7, style: "left: 55%; animation-duration: 16s; animation-delay: -2s; opacity: .5" },
  { id: 8, style: "left: 63%; animation-duration: 22s; animation-delay: -10s; opacity: .35" },
  { id: 9, style: "left: 71%; animation-duration: 18s; animation-delay: -14s; opacity: .45" },
  { id: 10, style: "left: 79%; animation-duration: 25s; animation-delay: -6s; opacity: .28" },
  { id: 11, style: "left: 86%; animation-duration: 20s; animation-delay: -18s; opacity: .4" },
  { id: 12, style: "left: 93%; animation-duration: 23s; animation-delay: -9s; opacity: .32" },
  { id: 13, style: "left: 18%; animation-duration: 28s; animation-delay: -21s; opacity: .22" },
  { id: 14, style: "left: 67%; animation-duration: 27s; animation-delay: -24s; opacity: .24" }
];
const tab = ref("account");
const redirect = ref("");
const loading = ref(false);
const username = ref("");
const password = ref("");
const showPassword = ref(false);
const needCaptcha = ref(false);
const identifier = ref("");
const code = ref("");
const sending = ref(false);
const remaining = ref(0);
let timer;
const captcha = ref("");
const captchaImage = ref("");
const captchaId = ref("");
const CAPTCHA_LENGTH = 4;
const CODE_LENGTH = 6;
const resultMessage = ref("");
const focusKey = ref("");
const errors = reactive({
  username: "",
  password: "",
  identifier: "",
  captcha: "",
  code: ""
});
const isCodeTab = computed(() => tab.value !== "account");
const codeType = computed(() => tab.value === "mail" ? "MAIL" : "MOBILE");
const identifierIcon = computed(() => fieldIcon(tab.value === "mail" ? "mail" : "phone"));
const identifierPlaceholder = computed(
  () => tab.value === "mail" ? "请输入邮箱地址" : "请输入手机号"
);
const sendText = computed(() => {
  if (remaining.value > 0) return `${remaining.value}s 后重发`;
  return sending.value ? "发送中…" : "获取验证码";
});
const canSend = computed(() => remaining.value === 0 && !sending.value);
onMounted(() => {
  redirect.value = typeof route.query.redirect === "string" ? route.query.redirect : "";
  // 注册完成跳回来时带上学号，省得再敲一遍
  if (typeof route.query.account === "string" && route.query.account) {
    username.value = route.query.account;
  }
  refreshCaptcha();
});
async function refreshCaptcha() {
  captcha.value = "";
  try {
    const res = await authApi.captcha();
    captchaId.value = res.captchaId;
    captchaImage.value = res.svg.startsWith("data:") ? res.svg : `data:image/svg+xml;base64,${res.svg}`;
  } catch {
    captchaImage.value = "";
    captchaId.value = "";
  }
}
function onFocus(key) {
  focusKey.value = key;
}
function onBlur(key) {
  if (focusKey.value === key) focusKey.value = "";
}
function clearError(key) {
  errors[key] = "";
}
function clearAllErrors() {
  Object.keys(errors).forEach((k) => errors[k] = "");
  resultMessage.value = "";
}
function fieldClass(key) {
  if (errors[key]) return "has-error border-auth-danger bg-auth-field";
  if (focusKey.value === key) return "border-auth-accent-line bg-auth-field-on";
  return "border-auth-line bg-auth-field";
}
function switchTab(next) {
  if (tab.value === next) return;
  tab.value = next;
  identifier.value = "";
  username.value = "";
  password.value = "";
  showPassword.value = false;
  needCaptcha.value = false;
  code.value = "";
  clearAllErrors();
  refreshCaptcha();
}
async function checkCaptchaState() {
  const account = username.value.trim();
  if (!account || needCaptcha.value) return;
  try {
    const st = await authApi.captchaState(account);
    if (st.needCaptcha) {
      needCaptcha.value = true;
      await refreshCaptcha();
    }
  } catch {
  }
}
async function submit() {
  if (loading.value) return;
  clearAllErrors();
  loading.value = true;
  try {
    if (tab.value === "account") await submitAccount();
    else await submitCode();
  } finally {
    loading.value = false;
  }
}
async function submitAccount() {
  let valid = true;
  if (!username.value.trim()) {
    errors.username = "请输入学号 / 工号 / 账号";
    valid = false;
  }
  if (!password.value) {
    errors.password = "请输入密码";
    valid = false;
  }
  if (needCaptcha.value && !captcha.value.trim()) {
    errors.captcha = "请输入图形验证码";
    valid = false;
  }
  if (!valid) return;
  try {
    await store.loginSecure(
      username.value.trim(),
      password.value,
      needCaptcha.value ? { id: captchaId.value, code: captcha.value.trim() } : void 0
    );
    afterLogin();
  } catch (e) {
    const message = humanMessage(e?.message, "登录失败，请稍后重试");
    errors.password = message;
    resultMessage.value = message;
    await checkCaptchaState();
    if (needCaptcha.value) await refreshCaptcha();
  }
}
async function submitCode() {
  const value = identifier.value.trim();
  let valid = true;
  if (tab.value === "phone" && !/^1\d{10}$/.test(value)) {
    errors.identifier = value ? "请输入 11 位手机号" : "请输入手机号";
    valid = false;
  }
  if (tab.value === "mail" && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) {
    errors.identifier = value ? "请输入正确的邮箱地址" : "请输入邮箱地址";
    valid = false;
  }
  if (!new RegExp('^\\d{' + CODE_LENGTH + '}$').test(code.value.trim())) {
    errors.code = code.value ? `验证码为 ${CODE_LENGTH} 位数字` : "请输入验证码";
    valid = false;
  }
  if (!valid) return;
  try {
    await store.loginWithCode(value, codeType.value, code.value.trim());
    afterLogin();
  } catch (e) {
    const message = humanMessage(e?.message, "登录失败，请检查验证码");
    errors.code = message;
    resultMessage.value = message;
    captchaId.value = "";
    await refreshCaptcha();
  }
}
function afterLogin() {
  toast("登录成功");
  const target = redirect.value || "/";
  window.setTimeout(() => router.replace(target), 260);
}
async function onSendCode() {
  if (!canSend.value) return;
  clearError("identifier");
  clearError("captcha");
  const value = identifier.value.trim();
  if (tab.value === "phone" && !/^1\d{10}$/.test(value)) {
    errors.identifier = value ? "请输入 11 位手机号" : "请输入手机号";
    return;
  }
  if (tab.value === "mail" && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value)) {
    errors.identifier = value ? "请输入正确的邮箱地址" : "请输入邮箱地址";
    return;
  }
  if (!captcha.value.trim()) {
    errors.captcha = "请输入图形验证码";
    return;
  }
  sending.value = true;
  try {
    const res = await authApi.sendCode({
      target: value,
      type: codeType.value,
      captchaId: captchaId.value,
      captchaCode: captcha.value.trim()
    });
    startCountdown(res.resendAfter || 60);
    const demo = res.demoCode ? `（演示码 ${res.demoCode}）` : "";
    toast(`验证码已发送至 ${res.target}${demo}`);
    captchaId.value = "";
    await refreshCaptcha();
  } catch (e) {
    await refreshCaptcha();
    toast(humanMessage(e?.message, "发送失败，请稍后重试"), "error");
  } finally {
    sending.value = false;
  }
}
function startCountdown(seconds) {
  remaining.value = seconds;
  window.clearInterval(timer);
  timer = window.setInterval(() => {
    remaining.value -= 1;
    if (remaining.value <= 0) window.clearInterval(timer);
  }, 1e3);
}
function thirdPartyLogin(label) {
  toast(`${label}登录即将开放`);
}
function openFooterLink(label) {
  toast(`${label}建设中`);
}
</script>

<template>
  <div class="auth-page bg-auth-stage">
    <!-- 浮尘：纯装饰，不接受点击 -->
    <div class="particles" aria-hidden="true">
      <span v-for="item in PARTICLES" :key="item.id" class="particle" :style="item.style" />
    </div>

    <div class="auth-body">
      <section class="auth-card bg-auth-card">
        <span class="card-top-line bg-auth-card-top" aria-hidden="true" />

        <!-- Header -->
        <header class="mb-7 flex flex-col items-center">
          <img class="logo-img mb-4" :src="APP_LOGO" alt="校园成长平台" />
          <h1 class="app-title">校园成长平台</h1>
          <p class="mt-1.5 text-xs text-auth-sub">portfolio · 请假 · 课表 · 奖学金 · 家校联动</p>
        </header>

        <!-- Tabs -->
        <div class="mb-6 flex gap-1.5 rounded-xl bg-white/[0.03] p-1" role="tablist">
          <button
            v-for="item in TABS"
            :key="item.key"
            type="button"
            role="tab"
            :aria-selected="item.key === tab"
            class="relative flex-1 rounded-[10px] py-2.5 transition"
            :class="item.key === tab ? 'bg-auth-accent-soft' : 'hover:bg-white/[0.03]'"
            @click="switchTab(item.key)"
          >
            <span class="flex items-center justify-center gap-1">
              <img class="h-6 w-6 shrink-0" :src="item.icon" alt="" />
              <span class="text-xs" :class="item.key === tab ? 'font-bold text-white' : 'text-auth-muted'">
                {{ item.label }}
              </span>
            </span>
            <span v-if="item.key === tab" class="tab-underline bg-auth-accent" />
          </button>
        </div>

        <!-- 账号 + 密码 -->
        <div v-if="tab === 'account'">
          <div class="mb-4">
            <div class="field" :class="fieldClass('username')">
              <span class="field-icon"><img class="h-6 w-6" :src="fieldIcon('user')" alt="" /></span>
              <input
                v-model="username"
                class="field-input"
                autocomplete="username"
                placeholder="学号 / 工号 / 账号"
                @focus="onFocus('username')"
                @blur="onBlur('username'); checkCaptchaState()"
                @input="clearError('username')"
                @keyup.enter="submit"
              />
            </div>
            <p v-if="errors.username" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.username }}</p>
          </div>

          <div class="mb-1">
            <div class="field" :class="fieldClass('password')">
              <span class="field-icon"><img class="h-6 w-6" :src="fieldIcon('lock')" alt="" /></span>
              <input
                v-model="password"
                class="field-input"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                @focus="onFocus('password')"
                @blur="onBlur('password')"
                @input="clearError('password')"
                @keyup.enter="submit"
              />
              <button
                type="button"
                class="flex h-full items-center px-3.5"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <img class="h-5 w-5" :src="fieldIcon(showPassword ? 'eye-off' : 'eye')" alt="" />
              </button>
            </div>
            <p v-if="errors.password" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.password }}</p>
          </div>

          <!-- 连续输错后才出现的图形验证码 -->
          <div v-if="needCaptcha" class="mt-4">
            <div class="flex items-center gap-2.5">
              <div class="field flex-1" :class="fieldClass('captcha')">
                <span class="field-icon"><img class="h-6 w-6" :src="fieldIcon('lock')" alt="" /></span>
                <input
                  v-model="captcha"
                  class="field-input"
                  :maxlength="CAPTCHA_LENGTH"
                  placeholder="图形验证码"
                  @focus="onFocus('captcha')"
                  @blur="onBlur('captcha')"
                  @input="clearError('captcha')"
                  @keyup.enter="submit"
                />
              </div>
              <button type="button" class="captcha-box" title="点击换一张" @click="refreshCaptcha()">
                <img v-if="captchaImage" class="captcha-img" :src="captchaImage" alt="图形验证码" />
                <span v-else class="text-[10px] text-auth-muted">点击获取</span>
              </button>
              <button type="button" class="refresh-btn" title="换一张" @click="refreshCaptcha()">
                <img class="h-5 w-5" :src="fieldIcon('refresh')" alt="" />
              </button>
            </div>
            <p v-if="errors.captcha" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.captcha }}</p>
          </div>
        </div>

        <!-- 手机 / 邮箱验证码 -->
        <div v-else>
          <div class="mb-4">
            <div class="field" :class="fieldClass('identifier')">
              <span class="field-icon"><img class="h-6 w-6" :src="identifierIcon" alt="" /></span>
              <input
                v-model="identifier"
                class="field-input"
                :type="tab === 'phone' ? 'tel' : 'email'"
                :inputmode="tab === 'phone' ? 'numeric' : 'email'"
                :maxlength="tab === 'phone' ? 11 : 64"
                :placeholder="identifierPlaceholder"
                @focus="onFocus('identifier')"
                @blur="onBlur('identifier')"
                @input="clearError('identifier')"
              />
            </div>
            <p v-if="errors.identifier" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.identifier }}</p>
          </div>

          <div class="mb-4">
            <div class="flex items-center gap-2.5">
              <div class="field flex-1" :class="fieldClass('captcha')">
                <span class="field-icon"><img class="h-6 w-6" :src="fieldIcon('lock')" alt="" /></span>
                <input
                  v-model="captcha"
                  class="field-input"
                  :maxlength="CAPTCHA_LENGTH"
                  placeholder="图形验证码（发码用）"
                  @focus="onFocus('captcha')"
                  @blur="onBlur('captcha')"
                  @input="clearError('captcha')"
                />
              </div>
              <button type="button" class="captcha-box" title="点击换一张" @click="refreshCaptcha()">
                <img v-if="captchaImage" class="captcha-img" :src="captchaImage" alt="图形验证码" />
                <span v-else class="text-[10px] text-auth-muted">点击获取</span>
              </button>
              <button type="button" class="refresh-btn" title="换一张" @click="refreshCaptcha()">
                <img class="h-5 w-5" :src="fieldIcon('refresh')" alt="" />
              </button>
            </div>
            <p v-if="errors.captcha" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.captcha }}</p>
          </div>

          <div class="mb-1">
            <div class="flex items-center gap-2.5">
              <div class="field flex-1" :class="fieldClass('code')">
                <span class="field-icon"><img class="h-6 w-6" :src="fieldIcon('bolt')" alt="" /></span>
                <input
                  v-model="code"
                  class="field-input"
                  type="text"
                  inputmode="numeric"
                  :maxlength="CODE_LENGTH"
                  :placeholder="tab === 'phone' ? '短信验证码' : '邮箱验证码'"
                  @focus="onFocus('code')"
                  @blur="onBlur('code')"
                  @input="clearError('code')"
                  @keyup.enter="submit"
                />
              </div>
              <button
                type="button"
                class="sms-btn"
                :class="canSend ? 'is-on' : 'is-off'"
                :disabled="!canSend"
                @click="onSendCode"
              >
                <span class="text-xs" :class="canSend ? 'text-auth-accent' : 'text-auth-muted'">{{ sendText }}</span>
              </button>
            </div>
            <p v-if="errors.code" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.code }}</p>
            <p class="mt-2 pl-1 text-[11px] text-auth-muted">本机未接短信/邮件网关，验证码以「演示码」形式在提示里给出</p>
          </div>
        </div>

        <!-- 提交 -->
        <button
          type="button"
          class="submit-btn bg-auth-submit"
          :class="{ 'is-loading': loading }"
          :disabled="loading"
          @click="submit"
        >
          <span v-if="!loading" class="text-base font-bold tracking-wide text-white">登 录</span>
          <span v-else class="flex items-center">
            <span class="dot" />
            <span class="dot dot-2" />
            <span class="dot dot-3" />
          </span>
        </button>

        <p
          v-if="resultMessage"
          class="mt-3 rounded-lg bg-auth-danger/10 px-3 py-2 text-center text-xs text-auth-danger"
        >
          {{ resultMessage }}
        </p>

        <p class="mt-4 text-center text-[11px] text-auth-muted">
          还没有账号？
          <router-link to="/register" class="text-auth-sub underline decoration-dotted hover:text-white">注册学生账号</router-link>
        </p>

        <!-- 分割线 -->
        <div class="my-5 flex items-center gap-3.5">
          <span class="h-px flex-1 bg-auth-divider" />
          <span class="text-[11px] text-auth-muted">其他方式登录</span>
          <span class="h-px flex-1 bg-auth-divider" />
        </div>

        <div class="flex justify-center gap-5">
          <button
            v-for="item in THIRD_PARTY"
            :key="item.key"
            type="button"
            class="social-btn"
            :aria-label="`${item.label}登录`"
            @click="thirdPartyLogin(item.label)"
          >
            <img class="social-icon" :src="oauthIcon(item.key)" alt="" />
          </button>
        </div>

        <div class="mt-6 flex items-center justify-center">
          <template v-for="(link, index) in FOOTER_LINKS" :key="link">
            <span v-if="index > 0" class="mx-3.5 h-2.5 w-px bg-white/10" />
            <button type="button" class="text-[11px] text-auth-sub hover:text-white" @click="openFooterLink(link)">
              {{ link }}
            </button>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* ── 页面骨架 ───────────────────────────────────── */
.auth-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

/**
 * 卡片布局：移动端是"贴底抽屉"，宽屏是居中卡片。
 * 不用 position: fixed —— 表单比屏幕高时 fixed 会让底部内容够不着。
 */
.auth-body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.auth-card {
  position: relative;
  width: 100%;
  margin-top: auto;
  padding: 32px 24px calc(28px + env(safe-area-inset-bottom));
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-bottom: none;
  border-radius: 20px 20px 0 0;
  box-shadow: 0 -4px 30px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.03);
  animation: card-up 0.5s ease-out both;
}

.card-top-line {
  position: absolute;
  top: 0;
  left: 40px;
  right: 40px;
  height: 2px;
  border-radius: 2px;
}

@keyframes card-up {
  from {
    transform: translateY(30px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@media screen and (min-width: 481px) {
  .auth-body {
    align-items: center;
    justify-content: center;
    padding: 24px 12px;
  }

  .auth-card {
    max-width: 440px;
    margin-top: 0;
    padding: 44px 40px 36px;
    border-radius: 20px;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2), 0 40px 80px rgba(0, 0, 0, 0.35),
      inset 0 1px 0 rgba(255, 255, 255, 0.03);
    animation-name: card-in;
  }

  .card-top-line {
    height: 1px;
  }

  @keyframes card-in {
    from {
      transform: translateY(24px) scale(0.97);
      opacity: 0;
    }
    to {
      transform: translateY(0) scale(1);
      opacity: 1;
    }
  }
}

/* ── 标题与 logo ─────────────────────────────────── */
.logo-img {
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 14px;
}

.app-title {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.2;
  /* 不支持 background-clip: text 时的实色兜底 */
  color: #8fa6ff;
  background-image: linear-gradient(135deg, #6d8fff, #a78bfa, #6d8fff);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: title-shine 3s ease-in-out infinite;
}

@keyframes title-shine {
  0%,
  100% {
    background-position: 0% center;
  }
  50% {
    background-position: 200% center;
  }
}

/* ── tab 选中态下划线 ───────────────────────────── */
.tab-underline {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 20px;
  height: 2px;
  margin-left: -10px;
  border-radius: 2px;
}

/* ── 输入框 ─────────────────────────────────────── */
.field {
  display: flex;
  align-items: center;
  height: 46px;
  overflow: hidden;
  border-width: 1px;
  border-style: solid;
  border-radius: 12px;
  transition: border-color 0.25s ease, background-color 0.25s ease;
}

.field:focus-within {
  border-color: rgba(79, 140, 255, 0.35);
  background-color: rgba(255, 255, 255, 0.07);
}

.field.has-error:focus-within {
  border-color: rgba(248, 113, 113, 0.55);
}

.field-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  flex-shrink: 0;
}

.field-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding-right: 12px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #e8eaef;
}

.field-input::placeholder {
  color: #5a6380;
}

/* 自动填充会把浅色底刷回来，这里压回深色 */
.field-input:-webkit-autofill {
  -webkit-text-fill-color: #e8eaef;
  -webkit-box-shadow: 0 0 0 1000px #141a2b inset;
  transition: background-color 99999s ease-in-out 0s;
}

/* ── 图形验证码 ─────────────────────────────────── */
.captcha-box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100px;
  height: 46px;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  background: linear-gradient(135deg, #1a2040, #1e2848);
  cursor: pointer;
}

.captcha-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.04);
  transition: background-color 0.2s ease;
}

.refresh-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}

/* ── 获取验证码 ─────────────────────────────────── */
.sms-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 46px;
  padding: 0 12px;
  flex-shrink: 0;
  border-width: 1px;
  border-style: solid;
  border-radius: 8px;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.sms-btn.is-on {
  border-color: rgba(79, 140, 255, 0.18);
  background: rgba(79, 140, 255, 0.1);
}

.sms-btn.is-off {
  border-color: rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.03);
  cursor: not-allowed;
}

/* ── 提交按钮 ───────────────────────────────────── */
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 48px;
  margin-top: 26px;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(79, 140, 255, 0.28);
  transition: opacity 0.2s ease, transform 0.15s ease;
}

.submit-btn:hover {
  opacity: 0.94;
}

.submit-btn:active {
  transform: scale(0.99);
}

.submit-btn.is-loading {
  opacity: 0.85;
  cursor: default;
}

.dot {
  width: 6px;
  height: 6px;
  margin: 0 3px;
  border-radius: 999px;
  background: #fff;
  animation: dot-bounce 1.2s ease-in-out infinite;
}

.dot-2 {
  animation-delay: 0.15s;
}

.dot-3 {
  animation-delay: 0.3s;
}

@keyframes dot-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.4);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* ── 第三方圆钮 ─────────────────────────────────── */
.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: floralwhite;
  transition: transform 0.15s ease, box-shadow 0.2s ease;
}

.social-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.35);
}

.social-icon {
  width: 28px;
  height: 28px;
  object-fit: contain;
}

/* ── 浮尘粒子 ───────────────────────────────────── */
.particles {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.particle {
  position: absolute;
  bottom: -10px;
  width: 2px;
  height: 2px;
  border-radius: 999px;
  background: rgba(79, 140, 255, 0.5);
  animation-name: particle-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

@keyframes particle-drift {
  0% {
    transform: translateY(0) scale(0);
  }
  10% {
    transform: translateY(-10vh) scale(1);
  }
  100% {
    transform: translateY(-110vh) scale(1.5);
  }
}
</style>
