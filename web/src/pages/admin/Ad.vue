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
  id: null,
  title: "",
  subTitle: "",
  image: "/static/ad/placeholder.jpg",
  linkType: 1,
  link: "",
  startTime: "",
  endTime: "",
  status: 1,
  visibleOrgId: 1,
  sort: 0
});
async function load() {
  list.value = (await adminApi.adSearch({ pageNum: 1, pageSize: 50 })).list;
}
function openNew() {
  Object.assign(form, {
    id: null,
    title: "",
    subTitle: "",
    image: "/static/ad/placeholder.jpg",
    linkType: 1,
    link: "",
    startTime: "",
    endTime: "",
    status: 1,
    visibleOrgId: 1,
    sort: 0
  });
  showForm.value = true;
}
async function save() {
  if (!form.title.trim()) return toast("标题不能为空", "error");
  await adminApi.saveAd({ ...form, startTime: toApiDateTime(form.startTime), endTime: toApiDateTime(form.endTime) });
  toast("已保存");
  showForm.value = false;
  await load();
}
async function remove(row) {
  const ok = await confirm({ content: `删除广告「${row.title}」？` })
  if (!ok) return
  await adminApi.deleteAd(row.id);
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
      <h1 class="text-lg font-semibold">广告管理</h1>
      <button class="px-3 h-9 rounded-lg bg-brand-500 text-white text-sm" @click="openNew">+ 新增</button>
    </div>

    <div v-if="showForm" class="card space-y-2">
      <input v-model="form.title" class="field" placeholder="标题" />
      <input v-model="form.subTitle" class="field" placeholder="副标题（选填）" />
      <input v-model="form.image" class="field" placeholder="图片地址，如 /static/ad/1.jpg" />
      <div class="grid grid-cols-2 gap-2">
        <select v-model.number="form.linkType" class="field">
          <option :value="1">站内链接</option>
          <option :value="2">站外链接</option>
        </select>
        <input v-model="form.link" class="field" placeholder="跳转地址" />
        <input v-model="form.startTime" type="datetime-local" class="field" />
        <input v-model="form.endTime" type="datetime-local" class="field" />
        <select v-model.number="form.visibleOrgId" class="field">
          <option v-for="o in orgs" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
        <input v-model.number="form.sort" type="number" class="field" placeholder="排序" />
      </div>
      <div class="flex gap-2">
        <button class="flex-1 h-10 rounded-xl border border-slate-200 text-sm" @click="showForm = false">取消</button>
        <button class="flex-1 btn-primary !h-10" @click="save">保存</button>
      </div>
    </div>

    <div class="card overflow-x-auto">
      <table class="w-full text-sm">
        <thead class="text-left text-xs text-slate-400 border-b border-slate-100">
          <tr><th class="py-2">标题</th><th>可见组织</th><th>时间</th><th class="text-right">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.id" class="border-b border-slate-50">
            <td class="py-2">{{ r.title }}</td>
            <td class="text-slate-500">{{ orgs.find(o => o.id === r.visibleOrgId)?.name || r.visibleOrgId }}</td>
            <td class="text-xs text-slate-400">{{ (r.startTime || '').slice(0, 10) }} ~ {{ (r.endTime || '').slice(0, 10) }}</td>
            <td class="text-right"><button class="text-xs text-rose-500" @click="remove(r)">删除</button></td>
          </tr>
          <tr v-if="!list.length"><td colspan="4" class="py-6 text-center text-slate-400">暂无数据</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
