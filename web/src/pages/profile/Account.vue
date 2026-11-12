<script setup>
/**
 * 我的（一级页面，套 AppShell）
 *
 * 桌面：左栏身份卡 + 分组菜单（吸顶），右栏是只读概览（身份 / 最近登录 / 环境）。
 * 移动：单列 —— 身份卡 → 菜单 → 概览 → 退出。
 *
 * 菜单数据来自 features/account/menu.js；绑定状态、家长/学生身份这类动态文案在这里按 key 覆盖。
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataState from '@/components/common/DataState.vue'
import { profileApi } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useAsyncData } from '@/composables/useAsyncData'
import { useConfirm } from '@/composables/useConfirm'
import { MENU_GROUPS, menuOfGroup } from '@/features/account/menu'
import { roleLabels } from '@/features/account/roles'

const router = useRouter()
const store = useUserStore()
const app = useAppStore()
const { confirm } = useConfirm()

const avatarBroken = ref(false)

/** 两个接口一起拉：资料挂了整页算错，组织信息挂了只影响右栏 */
const { data, status, error, run } = useAsyncData(async () => {
  const [me, org] = await Promise.allSettled([profileApi.me(), profileApi.org()])
  if (me.status === 'rejected') throw me.reason
  return { me: me.value, org: org.status === 'fulfilled' ? org.value : null }
})

const me = computed(() => data.value?.me ?? null)
const org = computed(() => data.value?.org ?? null)

/** 角色中文名：优先用组织接口给的（带班级语境），退回本地字典 */
const roleNames = computed(() => {
  const list = org.value?.roles || []
  if (list.length) return list.map((r) => r.name).filter(Boolean)
  return roleLabels(me.value?.roles || [])
})

const primaryUnit = computed(() => {
  const units = org.value?.units || []
  return units.find((u) => u.isPrimary === 1) || units[0] || null
})

/** 菜单：静态结构 + 按 key 覆盖的动态文案/角标 */
const menus = computed(() =>
  menuOfGroup(1)
    .concat(menuOfGroup(2), menuOfGroup(3))
    .map((item) => {
      if (item.key === 'phone') {
        const bound = !!me.value?.mobileBound
        return { ...item, desc: bound ? me.value.mobile : '未绑定', tag: bound ? '已绑' : '去绑定' }
      }
      if (item.key === 'mail') {
        const bound = !!me.value?.mailBound
        return { ...item, desc: bound ? me.value.mail : '未绑定', tag: bound ? '已绑' : '去绑定' }
      }
      if (item.key === 'relative') {
        return {
          ...item,
          desc: store.isParent ? '查看关联的孩子' : '邀请家长绑定',
          tag: store.isParent ? '家长' : ''
        }
      }
      return { ...item, tag: '' }
    })
)

/** 右栏概览：只放接口真给的数据，没有的整行不渲染 */
const overview = computed(() => {
  const list = []
  const employee = org.value?.employee
  if (employee) {
    list.push({ label: '姓名', value: employee.name || '—' })
    list.push({ label: '工号 / 学号', value: employee.employeeNo || '—' })
    list.push({ label: '所属单位', value: primaryUnit.value?.orgName || employee.orgName || '—' })
  }
  list.push({ label: '账号', value: me.value?.account || '—' })
  list.push({ label: '身份', value: roleNames.value.join(' / ') || '—' })
  if (org.value?.permissions?.length) {
    list.push({ label: '权限项', value: `${org.value.permissions.length} 项` })
  }
  return list
})

async function logout() {
  const ok = await confirm({
    title: '退出登录',
    content: '退出后需要重新输入账号密码才能进入。',
    confirmText: '退出',
    tone: 'danger'
  })
  if (!ok) return
  store.logout()
  app.reset()
  router.replace('/login')
}
</script>

