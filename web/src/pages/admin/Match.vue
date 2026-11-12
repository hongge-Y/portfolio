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
const form = reactive({
  title: "",
  subTitle: "",
  coverImage: "",
  visibleOrgId: 1,
  startShowTime: "",
  startRegisterTime: "",
  endRegisterTime: "",
  matchTime: "",
  groupCount: 1,
  status: 1,
  content: ""
});
async function load() {
  list.value = (await adminApi.matchSearch({ pageNum: 1, pageSize: 50 })).list;
}
function openNew() {
  Object.assign(form, {
    title: "",
    subTitle: "",
    coverImage: "",
    visibleOrgId: 1,
    startShowTime: "",
    startRegisterTime: "",
    endRegisterTime: "",
    matchTime: "",
    groupCount: 1,
    status: 1,
    content: ""
  });
  showForm.value = true;
}
async function save() {
  if (!form.title.trim()) return toast("标题不能为空", "error");
  await adminApi.saveMatch({
    ...form,
    startShowTime: toApiDateTime(form.startShowTime),
    startRegisterTime: toApiDateTime(form.startRegisterTime),
    endRegisterTime: toApiDateTime(form.endRegisterTime),
    matchTime: toApiDateTime(form.matchTime)
  });
  toast("已保存");
  showForm.value = false;
  await load();
}
async function remove(row) {
  const ok = await confirm({ content: `删除赛事「${row.title}」？` })
  if (!ok) return
  await adminApi.deleteMatch(row.id);
  toast("已删除");
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
      <h1 class="text-lg font-semibold">赛事管理</h1>
      <button class="px-3 h-9 rounded-lg bg-brand-500 text-white text-sm" @click="openNew">+ 新增</button>
    </div>

    <div v-if="showForm" class="card space-y-2">
      <input v-model="form.title" class="field" placeholder="赛事标题" />
      <input v-model="form.subTitle" class="field" placeholder="副标题" />
      <div class="grid grid-cols-2 gap-2">
        <label class="text-xs text-slate-500 col-span-2">展示时间</label>
        <input v-model="form.startShowTime" type="datetime-local" class="field" />
        <input v-model="form.visibleOrgId" class="field" placeholder="可见组织 ID" />
        <label class="text-xs text-slate-500 col-span-2">报名时间</label>
        <input v-model="form.startRegisterTime" type="datetime-local" class="field" />
        <input v-model="form.endRegisterTime" type="datetime-local" class="field" />
        <label class="text-xs text-slate-500 col-span-2">比赛时间</label>
        <input v-model="form.matchTime" type="datetime-local" class="field" />
        <input v-model.number="form.groupCount" type="number" class="field" placeholder="组队人数" />
      </div>
      <textarea v-model="form.content" rows="3" class="field !h-auto py-2" placeholder="赛事介绍（支持 HTML）" />
      <div class="flex gap-2">
        <button class="flex-1 h-10 rounded-xl border border-slate-200 text-sm" @click="showForm = false">取消</button>
        <button class="flex-1 btn-primary !h-10" @click="save">保存</button>
      </div>
    </div>

    <div class="card overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="text-left text-xs text-slate-400 border-b border-slate-100">
          <tr><th class="py-2">标题</th><th>报名截止</th><th>组队</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id" class="border-b border-slate-50">
            <td class="py-2">{{ r.title }}</td>
            <td class="text-xs text-slate-400">{{ (r.endRegisterTime || '').slice(0, 10) }}</td>
            <td class="text-slate-500">{{ r.groupCount }} 人</td>
            <td class="text-right"><button class="text-xs text-rose-500" @click="remove(r)">删除</button></td>
          </tr>
          <tr v-if="!list.length"><td colspan="4" class="py-6 text-center text-slate-400">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
