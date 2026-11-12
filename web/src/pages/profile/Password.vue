<script setup>
/**
 * 修改密码：前端先做一遍规则校验，减少一次无效请求；
 * 后端同样会校验（旧密码是否正确、新旧是否相同、是否字母+数字）。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { profileApi } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'

const router = useRouter()
const store = useUserStore()

const oldPassword = ref('')
const newPassword = ref('')
const confirm = ref('')
const saving = ref(false)

const rule = /^(?=.*[A-Za-z])(?=.*\d)[\w!@#$%^&*.-]{6,20}$/

const errors = computed(() => {
  const e = {}
  if (!oldPassword.value) e.old = '请输入当前密码'
  if (!newPassword.value) e.new = '请输入新密码'
  else if (!rule.test(newPassword.value)) e.new = '6-20 位，需同时包含字母和数字'
  else if (newPassword.value === oldPassword.value) e.new = '新密码不能与当前密码相同'
  if (!confirm.value) e.confirm = '请再次输入新密码'
  else if (confirm.value !== newPassword.value) e.confirm = '两次输入不一致'
  return e
})

const ok = computed(() => Object.keys(errors.value).length === 0)

async function submit() {
  if (!ok.value) return toast(Object.values(errors.value)[0], 'error')
  saving.value = true
  try {
    await profileApi.password(oldPassword.value, newPassword.value)
    toast('密码已修改，请重新登录')
    store.logout()
    setTimeout(() => router.replace('/login'), 900)
  } catch (e) {
    toast(e?.message || '修改失败', 'error')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>

    <div>
      <section class="card space-y-3">
        <label class="block">
          <span class="text-xs text-slate-500">当前密码</span>
          <input v-model="oldPassword" type="password" class="field mt-1" placeholder="请输入当前密码" />
          <span v-if="errors.old" class="mt-1 block text-[11px] text-rose-500">{{ errors.old }}</span>
        </label>
        <label class="block">
          <span class="text-xs text-slate-500">新密码</span>
          <input v-model="newPassword" type="password" class="field mt-1" placeholder="6-20 位，字母 + 数字" />
          <span v-if="errors.new" class="mt-1 block text-[11px] text-rose-500">{{ errors.new }}</span>
        </label>
        <label class="block">
          <span class="text-xs text-slate-500">确认新密码</span>
          <input v-model="confirm" type="password" class="field mt-1" placeholder="再输入一次" />
          <span v-if="errors.confirm" class="mt-1 block text-[11px] text-rose-500">{{ errors.confirm }}</span>
        </label>

        <p class="rounded-lg bg-amber-50 px-3 py-2 text-[11px] text-amber-700">
          改完密码后当前登录会失效，需要用新密码重新登录。
        </p>

        <button
          class="w-full rounded-xl bg-brand-500 py-2.5 text-sm font-medium text-white transition disabled:opacity-50"
          :disabled="saving"
          @click="submit"
        >{{ saving ? '提交中…' : '确认修改' }}</button>
      </section>
    </div>
  </div>
</template>
