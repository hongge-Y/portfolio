<script setup>
import { computed, onMounted, ref } from "vue";
import { adminApi } from "@/api/admin";
import { toast } from "@/utils/toast";
const orgs = ref([]);
const classOrgId = ref(null);
const term = ref("2026-2027-1");
const json = ref(`[
  {"courseCode":"CS201","courseName":"操作系统","teacherName":"周老师","credit":4,
   "weekDay":1,"sectionStart":1,"sectionEnd":2,"location":"教学楼A101","weeks":"1-16"},
  {"courseCode":"CS202","courseName":"软件工程","teacherName":"吴老师","credit":3,
   "weekDay":3,"sectionStart":3,"sectionEnd":4,"location":"教学楼A203","weeks":"1-8,10-16"}
]`);
const result = ref(null);
const submitting = ref(false);
const classes = computed(() => orgs.value.filter((o) => o.type === 4));
async function submit() {
  if (!classOrgId.value) return toast("请选择班级", "error");
  let items;
  try {
    items = JSON.parse(json.value);
    if (!Array.isArray(items)) throw new Error("必须是数组");
  } catch (e) {
    return toast("JSON 格式错误：" + e.message, "error");
  }
  submitting.value = true;
  try {
    result.value = await adminApi.importTimetable({ term: term.value, classOrgId: classOrgId.value, items });
    toast(`导入完成：成功 ${result.value.success} 条，失败 ${result.value.failed} 条`);
  } finally {
    submitting.value = false;
  }
}
onMounted(async () => {
  orgs.value = await adminApi.orgOptions();
});
</script>

<template>
  <div class="space-y-3">
    <p class="text-xs text-slate-400">
      按学期导入课表 JSON；导入后学生端「课表」立即可见。系统不生成假数据。
    </p>
    <div class="card space-y-2">
      <p class="text-xs text-slate-500">
        覆盖式导入：同一「班级 + 学期」重复导入会先清空旧课表。教务系统导出 Excel 后转成下面的 JSON 结构即可。
      </p>
      <div class="grid grid-cols-2 gap-2">
        <select v-model.number="classOrgId" class="field">
          <option :value="null">选择班级</option>
          <option v-for="c in classes" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <input v-model="term" class="field" placeholder="学期，如 2026-2027-1" />
      </div>
      <textarea v-model="json" rows="10" class="field !h-auto py-2 font-mono text-xs" />
      <button class="btn-primary" :disabled="submitting" @click="submit">
        {{ submitting ? '导入中…' : '开始导入' }}
      </button>
      <p v-if="result" class="text-xs text-emerald-600">
        成功 {{ result.success }} 条，失败 {{ result.failed }} 条（班级 {{ result.classOrgId }} / {{ result.term }}）
      </p>
    </div>
    <div class="card text-xs text-slate-500 space-y-1">
      <p class="font-medium text-slate-600">字段说明</p>
      <p>courseCode 课程号（同课程号同班级会复用课程记录）· courseName 课程名 · teacherName 教师</p>
      <p>weekDay 星期（1-7）· sectionStart / sectionEnd 第几节 · weeks 周次（1-16 或 1-8,10-16）· location 地点</p>
    </div>
  </div>
</template>
