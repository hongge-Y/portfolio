<script setup>
/**
 * 新手引导 ①选小宠（套 BlankLayout：整屏自绘，深色与登录页同源）
 *
 * 交互：横向 snap 卡片列表 → 点选 → 底部确认。
 * 确认时一次性做三件事：POST /v1/profile/guide（后端标记 guide_done 并 +100 光粒 / +1 礼盒）、
 * 刷新 store 里的用户上下文（首页问候/身份卡立刻生效）、把前后快照交给礼盒页。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { profileApi } from '@/api/profile'
import { useUserStore } from '@/stores/user'
import { useOnboarding } from '@/composables/useOnboarding'
import { PETS } from '@/features/onboarding/pets'
import { toast } from '@/utils/toast'
import { humanMessage } from '@/utils/crypto'

const router = useRouter()
const store = useUserStore()
const { complete } = useOnboarding()

const selected = ref(0)
const submitting = ref(false)
const loaded = ref(false)
const me = ref(null)

const canSubmit = computed(() => selected.value > 0 && !submitting.value)

onMounted(async () => {
  // 已走过引导的用户（比如从收藏夹直接打开本页）就别再走一遍
  try {
    me.value = await profileApi.me()
    if (me.value?.pet > 0) selected.value = me.value.pet
    if (Number(me.value?.guideDone)) {
      router.replace('/')
      return
    }
  } catch {
    /* 拉不到资料也让页面可用，确认时会报真正的错 */
  }
  loaded.value = true
})

async function confirm() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const before = me.value || (await profileApi.me().catch(() => null))
    await profileApi.guide(selected.value)
    // 强制刷新 store 里的资料缓存：既拿到礼盒页要显示的真实增量，
    // 也让路由守卫不再认为"还没引导"（否则一导航就被弹回本页）
    const after = await store.fetchProfile(true).catch(() => null)
    complete({ pet: selected.value, before, after })
    // 首页/我的页读的是 store.me，这里刷一次，避免进去还显示"未选小宠"
    await store.fetchMe().catch(() => {})
    router.replace('/guide/gift')
  } catch (e) {
    toast(humanMessage(e?.message, '选择失败，请重试'), 'error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="guide-stage">
    <!-- 装饰光斑：跟着容器裁掉，不参与布局 -->
    <span class="glow glow-a" aria-hidden="true" />
    <span class="glow glow-b" aria-hidden="true" />

    <div class="relative mx-auto flex min-h-screen w-full max-w-5xl flex-col px-5 py-8 lg:px-8">
      <!-- 步骤条 -->
      <header class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="guide-logo">P</span>
          <span class="text-sm font-semibold text-auth-text">校园成长平台</span>
        </div>
        <div class="flex items-center gap-2 text-[11px] text-auth-sub">
          <span class="rounded-full bg-white/10 px-2 py-0.5 text-auth-text">1 / 2</span>
          <span>选择伙伴</span>
          <span class="text-auth-muted">→</span>
          <span class="text-auth-muted">开启礼盒</span>
        </div>
      </header>

      <!-- 标题 -->
      <div class="mt-8 text-center lg:mt-12">
        <h1 class="text-xl font-bold text-auth-text lg:text-2xl">选一个陪你上完这学期的伙伴</h1>
        <p class="mx-auto mt-2 max-w-md text-xs leading-relaxed text-auth-sub">
          它会出现在首页右下角，提醒你下一节课、待办审批和没读的消息。选好之后还能在「我的 → 账号资料」里换。
        </p>
      </div>

      <!-- 卡片列表：移动端横向 snap，桌面五列并排 -->
      <div class="flex flex-1 items-center">
        <div
          v-if="loaded"
          class="flex w-full snap-x snap-mandatory gap-3 overflow-x-auto px-1 pb-2 lg:grid lg:grid-cols-5 lg:gap-4 lg:overflow-visible"
        >
          <button
            v-for="pet in PETS"
            :key="pet.id"
            type="button"
            class="group relative w-[46%] shrink-0 snap-center rounded-2xl border p-4 text-left transition duration-200 sm:w-[38%] lg:w-auto"
            :class="
              selected === pet.id
                ? 'border-auth-accent bg-white/[0.09] shadow-[0_0_0_1px_rgba(79,140,255,0.6),0_18px_40px_-24px_rgba(79,140,255,0.9)]'
                : 'border-white/10 bg-white/[0.04] hover:border-white/25 hover:bg-white/[0.06]'
            "
            :aria-pressed="selected === pet.id"
            @click="selected = pet.id"
          >
            <span class="block text-3xl transition group-hover:scale-110 lg:text-4xl">{{ pet.emoji }}</span>
            <span class="mt-3 block text-sm font-semibold text-auth-text">{{ pet.name }}</span>
            <span class="mt-1 block text-[11px] leading-relaxed text-auth-sub">{{ pet.desc }}</span>
            <span
              v-if="selected === pet.id"
              class="absolute right-3 top-3 grid h-4 w-4 place-items-center rounded-full bg-auth-accent text-[10px] font-bold text-white"
            >
              ✓
            </span>
          </button>
        </div>

        <div v-else class="grid w-full gap-3 lg:grid-cols-5">
          <span v-for="i in 5" :key="i" class="h-32 animate-pulse rounded-2xl bg-white/[0.05]" />
        </div>
      </div>

      <!-- 底部确认 -->
      <div class="mt-auto pt-8">
        <p class="text-center text-[11px] text-auth-sub">
          {{ selected ? `已选择 · ${PETS.find((p) => p.id === selected)?.name}` : '左右滑动挑一个，选中后确认' }}
        </p>
        <button
          type="button"
          class="guide-submit"
          :disabled="!canSubmit"
          @click="confirm"
        >
          {{ submitting ? '正在为它腾位置…' : '就它了' }}
        </button>
        <p class="mt-3 text-center text-[10px] leading-relaxed text-auth-sub">
          确认后开启礼盒，可领取 100 光粒与 1 个礼盒 · 引导随时可以跳过，不影响任何业务功能
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.guide-stage {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(ellipse 70% 55% at 50% 0%, rgba(79, 140, 255, 0.16) 0%, transparent 60%),
    linear-gradient(180deg, #0a0e17 0%, #0d1225 45%, #0a0e17 100%);
}

/* 两个大光斑代替大半径 blur，移动端不卡 */
.glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(2px);
  pointer-events: none;
}
.glow-a {
  right: -120px;
  top: -80px;
  width: 320px;
  height: 320px;
  background: radial-gradient(circle, rgba(79, 140, 255, 0.18), transparent 70%);
}
.glow-b {
  left: -100px;
  bottom: -120px;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(124, 92, 252, 0.16), transparent 70%);
}

.guide-logo {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 10px;
  background: linear-gradient(135deg, #4f8cff, #7c5cfc);
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}

.guide-submit {
  margin-top: 12px;
  width: 100%;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4f8cff 0%, #3d6fd9 100%);
  font-size: 15px;
  font-weight: 500;
  color: #fff;
  transition:
    transform 0.15s ease,
    opacity 0.15s ease;
}
.guide-submit:active:not(:disabled) {
  transform: scale(0.99);
}
.guide-submit:disabled {
  opacity: 0.45;
}
</style>
