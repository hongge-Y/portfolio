<script setup>
import { useConfirm } from '@/composables/useConfirm'
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/stores/user";
import { relativeApi } from "@/api/relative";
import { toast } from "@/utils/toast";

const { confirm } = useConfirm()
const router = useRouter();
const store = useUserStore();
const list = ref([]);
const children = ref([]);
const employeeNo = ref("");
const parentMobile = ref("");
const loading = ref(true);
const submitting = ref(false);
async function load() {
  loading.value = true;
  try {
    const [res, kids] = await Promise.all([relativeApi.search(), relativeApi.children().catch(() => [])]);
    list.value = res.list;
    children.value = kids;
  } finally {
    loading.value = false;
  }
}
async function apply() {
  submitting.value = true;
  try {
    if (store.isParent) {
      if (!employeeNo.value.trim()) return toast("请填写学生学号", "error");
      await relativeApi.apply(employeeNo.value.trim());
      toast("申请已发出，等待学生确认");
      employeeNo.value = "";
    } else {
      if (!parentMobile.value.trim()) return toast("请填写家长手机号", "error");
      await relativeApi.applyByStudent(parentMobile.value.trim());
      toast("申请已发出，等待家长确认");
      parentMobile.value = "";
    }
    load();
  } finally {
    submitting.value = false;
  }
}
async function agree(item) {
  await relativeApi.agree(item.id);
  toast("已同意，关联已建立");
  load();
}
async function reject(item) {
  const ok = await confirm({ content: "确认拒绝该关联申请？" })
  if (!ok) return
  await relativeApi.reject(item.id);
  toast("已拒绝");
  load();
}
async function unbind(item) {
  const ok = await confirm({ content: "确认解除关联？解除后将看不到对方成长记录。" })
  if (!ok) return
  await relativeApi.unbind(item.id);
  toast("已解除关联");
  load();
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-4">
      <!-- 申请入口 -->
      <section class="card space-y-2">
        <h2 class="font-medium text-sm">
          {{ store.isParent ? '申请关联我的孩子' : '邀请家长关联我' }}
        </h2>
        <div v-if="store.isParent" class="flex gap-2">
          <input v-model="employeeNo" class="field" placeholder="学生学号，如 2025000001" />
          <button class="btn-primary !w-24 shrink-0" :disabled="submitting" @click="apply">申请</button>
        </div>
        <div v-else class="flex gap-2">
          <input v-model="parentMobile" class="field" placeholder="家长手机号（需已注册）" />
          <button class="btn-primary !w-24 shrink-0" :disabled="submitting" @click="apply">申请</button>
        </div>
        <p class="text-xs text-slate-400">
          关联由双方自行确认，不经过学校工作人员。一个家长可关联多个学生，一个学生也可被多位家长关联。
        </p>
      </section>

      <!-- 已关联 -->
      <section v-if="children.length" class="card">
        <h3 class="font-medium text-sm mb-2">已关联的孩子</h3>
        <ul class="space-y-2">
          <li v-for="c in children" :key="c.linkId" class="flex items-center justify-between text-sm">
            <span>{{ c.name }} <span class="text-xs text-slate-400">{{ c.employeeNo }} · {{ c.className }}</span></span>
            <button class="text-xs text-slate-400" @click="router.push('/rank')">看成长档案 ›</button>
          </li>
        </ul>
      </section>

      <!-- 申请列表 -->
      <section class="space-y-2">
        <h3 class="font-medium text-sm text-slate-600">申请记录</h3>
        <p v-if="loading" class="card text-center text-sm text-slate-400 py-6">加载中…</p>
        <p v-else-if="!list.length" class="card text-center text-sm text-slate-400 py-8">暂无关联申请</p>

        <article v-for="item in list" :key="item.id" class="card">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0">
              <p class="text-sm font-medium">
                {{ item.applyFrom === 'PARENT' ? item.parentName : item.studentName }}
                <span class="text-xs text-slate-400 font-normal">
                  {{ item.applyFrom === 'PARENT' ? '申请关联' : '邀请家长关联' }}
                </span>
                {{ item.applyFrom === 'PARENT' ? item.studentName : item.parentName }}
              </p>
              <p class="text-xs text-slate-400 mt-0.5">
                {{ item.className }} · {{ (item.applyTime || '').slice(0, 16) }}
              </p>
              <p v-if="item.remark" class="text-xs text-slate-500 mt-1">备注：{{ item.remark }}</p>
            </div>
            <span
              class="shrink-0 text-[10px] px-1.5 py-0.5 rounded"
              :class="item.status === 1 ? 'bg-emerald-50 text-emerald-700'
                : item.status === 0 ? 'bg-amber-50 text-amber-700'
                : item.status === 2 ? 'bg-rose-50 text-rose-600' : 'bg-slate-100 text-slate-500'"
            >{{ item.statusText }}</span>
          </div>

          <div class="mt-3 flex justify-end gap-2">
            <template v-if="item.status === 0 && !item.mine">
              <button class="px-3 h-8 rounded-lg text-xs border border-slate-200" @click="reject(item)">拒绝</button>
              <button class="px-3 h-8 rounded-lg text-xs bg-brand-500 text-white" @click="agree(item)">同意</button>
            </template>
            <span v-else-if="item.status === 0 && item.mine" class="text-xs text-slate-400">等待对方确认</span>
            <button
              v-else-if="item.status === 1"
              class="px-3 h-8 rounded-lg text-xs border border-rose-200 text-rose-600"
              @click="unbind(item)"
            >解除关联</button>
          </div>
        </article>
      </section>
    </div>
  </div>
</template>
