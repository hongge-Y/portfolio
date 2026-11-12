<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { onMounted, reactive, ref } from "vue";
import { adminApi } from "@/api/admin";
import { toApiDateTime } from "@/utils/format";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const list = ref([]);
const orgs = ref([]);
const showForm = ref(false);
const ranking = ref(null);
const form = reactive({
  name: "",
  orgId: 2,
  term: "2026-2027-1",
  quota: 3,
  amount: 5e3,
  applyStart: "",
  applyEnd: "",
  status: 1
});
async function load() {
  list.value = (await adminApi.planSearch({ pageNum: 1, pageSize: 50 })).list;
}
function openNew() {
  Object.assign(form, {
    name: "",
    orgId: 2,
    term: "2026-2027-1",
    quota: 3,
    amount: 5e3,
    applyStart: "",
    applyEnd: "",
    status: 1
  });
  showForm.value = true;
}
async function save() {
  if (!form.name.trim()) return toast("名称不能为空", "error");
  await adminApi.savePlan({
    ...form,
    applyStart: toApiDateTime(form.applyStart),
    applyEnd: toApiDateTime(form.applyEnd),
    ruleJson: '{"gpaWeight":6,"redWeight":2,"blackWeight":3}'
  });
  toast("已保存");
  showForm.value = false;
  await load();
}
async function rank(p) {
  const ok = await confirm({ content: `按综合分生成「${p.name}」排名？（名额 ${p.quota}）` })
  if (!ok) return
  ranking.value = await adminApi.rankPlan(p.id);
  toast(`已生成 ${ranking.value.list.length} 人排名`);
  await load();
}
async function publicity(p) {
  const ok = await confirm({ content: "发起公示？公示期内学生可查看名单。" })
  if (!ok) return
  const res = await adminApi.publicityPlan(p.id);
  toast(`已公示 ${res.winners.length} 人`);
  await load();
}
async function grant(p) {
  const ok = await confirm({ content: "确认发放？发放后不可撤回。" })
  if (!ok) return
  const res = await adminApi.grantPlan(p.id);
  toast(`已发放 ${res.granted} 人`);
  await load();
}
onMounted(async () => {
  orgs.value = await adminApi.orgOptions();
  await load();
});
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold">奖学金管理</h1>
      <button class="px-3 h-9 rounded-lg bg-brand-500 text-white text-sm" @click="openNew">+ 新建计划</button>
    </div>

    <div v-if="showForm" class="card space-y-2">
      <input v-model="form.name" class="field" placeholder="计划名称" />
      <div class="grid grid-cols-2 gap-2">
        <select v-model.number="form.orgId" class="field">
          <option v-for="o in orgs.filter(x => x.type <= 3)" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
        <input v-model="form.term" class="field" placeholder="学期" />
        <input v-model.number="form.quota" type="number" class="field" placeholder="名额" />
        <input v-model.number="form.amount" type="number" class="field" placeholder="金额" />
        <input v-model="form.applyStart" type="datetime-local" class="field" />
        <input v-model="form.applyEnd" type="datetime-local" class="field" />
        <select v-model.number="form.status" class="field">
          <option :value="0">草稿</option>
          <option :value="1">报名中</option>
        </select>
      </div>
      <p class="text-[11px] text-slate-400">计分规则默认 GPA×6 + 红榜×2 − 黑榜×3，可在数据库 `sch_plan.rule_json` 调整。</p>
      <div class="flex gap-2">
        <button class="flex-1 h-10 rounded-xl border border-slate-200 text-sm" @click="showForm = false">取消</button>
        <button class="flex-1 btn-primary !h-10" @click="save">保存</button>
      </div>
    </div>

    <div class="card overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="text-left text-xs text-slate-400 border-b border-slate-100">
          <tr><th class="py-2">计划</th><th>名额/金额</th><th>状态</th><th>申请数</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.id" class="border-b border-slate-50">
            <td class="py-2 max-w-[200px] truncate">{{ p.name }}</td>
            <td class="text-slate-500">{{ p.quota }} 人 / {{ p.amount }} 元</td>
            <td class="text-xs">{{ p.statusText }}</td>
            <td class="text-slate-400">{{ p.applyCount }}</td>
            <td class="text-right whitespace-nowrap">
              <button class="text-xs text-brand-600 mr-2" @click="rank(p)">排名</button>
              <button class="text-xs text-brand-600 mr-2" @click="publicity(p)">公示</button>
              <button class="text-xs text-rose-500" @click="grant(p)">发放</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="5" class="py-6 text-center text-slate-400">暂无计划</td></tr>
        </tbody>
      </table>
    </div>

    <div v-if="ranking" class="card">
      <div class="flex items-center justify-between mb-2">
        <p class="text-sm font-medium">排名结果（名额 {{ ranking.quota }}）</p>
        <button class="text-xs text-slate-400" @click="ranking = null">收起</button>
      </div>
      <ul class="divide-y divide-slate-100 text-sm">
        <li v-for="r in ranking.list" :key="r.applyId" class="py-1.5 flex items-center justify-between">
          <span>第 {{ r.rankNo }} 名 · {{ r.employeeName }} <span class="text-xs text-slate-400">{{ r.employeeNo }}</span></span>
          <span :class="r.inQuota ? 'text-emerald-600' : 'text-slate-400'">
            {{ r.totalScore }}{{ r.inQuota ? ' · 名额内' : '' }}
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>
