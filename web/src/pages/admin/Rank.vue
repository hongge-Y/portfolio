<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { computed, onMounted, reactive, ref } from "vue";
import { adminApi } from "@/api/admin";
import { toApiDateTime } from "@/utils/format";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const list = ref([]);
const labels = ref([]);
const showForm = ref(false);
const lookupResult = ref(null);
const form = reactive({
  employeeNo: "",
  employeeId: null,
  title: "",
  subTitle: "",
  red: true,
  labelId: null,
  type: 1,
  achievement: "",
  updateTime: "",
  status: 1,
  relativeVisible: true,
  content: ""
});
const currentLabels = computed(() => labels.value.filter((l) => !!l.red === form.red));
async function load() {
  list.value = (await adminApi.rankSearch({ pageNum: 1, pageSize: 50 })).list;
}
async function lookup() {
  if (!form.employeeNo.trim()) return toast("请填学号", "error");
  try {
    lookupResult.value = await adminApi.employeeLookup(form.employeeNo.trim());
    form.employeeId = lookupResult.value.employeeId;
    toast(`找到：${lookupResult.value.name}（${lookupResult.value.className}）`);
  } catch {
    lookupResult.value = null;
    form.employeeId = null;
  }
}
function openNew() {
  Object.assign(form, {
    employeeNo: "",
    employeeId: null,
    title: "",
    subTitle: "",
    red: true,
    labelId: null,
    type: 1,
    achievement: "",
    updateTime: "",
    status: 1,
    relativeVisible: true,
    content: ""
  });
  lookupResult.value = null;
  showForm.value = true;
}
async function save() {
  if (!form.employeeId) return toast("请先按学号查到学生", "error");
  if (!form.title.trim()) return toast("标题不能为空", "error");
  await adminApi.saveRank({
    employeeId: form.employeeId,
    title: form.title,
    subTitle: form.subTitle,
    red: form.red,
    labelId: form.labelId,
    type: form.type,
    achievement: form.achievement,
    updateTime: toApiDateTime(form.updateTime),
    status: form.status,
    relativeVisible: form.relativeVisible,
    content: form.content
  });
  toast("已保存");
  showForm.value = false;
  await load();
}
async function remove(row) {
  const ok = await confirm({ content: `删除「${row.title}」？` })
  if (!ok) return
  await adminApi.deleteRank(row.id);
  toast("已删除");
  await load();
}
onMounted(async () => {
  labels.value = await adminApi.rankLabels();
  await load();
});
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <h1 class="text-lg font-semibold">红黑榜管理</h1>
      <button class="px-3 h-9 rounded-lg bg-brand-500 text-white text-sm" @click="openNew">+ 新增</button>
    </div>

    <div v-if="showForm" class="card space-y-2">
      <div class="flex gap-2">
        <input v-model="form.employeeNo" class="field" placeholder="学生学号" />
        <button class="px-3 h-11 rounded-xl border border-slate-200 text-sm shrink-0" @click="lookup">查学生</button>
      </div>
      <p v-if="lookupResult" class="text-xs text-emerald-600">
        {{ lookupResult.name }} · {{ lookupResult.className }} · employeeId={{ lookupResult.employeeId }}
      </p>
      <div class="flex gap-2">
        <button
          class="flex-1 h-9 rounded-lg text-sm"
          :class="form.red ? 'bg-rose-500 text-white' : 'bg-slate-100 text-slate-600'"
          @click="form.red = true; form.labelId = null"
        >红榜</button>
        <button
          class="flex-1 h-9 rounded-lg text-sm"
          :class="!form.red ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'"
          @click="form.red = false; form.labelId = null"
        >黑榜</button>
      </div>
      <input v-model="form.title" class="field" placeholder="标题，如 程序设计大赛校赛一等奖" />
      <input v-model="form.subTitle" class="field" placeholder="副标题（选填）" />
      <div class="grid grid-cols-2 gap-2">
        <select v-model.number="form.labelId" class="field">
          <option :value="null">选择标签</option>
          <option v-for="l in currentLabels" :key="l.id" :value="l.id">{{ l.name }}</option>
        </select>
        <input v-model="form.updateTime" type="datetime-local" class="field" />
      </div>
      <textarea v-model="form.achievement" rows="2" class="field !h-auto py-2" placeholder="事迹描述" />
      <label class="flex items-center gap-2 text-xs text-slate-600">
        <input v-model="form.relativeVisible" type="checkbox" /> 允许关联家长查看（黑榜建议按需开启）
      </label>
      <div class="flex gap-2">
        <button class="flex-1 h-10 rounded-xl border border-slate-200 text-sm" @click="showForm = false">取消</button>
        <button class="flex-1 btn-primary !h-10" @click="save">保存</button>
      </div>
    </div>

    <div class="card overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="text-left text-xs text-slate-400 border-b border-slate-100">
          <tr><th class="py-2">标题</th><th>学生</th><th>类型</th><th>家长可见</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id" class="border-b border-slate-50">
            <td class="py-2 max-w-[200px] truncate">{{ r.title }}</td>
            <td class="text-slate-500">{{ r.employeeName }}</td>
            <td>
              <span :class="r.red ? 'text-rose-600' : 'text-slate-700'">{{ r.red ? '红榜' : '黑榜' }}</span>
              <span class="text-xs text-slate-400 ml-1">{{ r.labelName }}</span>
            </td>
            <td class="text-xs">{{ r.relativeVisible ? '是' : '否' }}</td>
            <td class="text-right"><button class="text-xs text-rose-500" @click="remove(r)">删除</button></td>
          </tr>
          <tr v-if="!list.length"><td colspan="5" class="py-6 text-center text-slate-400">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
