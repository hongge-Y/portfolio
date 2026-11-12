<script setup>
/**
 * 账号安全：绑定状态 + 风险预警 + 近期登录记录。
 * 数据：/v1/profile/me（绑定状态）、/risks、/login-logs。
 */
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import DataState from '@/components/common/DataState.vue'
import { profileApi } from '@/api/profile'

const status = ref('loading')
const errorText = ref('')
const me = ref(null)
const risks = ref([])
const logs = ref([])

const LOGIN_TYPE = { PASSWORD: '密码', CODE: '验证码', SMS: '短信', MAIL: '邮箱', OAUTH: '第三方' }

async function load() {
  status.value = 'loading'
  try {
    const [m, r, l] = await Promise.allSettled([profileApi.me(), profileApi.risks(), profileApi.loginLogs()])
    if (m.status === 'rejected') throw m.reason
    me.value = m.value
    risks.value = r.status === 'fulfilled' ? r.value || [] : []
    logs.value = l.status === 'fulfilled' ? (l.value || []).slice(0, 10) : []
    status.value = 'ready'
  } catch (e) {
    status.value = 'error'
    errorText.value = e?.message || '加载失败'
  }
}

onMounted(load)
</script>

<template>
  <div>

    <div>
      <DataState :status="status" :error-text="errorText" :rows="6" @retry="load">
        <div class="space-y-4">
          <!-- 绑定状态 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">绑定状态</p>
            <ul class="divide-y divide-slate-100">
              <li class="flex items-center justify-between py-2.5">
                <span class="text-sm">手机号</span>
                <span class="text-xs" :class="me?.mobileBound ? 'text-slate-500' : 'text-amber-600'">
                  {{ me?.mobileBound ? me.mobile : '未绑定' }}
                </span>
              </li>
              <li class="flex items-center justify-between py-2.5">
                <span class="text-sm">邮箱</span>
                <span class="text-xs" :class="me?.mailBound ? 'text-slate-500' : 'text-amber-600'">
                  {{ me?.mailBound ? me.mail : '未绑定' }}
                </span>
              </li>
              <li class="flex items-center justify-between py-2.5">
                <span class="text-sm">登录密码</span>
                <span class="text-xs text-slate-500">已设置</span>
              </li>
            </ul>
          </section>

          <!-- 风险预警 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">风险预警</p>
            <p v-if="!risks.length" class="py-4 text-center text-xs text-slate-400">
              暂无预警，学习状态正常
            </p>
            <ul v-else class="space-y-2">
              <li v-for="(r, i) in risks" :key="i" class="rounded-xl bg-rose-50/70 px-3 py-2">
                <p class="text-sm text-rose-700">{{ r.title || r.riskName || r.type || '风险提示' }}</p>
                <p class="mt-0.5 text-[11px] text-rose-500/80">{{ r.content || r.desc || r.remark || '—' }}</p>
              </li>
            </ul>
          </section>

          <!-- 登录记录 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">近期登录</p>
            <p v-if="!logs.length" class="py-4 text-center text-xs text-slate-400">暂无记录</p>
            <ul v-else class="divide-y divide-slate-100">
              <li v-for="l in logs" :key="l.id" class="flex items-start gap-2 py-2">
                <span
                  class="mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[10px]"
                  :class="l.success === 1 ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'"
                >{{ l.success === 1 ? '成功' : '失败' }}</span>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-xs text-slate-600">
                    {{ LOGIN_TYPE[l.loginType] || l.loginType }} · {{ l.ip || '—' }}
                  </p>
                  <p class="truncate text-[11px] text-slate-400">
                    {{ (l.createTime || '').slice(0, 16) }}
                    <template v-if="l.msg"> · {{ l.msg }}</template>
                  </p>
                </div>
              </li>
            </ul>
          </section>
        </div>
      </DataState>
    </div>
  </div>
</template>