<template>
  <!-- 根必须是单个元素：App.vue 用 <Transition> 包页面，组件为根时无法动画（会卡在进入态） -->
  <div>
    <DataState :status="status" :error-text="error" :rows="5" @retry="run">
    <div class="grid gap-4 lg:grid-cols-[300px_minmax(0,1fr)] lg:items-start lg:gap-5">
      <!-- 左栏：身份卡 + 菜单 -->
      <div class="min-w-0 space-y-4 lg:sticky lg:top-[76px]">
        <!-- 身份卡 -->
        <section
          class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-brand-500 via-brand-600 to-indigo-500 p-5 text-white shadow-lg shadow-brand-500/20"
        >
          <span class="pointer-events-none absolute -right-8 -top-10 h-32 w-32 rounded-full bg-white/10" />
          <div class="relative flex items-center gap-3">
            <img
              v-show="!avatarBroken"
              :src="me?.avatar || '/avatar/default.svg'"
              alt="头像"
              class="h-14 w-14 rounded-2xl bg-white/20 object-cover ring-2 ring-white/40"
              @error="avatarBroken = true"
            />
            <span
              v-if="avatarBroken"
              class="grid h-14 w-14 place-items-center rounded-2xl bg-white/20 text-xl font-bold ring-2 ring-white/40"
            >
              {{ (me?.showName || me?.account || '?').slice(0, 1) }}
            </span>
            <div class="min-w-0 flex-1">
              <p class="truncate text-lg font-bold leading-tight">
                {{ me?.showName || me?.account || '—' }}
              </p>
              <p class="mt-0.5 truncate text-xs text-white/80">
                账号 {{ me?.account }} · {{ roleNames.join(' / ') || '—' }}
              </p>
              <p v-if="primaryUnit" class="mt-0.5 truncate text-[11px] text-white/70">
                {{ primaryUnit.orgName }}
                <template v-if="primaryUnit.employeeType"> · {{ primaryUnit.employeeType }}</template>
              </p>
            </div>
          </div>

          <div class="relative mt-4 grid grid-cols-3 gap-2 text-center">
            <div class="rounded-xl bg-white/15 py-2">
              <p class="text-lg font-bold leading-none">{{ me?.coin ?? 0 }}</p>
              <p class="mt-1 text-[11px] text-white/75">光粒</p>
            </div>
            <div class="rounded-xl bg-white/15 py-2">
              <p class="text-lg font-bold leading-none">{{ me?.gift ?? 0 }}</p>
              <p class="mt-1 text-[11px] text-white/75">礼盒</p>
            </div>
            <div class="rounded-xl bg-white/15 py-2">
              <p class="text-lg font-bold leading-none">{{ me?.loginCount ?? 0 }}</p>
              <p class="mt-1 text-[11px] text-white/75">登录次数</p>
            </div>
          </div>
        </section>

        <!-- 分组菜单 -->
        <section v-for="group in [1, 2, 3]" :key="group" class="card-flat overflow-hidden">
          <p class="border-b border-slate-100 px-4 py-2 text-[11px] font-medium text-slate-400">
            {{ MENU_GROUPS[group] }}
          </p>
          <button
            v-for="m in menus.filter((x) => x.group === group)"
            :key="m.key"
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left transition hover:bg-slate-50"
            @click="router.push(m.path)"
          >
            <span class="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-slate-50 text-base">
              {{ m.icon }}
            </span>
            <span class="min-w-0 flex-1">
              <span class="block text-sm font-medium text-slate-800">{{ m.title }}</span>
              <span class="block truncate text-[11px] text-slate-400">{{ m.desc }}</span>
            </span>
            <span
              v-if="m.tag"
              class="shrink-0 rounded-md bg-brand-50 px-1.5 py-0.5 text-[10px] text-brand-700"
            >
              {{ m.tag }}
            </span>
            <svg
              class="h-3.5 w-3.5 shrink-0 text-slate-300"
              viewBox="0 0 20 20"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
            >
              <path d="m8 5 5 5-5 5" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
        </section>
      </div>

      <!-- 右栏：只读概览 -->
      <div class="min-w-0 space-y-4">
        <section class="card">
          <header class="mb-3 flex items-center justify-between">
            <h2 class="section-title">账号概览</h2>
            <button type="button" class="btn-text" @click="router.push('/profile/edit')">
              编辑资料 ›
            </button>
          </header>
          <dl class="grid gap-x-6 gap-y-2.5 text-sm sm:grid-cols-2">
            <div v-for="row in overview" :key="row.label" class="flex items-baseline justify-between gap-3">
              <dt class="shrink-0 text-xs text-slate-400">{{ row.label }}</dt>
              <dd class="min-w-0 truncate text-right text-slate-700">{{ row.value }}</dd>
            </div>
          </dl>
        </section>

        <!-- 组织 -->
        <section v-if="org?.units?.length" class="card">
          <header class="mb-3 flex items-center justify-between">
            <h2 class="section-title">组织与角色</h2>
            <button type="button" class="btn-text" @click="router.push('/profile/org')">
              查看详情 ›
            </button>
          </header>
          <ul class="space-y-2">
            <li
              v-for="u in org.units"
              :key="u.orgId || u.orgName"
              class="flex items-center justify-between gap-3 rounded-xl border border-slate-100 px-3 py-2"
            >
              <div class="min-w-0">
                <p class="truncate text-sm text-slate-700">{{ u.orgName }}</p>
                <p class="truncate text-[11px] text-slate-400">
                  {{ u.orgType || '组织' }}
                  <template v-if="u.employeeType"> · {{ u.employeeType }}</template>
                </p>
              </div>
              <span v-if="u.isPrimary === 1" class="shrink-0 rounded-md bg-brand-50 px-1.5 py-0.5 text-[10px] text-brand-700">
                主身份
              </span>
            </li>
          </ul>
        </section>

        <!-- 安全摘要 -->
        <section class="card">
          <header class="mb-3 flex items-center justify-between">
            <h2 class="section-title">账号安全</h2>
            <button type="button" class="btn-text" @click="router.push('/profile/security')">
              安全中心 ›
            </button>
          </header>
          <div class="grid gap-2 sm:grid-cols-3">
            <div class="rounded-xl border border-slate-100 px-3 py-2.5">
              <p class="text-[11px] text-slate-400">手机</p>
              <p class="mt-0.5 truncate text-sm" :class="me?.mobileBound ? 'text-slate-700' : 'text-amber-600'">
                {{ me?.mobileBound ? '已绑定' : '未绑定' }}
              </p>
            </div>
            <div class="rounded-xl border border-slate-100 px-3 py-2.5">
              <p class="text-[11px] text-slate-400">邮箱</p>
              <p class="mt-0.5 truncate text-sm" :class="me?.mailBound ? 'text-slate-700' : 'text-amber-600'">
                {{ me?.mailBound ? '已绑定' : '未绑定' }}
              </p>
            </div>
            <div class="rounded-xl border border-slate-100 px-3 py-2.5">
              <p class="text-[11px] text-slate-400">最近登录</p>
              <p class="mt-0.5 truncate text-sm text-slate-700">
                {{ (me?.lastLoginTime || '').slice(5, 16) || '—' }}
              </p>
            </div>
          </div>
        </section>

        <!-- 退出 -->
        <div class="flex flex-col gap-2 sm:flex-row">
          <button
            type="button"
            class="btn-ghost sm:w-auto"
            @click="router.push('/profile/password')"
          >
            修改密码
          </button>
          <button
            type="button"
            class="btn flex-1 border border-slate-200 bg-white text-rose-600 hover:border-rose-200 hover:bg-rose-50 sm:flex-none"
            @click="logout"
          >
            退出登录
          </button>
        </div>

        <p v-if="me?.lastLoginIp" class="text-center text-[11px] text-slate-300">
          上次登录 IP {{ me.lastLoginIp }}
        </p>
      </div>
    </div>
    </DataState>
  </div>
</template>
