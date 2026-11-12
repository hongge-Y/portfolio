<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { eduApi } from "@/api/edu";
import { toast } from "@/utils/toast";
const router = useRouter();
const list = ref([]);
const loading = ref(true);
const activeId = ref(null);
const submitting = ref(false);
const form = reactive({ scoreTeach: 5, scoreContent: 5, scoreAtmos: 5, content: "", anonymous: true });
const dims = [
  { key: "scoreTeach", label: "教学水平" },
  { key: "scoreContent", label: "内容质量" },
  { key: "scoreAtmos", label: "课堂氛围" }
];
async function load() {
  loading.value = true;
  try {
    list.value = await eduApi.evalOptions();
  } finally {
    loading.value = false;
  }
}
function open(c) {
  activeId.value = c.courseId;
  form.scoreTeach = 5;
  form.scoreContent = 5;
  form.scoreAtmos = 5;
  form.content = c.myContent || "";
  form.anonymous = true;
}
async function submit() {
  if (activeId.value == null) return;
  submitting.value = true;
  try {
    await eduApi.submitEval({
      courseId: activeId.value,
      scoreTeach: form.scoreTeach,
      scoreContent: form.scoreContent,
      scoreAtmos: form.scoreAtmos,
      content: form.content,
      anonymous: form.anonymous
    });
    toast("评价已提交");
    activeId.value = null;
    await load();
  } finally {
    submitting.value = false;
  }
}
onMounted(load);
</script>

<template>
  <div>

    <div class="space-y-3">
      <p v-if="loading" class="card text-center text-sm text-slate-400 py-8">加载中…</p>
      <p v-else-if="!list.length" class="card text-center text-sm text-slate-400 py-10">本学期没有可评价的课程</p>

      <article v-for="c in list" :key="c.courseId" class="card">
        <div class="flex items-start justify-between gap-2">
          <div>
            <p class="font-medium">{{ c.courseName }}</p>
            <p class="text-xs text-slate-400 mt-0.5">{{ c.teacherName }} · {{ c.credit }} 学分</p>
          </div>
          <span
            class="shrink-0 text-[10px] px-1.5 py-0.5 rounded"
            :class="c.evaluated ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'"
          >{{ c.evaluated ? '已评价 ' + (c.scoreTotal ?? '') : '未评价' }}</span>
        </div>

        <button
          class="mt-2 text-xs text-brand-600"
          @click="open(c)"
        >{{ c.evaluated ? '修改评价' : '去评价' }} ›</button>

        <div v-if="activeId === c.courseId" class="mt-3 pt-3 border-t border-slate-100 space-y-3">
          <div v-for="d in dims" :key="d.key" class="flex items-center justify-between">
            <span class="text-sm text-slate-600">{{ d.label }}</span>
            <div class="flex gap-1">
              <button
                v-for="n in 5"
                :key="n"
                class="w-8 h-8 rounded-lg text-sm"
                :class="form[d.key] >= n ? 'bg-amber-400 text-white' : 'bg-slate-100 text-slate-400'"
                @click="form[d.key] = n"
              >{{ n }}</button>
            </div>
          </div>
          <textarea v-model="form.content" rows="3" class="field !h-auto py-2" placeholder="想对老师说的话（选填）" />
          <label class="flex items-center gap-2 text-xs text-slate-500">
            <input v-model="form.anonymous" type="checkbox" /> 匿名提交（教师端只看到聚合结果）
          </label>
          <div class="flex gap-2">
            <button class="flex-1 h-10 rounded-xl border border-slate-200 text-slate-600 text-sm" @click="activeId = null">取消</button>
            <button class="flex-1 btn-primary !h-10" :disabled="submitting" @click="submit">
              {{ submitting ? '提交中…' : '提交评价' }}
            </button>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>
