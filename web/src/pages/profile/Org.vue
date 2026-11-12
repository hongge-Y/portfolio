<script setup>
/**
 * 组织与角色：我的班级/单位、身份、权限清单。
 * 学生在"软件工程2025级1班"，辅导员带多个班，管理员是全校 —— 都用同一套结构展示。
 */
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import DataState from '@/components/common/DataState.vue'
import { profileApi } from '@/api/profile'

const status = ref('loading')
const errorText = ref('')
const org = ref(null)

const ORG_TYPE = { 1: '学校', 2: '学院', 3: '系', 4: '班级', 5: '部门' }

/** 权限码 → 人话，未收录的直接显示原码，方便排查 */
const PERM_TEXT = {
  'leave:apply': '发起请假',
  'leave:approve': '审批请假',
  'leave:self:view': '查看我的请假',
  'leave:class:view': '查看班级请假',
  'leave:share': '请假分享链接',
  'rank:self:view': '查看我的红黑榜',
  'rank:manage': '管理红黑榜',
  'edu:timetable:view': '查看课表',
  'edu:timetable:manage': '导入课表',
  'edu:eval:submit': '课程评价',
  'scholarship:apply': '奖学金申请',
  'scholarship:approve': '奖学金审批',
  'message:send': '发送消息',
  'message:publish': '发布公告',
  'relative:confirm': '家长关联确认',
  'admin:all': '全部管理权限'
}

async function load() {
  status.value = 'loading'
  try {
    org.value = await profileApi.org()
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
      <DataState :status="status" :error-text="errorText" :rows="5" @retry="load">
        <div class="space-y-4">
          <!-- 员工档案 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">基本档案</p>
            <dl class="divide-y divide-slate-100 text-sm">
              <div class="flex justify-between py-2">
                <dt class="text-slate-500">姓名</dt>
                <dd>{{ org?.employee?.name || '—' }}</dd>
              </div>
              <div class="flex justify-between py-2">
                <dt class="text-slate-500">学号 / 工号</dt>
                <dd>{{ org?.employee?.employeeNo || org?.account || '—' }}</dd>
              </div>
              <div class="flex justify-between py-2">
                <dt class="text-slate-500">所属学校</dt>
                <dd>{{ org?.employee?.rootOrgName || '—' }}</dd>
              </div>
              <div v-if="org?.employee?.hireDate" class="flex justify-between py-2">
                <dt class="text-slate-500">入学 / 入职</dt>
                <dd>{{ org.employee.hireDate }}</dd>
              </div>
              <div class="flex justify-between py-2">
                <dt class="text-slate-500">家长关联</dt>
                <dd :class="org?.employee?.enableRelative === 1 ? 'text-emerald-600' : 'text-slate-400'">
                  {{ org?.employee?.enableRelative === 1 ? '已开通' : '未开通' }}
                </dd>
              </div>
            </dl>
          </section>

          <!-- 所属组织 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">我所在的单位</p>
            <p v-if="!org?.units?.length" class="py-3 text-center text-xs text-slate-400">暂无组织信息</p>
            <ul v-else class="space-y-2">
              <li
                v-for="u in org.units"
                :key="u.orgId"
                class="flex items-center gap-2 rounded-xl border border-slate-100 px-3 py-2"
              >
                <span class="shrink-0 rounded-md bg-slate-50 px-1.5 py-0.5 text-[10px] text-slate-500">
                  {{ ORG_TYPE[u.orgType] || '组织' }}
                </span>
                <span class="min-w-0 flex-1 truncate text-sm">{{ u.orgName }}</span>
                <span v-if="u.employeeType" class="shrink-0 text-[11px] text-slate-500">{{ u.employeeType }}</span>
                <span v-if="u.isPrimary === 1" class="shrink-0 rounded bg-brand-50 px-1.5 py-0.5 text-[10px] text-brand-700">
                  主
                </span>
              </li>
            </ul>
          </section>

          <!-- 角色 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">我的角色</p>
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="r in org?.roles || []"
                :key="r.code + (r.orgId || '')"
                class="rounded-full bg-brand-50 px-2.5 py-1 text-xs text-brand-700"
              >{{ r.name || r.code }}</span>
              <span v-if="!org?.roles?.length" class="text-xs text-slate-400">暂无角色</span>
            </div>
          </section>

          <!-- 权限 -->
          <section class="card">
            <p class="mb-2 text-sm font-medium">我能做什么（{{ org?.permissions?.length || 0 }} 项）</p>
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="p in org?.permissions || []"
                :key="p"
                class="rounded-lg bg-slate-50 px-2 py-1 text-[11px] text-slate-600"
              >{{ PERM_TEXT[p] || p }}</span>
            </div>
            <p class="mt-2 text-[11px] text-slate-400">
              权限由角色决定，页面入口也会跟着隐藏；直接改 URL 会被后端拦截。
            </p>
          </section>
        </div>
      </DataState>
    </div>
  </div>
</template>
