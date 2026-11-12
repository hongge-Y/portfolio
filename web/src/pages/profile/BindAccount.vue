<script setup>
/**
 * 绑定手机 / 绑定邮箱（一个页面两种类型，路由带 type 参数）。
 * 后端 /v1/profile/bind 要求当前密码确认，防止 token 被盗后直接换绑。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { profileApi } from '@/api/profile'
import { toast } from '@/utils/toast'

const route = useRoute()
const router = useRouter()

const type = computed(() => (route.params.type === 'mail' ? 'mail' : 'mobile'))
const isMail = computed(() => type.value === 'mail')

const current = ref('')   // 已绑定的脱敏值
const bound = ref(false)
const value = ref('')
const password = ref('')
const saving = ref(false)

const MOBILE = /^1[3-9]\d{9}$/
const MAIL = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/

const error = computed(() => {
  if (!value.value.trim()) return isMail.value ? '请输入邮箱' : '请输入手机号'
  if (isMail.value && !MAIL.test(value.value.trim())) return '邮箱格式不正确'
  if (!isMail.value && !MOBILE.test(value.value.trim())) return '手机号格式不正确'
  if (!password.value) return '请输入当前密码以确认身份'
  return ''
})

async function load() {
  try {
    const me = await profileApi.me()
    bound.value = isMail.value ? !!me.mailBound : !!me.mobileBound
    current.value = (isMail.value ? me.mail : me.mobile) || ''
  } catch (e) {
    toast(e?.message || '加载失败', 'error')
  }
}

async function submit() {
  if (error.value) return toast(error.value, 'error')
  saving.value = true
  try {
    const res = await profileApi.bind(type.value, value.value.trim(), password.value)
    toast(bound.value ? '已更换绑定' : '绑定成功')
    current.value = res?.value || ''
    bound.value = true
    value.value = ''
    password.value = ''
  } catch (e) {
    toast(e?.message || '绑定失败', 'error')
  } finally {
    saving.value = false
  }
}

watch(type, load)
onMounted(load)
</script>

<template>
  <div>

    <div class="space-y-3">
      <section class="card space-y-3">
        <label class="block">
          <span class="text-xs text-slate-500">{{ isMail ? '邮箱地址' : '手机号' }}</span>
          <input
            v-model="value"
            class="field mt-1"
            :placeholder="isMail ? 'name@example.com' : '11 位手机号'"
            :inputmode="isMail ? 'email' : 'numeric'"
          />
        </label>
        <label class="block">
          <span class="text-xs text-slate-500">当前密码（确认身份）</span>
          <input v-model="password" type="password" class="field mt-1" placeholder="请输入登录密码" />
        </label>

        <p v-if="value && error" class="text-[11px] text-rose-500">{{ error }}</p>

        <button
          class="w-full rounded-xl bg-brand-500 py-2.5 text-sm font-medium text-white transition disabled:opacity-50"
          :disabled="saving"
          @click="submit"
        >{{ saving ? '提交中…' : bound ? '更换绑定' : '立即绑定' }}</button>
      </section>

      <section class="card space-y-1.5 text-[11px] leading-relaxed text-slate-500">
        <p>· 绑定的{{ isMail ? '邮箱' : '手机号' }}可以用来接收消息提醒、验证码登录。</p>
        <p>· 换绑需要当前密码确认；忘记密码请先在「修改密码」里重设。</p>
        <p>· 本演示环境没有真实短信/邮件网关，验证码走演示模式。</p>
      </section>

      <button class="w-full text-center text-xs text-slate-400 underline" @click="router.back()">返回</button>
    </div>
  </div>
</template>
