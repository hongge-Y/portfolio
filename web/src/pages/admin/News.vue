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
const saving = ref(false);
const form = reactive({
  id: null,
  title: "",
  subTitle: "",
  newsType: 1,
  visibleOrgId: 1,
  startTime: "",
  endTime: "",
  status: 1,
  content: ""
});
const newsTypes = [
  { v: 1, t: "通知公告" },
  { v: 2, t: "校园活动" },
  { v: 3, t: "学院动态" },
  { v: 4, t: "其他" }
];
async function load() {
  const res = await adminApi.newsSearch({ pageNum: 1, pageSize: 50, searchModel: {} });
  list.value = res.list;
}
function openNew() {
  Object.assign(form, {
    id: null,
    title: "",
    subTitle: "",
    newsType: 1,
    visibleOrgId: 1,
    startTime: "",
    endTime: "",
    status: 1,
    content: ""
  });
  showForm.value = true;
}
async function edit(row) {
  const d = await adminApi.newsDetail(row.id);
  Object.assign(form, {
    id: row.id,
    title: d.title,
    subTitle: d.subTitle || "",
    newsType: d.newsType,
    visibleOrgId: d.visibleOrgId || 1,
    startTime: (d.startTime || "").replace(" ", "T").slice(0, 16),
    endTime: (d.endTime || "").replace(" ", "T").slice(0, 16),
    status: d.status,
    content: d.content || ""
  });
  showForm.value = true;
}
async function save() {
  if (!form.title.trim()) return toast("标题不能为空", "error");
  saving.value = true;
  try {
    await adminApi.saveNews({
      ...form,
      startTime: toApiDateTime(form.startTime),
      endTime: toApiDateTime(form.endTime)
    });
    toast("已保存");
    showForm.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  const ok = await confirm({ content: `删除「${row.title}」？` })
  if (!ok) return
  await adminApi.deleteNews(row.id);
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
      <h1 class="text-lg font-semibold">新闻管理</h1>
      <button class="px-3 h-9 rounded-lg bg-brand-500 text-white text-sm" @click="openNew">+ 新增</button>
    </div>

    <div v-if="showForm" class="card space-y-2">
      <p class="text-sm font-medium">{{ form.id ? '编辑新闻' : '新增新闻' }}</p>
      <input v-model="form.title" class="field" placeholder="标题" />
      <input v-model="form.subTitle" class="field" placeholder="副标题（选填）" />
      <div class="grid grid-cols-2 gap-2">
        <select v-model.number="form.newsType" class="field">
          <option v-for="t in newsTypes" :key="t.v" :value="t.v">{{ t.t }}</option>
        </select>
        <select v-model.number="form.visibleOrgId" class="field">
          <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}（{{ o.type }}）</option>
        </select>
        <input v-model="form.startTime" type="datetime-local" class="field" />
        <input v-model="form.endTime" type="datetime-local" class="field" />
        <select v-model.number="form.status" class="field">
          <option :value="0">草稿</option>
          <option :value="1">正式</option>
        </select>
      </div>
      <textarea v-model="form.content" rows="4" class="field !h-auto py-2" placeholder="正文（支持 HTML）" />
      <div class="flex gap-2">
        <button class="flex-1 h-10 rounded-xl border border-slate-200 text-sm" @click="showForm = false">取消</button>
        <button class="flex-1 btn-primary !h-10" :disabled="saving" @click="save">保存</button>
      </div>
    </div>

    <div class="card overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="text-left text-xs text-slate-400 border-b border-slate-100">
          <tr><th class="py-2">标题</th><th>类型</th><th>状态</th><th>浏览</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id" class="border-b border-slate-50">
            <td class="py-2 max-w-[220px] truncate">{{ r.title }}</td>
            <td class="text-slate-500">{{ ['', '通知', '活动', '院动态', '其他'][r.newsType] }}</td>
            <td>
              <span :class="r.status === 1 ? 'text-emerald-600' : 'text-slate-400'">
                {{ r.status === 1 ? '正式' : '草稿' }}
              </span>
            </td>
            <td class="text-slate-400">{{ r.viewCount }}</td>
            <td class="text-right whitespace-nowrap">
              <button class="text-xs text-brand-600 mr-2" @click="edit(r)">编辑</button>
              <button class="text-xs text-rose-500" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="5" class="py-6 text-center text-slate-400">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
