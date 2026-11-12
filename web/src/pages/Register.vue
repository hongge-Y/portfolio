<script setup>
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { toast } from "@/utils/toast";
import { authApi } from "@/api/auth";
import { APP_LOGO } from "@/utils/icons";

const router = useRouter();

const account = ref("");
const showName = ref("");
const classId = ref("");
const mobile = ref("");
const password = ref("");
const password2 = ref("");
const showPassword = ref(false);
const classes = ref([]);
const submitting = ref(false);
const errors = ref({});
const serverError = ref("");

const canSubmit = computed(
  () => account.value && showName.value && classId.value && password.value && password2.value && !submitting.value
);

onMounted(async () => {
  try {
    const list = await authApi.classes();
    classes.value = Array.isArray(list) ? list : list.list || [];
  } catch {
    classes.value = [];
  }
});

function clearError(key) {
  if (errors.value[key]) errors.value = { ...errors.value, [key]: "" };
  serverError.value = "";
}

function validate() {
  const e = {};
  if (!/^\d{8,12}$/.test(account.value)) e.account = "学号须为 8-12 位数字";
  if (showName.value.trim().length < 2) e.showName = "请填写真实姓名（至少 2 个字）";
  if (!classId.value) e.classId = "请选择所在班级";
  if (mobile.value && !/^1\d{10}$/.test(mobile.value)) e.mobile = "手机号格式不正确";
  if (password.value.length < 6 || password.value.length > 20) e.password = "密码长度需为 6-20 位";
  if (password.value !== password2.value) e.password2 = "两次输入的密码不一致";
  errors.value = e;
  return Object.keys(e).length === 0;
}

async function submit() {
  if (!validate()) return;
  submitting.value = true;
  serverError.value = "";
  try {
    await authApi.register({
      account: account.value.trim(),
      showName: showName.value.trim(),
      password: password.value,
      mobile: mobile.value || undefined,
      classId: Number(classId.value)
    });
    toast("注册成功，请用学号登录");
    router.push({ path: "/login", query: { account: account.value.trim() } });
  } catch (e) {
    // 后端返回 REGISTER_DUP / REGISTER_CLASS 等业务码，直接把 message 展示出来
    serverError.value = e?.response?.data?.message || e?.message || "注册失败，请稍后重试";
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="auth-page bg-auth-stage">
    <div class="auth-body">
      <section class="auth-card bg-auth-card">
        <span class="card-top-line bg-auth-card-top" aria-hidden="true" />

        <header class="mb-6 flex flex-col items-center">
          <img class="logo-img mb-3" :src="APP_LOGO" alt="校园成长平台" />
          <h1 class="app-title">校园成长平台</h1>
          <p class="mt-1.5 text-xs text-auth-sub">学生注册 · 一次填完即可用学号登录</p>
        </header>

        <div class="mb-3">
          <div class="field">
            <input
              v-model="account"
              class="field-input"
              maxlength="12"
              inputmode="numeric"
              placeholder="学号（8-12 位数字）"
              @input="clearError('account')"
            />
          </div>
          <p v-if="errors.account" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.account }}</p>
        </div>

        <div class="mb-3">
          <div class="field">
            <input
              v-model="showName"
              class="field-input"
              maxlength="20"
              placeholder="姓名"
              @input="clearError('showName')"
            />
          </div>
          <p v-if="errors.showName" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.showName }}</p>
        </div>

        <div class="mb-3">
          <div class="field">
            <select v-model="classId" class="field-input select-input" @change="clearError('classId')">
              <option value="">选择所在班级</option>
              <option v-for="c in classes" :key="c.id" :value="c.id">
                {{ c.college ? c.college + " · " : "" }}{{ c.name }}
              </option>
            </select>
          </div>
          <p v-if="errors.classId" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.classId }}</p>
        </div>

        <div class="mb-3">
          <div class="field">
            <input
              v-model="mobile"
              class="field-input"
              maxlength="11"
              inputmode="numeric"
              placeholder="手机号（选填，用于验证码登录）"
              @input="clearError('mobile')"
            />
          </div>
          <p v-if="errors.mobile" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.mobile }}</p>
        </div>

        <div class="mb-3">
          <div class="field flex">
            <input
              v-model="password"
              class="field-input"
              :type="showPassword ? 'text' : 'password'"
              maxlength="20"
              placeholder="密码（6-20 位）"
              @input="clearError('password')"
            />
            <button
              type="button"
              class="px-3 text-[11px] text-auth-muted hover:text-white"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? "隐藏" : "显示" }}
            </button>
          </div>
          <p v-if="errors.password" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.password }}</p>
        </div>

        <div class="mb-4">
          <div class="field">
            <input
              v-model="password2"
              class="field-input"
              :type="showPassword ? 'text' : 'password'"
              maxlength="20"
              placeholder="确认密码"
              @input="clearError('password2')"
              @keyup.enter="submit"
            />
          </div>
          <p v-if="errors.password2" class="mt-1.5 pl-1 text-[11px] text-auth-danger">{{ errors.password2 }}</p>
        </div>

        <p v-if="serverError" class="mb-3 rounded-lg bg-auth-danger/10 px-3 py-2 text-center text-xs text-auth-danger">
          {{ serverError }}
        </p>

        <button type="button" class="submit-btn" :disabled="!canSubmit" @click="submit">
          {{ submitting ? "提交中…" : "注册并返回登录" }}
        </button>

        <p class="mt-4 text-center text-[11px] text-auth-muted">
          已有账号？
          <router-link to="/login" class="text-auth-sub underline decoration-dotted hover:text-white">返回登录</router-link>
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  min-height: 100vh;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
}
.auth-body {
  width: 100%;
  max-width: 420px;
}
.auth-card {
  position: relative;
  border-radius: 20px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  padding: 28px 26px 24px;
}
.card-top-line {
  position: absolute;
  top: 0;
  left: 24px;
  right: 24px;
  height: 1px;
}
.logo-img {
  width: 44px;
  height: 44px;
}
.app-title {
  font-size: 20px;
  font-weight: 600;
  color: #e8eaef;
  letter-spacing: 0.5px;
}
.field {
  display: flex;
  align-items: center;
  height: 46px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  transition: border-color 0.25s ease, background-color 0.25s ease;
}
.field:focus-within {
  border-color: rgba(79, 140, 255, 0.35);
  background-color: rgba(255, 255, 255, 0.07);
}
.field-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0 14px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #e8eaef;
}
.field-input::placeholder {
  color: #5a6380;
}
.select-input option {
  background: #141a2b;
  color: #e8eaef;
}
.submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #4f8cff 0%, #3d6fd9 50%, #4f8cff 100%);
  transition: opacity 0.2s ease;
}
.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
