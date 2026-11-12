<script setup>
/**
 * 账号资料：昵称、生日、性别、小宠。
 * 对应 /v1/profile/me（GET 读 / PUT 改），后端只更新提交的字段。
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import DataState from '@/components/common/DataState.vue'
import { profileApi } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import { toast } from '@/utils/toast'

const router = useRouter()
const store = useUserStore()

const status = ref('loading')
const errorText = ref('')
const saving = ref(false)

const form = ref({ showName: '', nickName: '', birthday: '', gender: 0, pet: 0, petHide: 0 })
const account = ref('')

const GENDERS = [
  { v: 0, t: '未设置' },
  { v: 1, t: '男' },
  { v: 2, t: '女' }
]

/** 小宠 0 表示未选；1-5 对应参考版里的五种 */
const PETS = [
  { v: 0, t: '未选择', emoji: '·' },
  { v: 1, t: '星光猫', emoji: '🐱' },
  { v: 2, t: '云朵犬', emoji: '🐶' },
  { v: 3, t: '萤火兔', emoji: '🐰' },
  { v: 4, t: '树莓熊', emoji: '🐻' },
  { v: 5, t: '月影狐', emoji: '🦊' }
]

async function load() {
  status.value = 'loading'
  try {
    const m = await profileApi.me()
    account.value = m.account
    form.value = {
      showName: m.showName || '',
      nickName: m.nickName || '',
      birthday: (m.birthday || '').slice(0, 10),
      gender: m.gender ?? 0,
      pet: m.pet ?? 0,
      petHide: m.petHide ?? 0
    }
    status.value = 'ready'
  } catch (e) {
    status.value = 'error'
    errorText.value = e?.message || '加载失败'
  }
}

async function save() {
  if (!form.value.showName.trim()) return toast('昵称不能为空', 'error')
  saving.value = true
  try {
    const body = {
      showName: form.value.showName.trim(),
      nickName: form.value.nickName.trim(),
      birthday: form.value.birthday || null,
      gender: form.value.gender,
      pet: form.value.pet,
      petHide: form.value.petHide
    }
    await profileApi.update(body)
    // 首页问候语用的是 store 里的名字，改完顺手刷一下
    await store.fetchMe().catch(() => {})
    toast('已保存')
    router.back()
  } catch (e) {
    toast(e?.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div>
      <DataState :status="status" :error-text="errorText" :rows="5" @retry="load">
        <div class="space-y-4">
          <p v-if="account" class="text-[11px] text-slate-400">当前账号 {{ account }}（账号本身不可修改）</p>
          <section class="card space-y-3">
            <label class="block">
              <span class="text-xs text-slate-500">昵称（展示名）</span>
              <input v-model="form.showName" class="field mt-1" maxlength="20" placeholder="请输入昵称" />
            </label>
            <label class="block">
              <span class="text-xs text-slate-500">备注名</span>
              <input v-model="form.nickName" class="field mt-1" maxlength="20" placeholder="可选" />
            </label>
            <label class="block">
              <span class="text-xs text-slate-500">生日</span>
              <input v-model="form.birthday" type="date" class="field mt-1" />
            </label>
            <div>
              <span class="text-xs text-slate-500">性别</span>
              <div class="mt-1 flex gap-1.5">
                <button
                  v-for="g in GENDERS"
                  :key="g.v"
                  class="flex-1 rounded-lg py-2 text-sm transition"
                  :class="form.gender === g.v ? 'bg-brand-500 text-white' : 'bg-slate-50 text-slate-600'"
                  @click="form.gender = g.v"
                >{{ g.t }}</button>
              </div>
            </div>
          </section>

          <section class="card">
            <div class="mb-2 flex items-center justify-between">
              <p class="text-sm font-medium">我的小宠</p>
              <label class="flex items-center gap-1.5 text-xs text-slate-500">
                <input v-model="form.petHide" type="checkbox" :true-value="1" :false-value="0" />
                对他人隐藏
              </label>
            </div>
            <div class="grid grid-cols-3 gap-2 sm:grid-cols-5">
              <button
                v-for="p in PETS"
                :key="p.v"
                class="rounded-xl border py-2 text-center transition"
                :class="form.pet === p.v ? 'border-brand-500 bg-brand-50' : 'border-slate-100'"
                @click="form.pet = p.v"
              >
                <div class="text-lg">{{ p.emoji }}</div>
                <div class="mt-0.5 text-[11px] text-slate-500">{{ p.t }}</div>
              </button>
            </div>
            <p class="mt-2 text-[11px] text-slate-400">小宠只做趣味展示，不影响任何业务数据。</p>
          </section>

          <!-- 保存：桌面跟在内容后，移动端吸在底部拇指区 -->
          <div class="sticky bottom-0 -mx-4 border-t border-slate-200/70 bg-white/95 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
            <button
              type="button"
              class="btn-solid w-full sm:w-auto"
              :disabled="saving || status !== 'ready'"
              @click="save"
            >
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
          </div>
        </div>
      </DataState>
    </div>
  </div>
</template>
