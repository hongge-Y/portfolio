<script setup>
/**
 * 新手引导 ②开礼盒（套 BlankLayout）
 *
 * 三段演出：礼盒抖动(1.2s) → 开盖发光(0.8s) → 卡片飞出。
 * 奖励不是造的：数值来自"选宠前后"的账户快照差值（useOnboarding.delta），
 * 拉不到快照才退回 features/onboarding/pets.js 里的固定奖励文案。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useOnboarding } from '@/composables/useOnboarding'
import { GUIDE_REWARD, petById } from '@/features/onboarding/pets'
import { profileApi } from '@/api/profile'

const router = useRouter()
const { onboarding, delta } = useOnboarding()

/** idle → shaking → opening → card */
const stage = ref('idle')
/** 卡片是否已翻面（3D rotateY） */
const flipped = ref(false)
const owned = ref(false)
let timers = []

const pet = computed(() => petById(onboarding.pet))
const reward = computed(() => {
  const d = delta({ coin: 0, gift: 0 })
  return d.coin || d.gift ? d : GUIDE_REWARD
})

function schedule(fn, ms) {
  timers.push(setTimeout(fn, ms))
}

function play() {
  stage.value = 'shaking'
  schedule(() => (stage.value = 'opening'), 1200)
  schedule(() => (stage.value = 'card'), 2000)
  schedule(() => (flipped.value = true), 2600)
}

onMounted(async () => {
  // 直接打开本页（没走选宠）时：已引导过的用户按"回顾"处理，没引导过的送回第一步
  if (!onboarding.pet) {
    const me = await profileApi.me().catch(() => null)
    if (me && !Number(me.guideDone)) {
      router.replace('/guide/pet')
      return
    }
    if (me?.pet > 0) {
      onboarding.pet = me.pet
      owned.value = true
    }
  }
  schedule(play, 400)
})

onBeforeUnmount(() => {
  timers.forEach(clearTimeout)
  timers = []
})

function enter() {
  router.replace('/')
}

function skip() {
  stage.value = 'card'
  flipped.value = true
}
</script>

<template>
  <div class="guide-stage">
    <span class="glow glow-a" aria-hidden="true" />
    <span class="glow glow-b" aria-hidden="true" />

    <div class="relative mx-auto flex min-h-screen w-full max-w-lg flex-col px-6 py-8">
      <!-- 步骤条 -->
      <header class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <span class="guide-logo">P</span>
          <span class="text-sm font-semibold text-auth-text">校园成长平台</span>
        </div>
        <div class="flex items-center gap-2 text-[11px]">
          <span class="rounded-full bg-white/10 px-2 py-0.5 text-auth-text">2 / 2</span>
          <span class="text-auth-muted">开启礼盒</span>
        </div>
      </header>

      <div class="flex flex-1 flex-col items-center justify-center">
        <!-- 标题 -->
        <p class="text-center text-sm text-auth-sub">
          {{ stage === 'card' ? '请收下这份见面礼' : '它给你留了一份见面礼' }}
        </p>

        <!-- 礼盒演出区 -->
        <div class="relative mt-8 grid h-52 w-full place-items-center">
          <!-- 发光环：开盖瞬间放大 -->
          <span class="ring" :class="{ 'ring-on': stage === 'opening' || stage === 'card' }" aria-hidden="true" />

          <span
            v-if="stage !== 'card'"
            class="box text-[76px] leading-none select-none"
            :class="{ 'box-shake': stage === 'shaking', 'box-open': stage === 'opening' }"
            role="img"
            aria-label="礼盒"
          >
            🎁
          </span>

          <!-- 卡片：飞出 + 翻面 -->
          <div v-if="stage === 'card'" class="card-scene">
            <div class="card-3d" :class="{ 'is-flipped': flipped }" @click="flipped = !flipped">
              <!-- 正面 -->
              <div class="card-face card-front">
                <span class="text-4xl">{{ pet?.emoji || '✨' }}</span>
                <p class="mt-2 text-sm font-semibold text-auth-text">{{ pet?.card || '成长伙伴 · 初见' }}</p>
                <p class="mt-1 px-3 text-[11px] leading-relaxed text-auth-sub">
                  {{ pet?.cardDesc || '从今天起，课表、请假、消息它都替你盯着。' }}
                </p>
              </div>
              <!-- 背面：奖励 -->
              <div class="card-face card-back">
                <p class="text-[11px] tracking-widest text-white/70">见面礼</p>
                <div class="mt-3 flex items-center gap-6">
                  <div class="text-center">
                    <p class="text-xl font-bold text-white">+{{ reward.coin }}</p>
                    <p class="mt-0.5 text-[10px] text-white/70">光粒</p>
                  </div>
                  <div class="text-center">
                    <p class="text-xl font-bold text-white">×{{ reward.gift }}</p>
                    <p class="mt-0.5 text-[10px] text-white/70">礼盒</p>
                  </div>
                </div>
                <p class="mt-4 text-[10px] text-white/60">点卡片可以翻回来</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 奖励说明（卡片出现后才有意义） -->
        <div v-if="stage === 'card'" class="mt-6 w-full space-y-1.5 text-center">
          <p class="text-xs text-auth-sub">
            光粒与礼盒已到账，可在「我的」页查看
            <template v-if="owned">（这份是你之前领过的）</template>
          </p>
          <button type="button" class="text-[11px] text-auth-muted underline" @click="flipped = !flipped">
            看看奖励明细
          </button>
        </div>
      </div>

      <!-- 底部动作 -->
      <div class="pt-6">
        <button v-if="stage === 'card'" type="button" class="guide-submit" @click="enter">
          进主页看看
        </button>
        <button v-else type="button" class="guide-ghost" @click="skip">跳过动画</button>
        <p class="mt-3 text-center text-[10px] text-auth-muted">
          引导只出现一次，之后可在「我的 → 账号资料」更换小宠
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

.glow {
  position: absolute;
  border-radius: 999px;
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

/* ---------- 礼盒 ---------- */
.ring {
  position: absolute;
  width: 180px;
  height: 180px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(79, 140, 255, 0.35), transparent 65%);
  opacity: 0;
  transform: scale(0.5);
  transition:
    opacity 0.5s ease,
    transform 0.6s ease;
}
.ring-on {
  opacity: 1;
  transform: scale(1.35);
}

.box {
  display: block;
  transition: transform 0.25s ease;
}
.box-shake {
  animation: box-shake 1.2s ease-in-out;
}
.box-open {
  animation: box-pop 0.8s cubic-bezier(0.2, 0.9, 0.3, 1.2) forwards;
}

@keyframes box-shake {
  0%,
  100% {
    transform: rotate(0deg) scale(1);
  }
  15% {
    transform: rotate(-7deg) scale(1.02);
  }
  30% {
    transform: rotate(6deg) scale(1.02);
  }
  45% {
    transform: rotate(-5deg) scale(1.03);
  }
  60% {
    transform: rotate(4deg) scale(1.03);
  }
  80% {
    transform: rotate(-2deg) scale(1.04);
  }
}

@keyframes box-pop {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  55% {
    transform: scale(1.5) translateY(-14px);
    opacity: 0.95;
  }
  100% {
    transform: scale(0.2) translateY(-40px);
    opacity: 0;
  }
}

/* ---------- 卡片 ---------- */
.card-scene {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  perspective: 1000px;
}

.card-3d {
  position: relative;
  width: 260px;
  height: 168px;
  transform-style: preserve-3d;
  transform: translateY(30px) rotateY(0deg);
  animation: card-fly 0.6s cubic-bezier(0.2, 0.9, 0.3, 1.1) forwards;
  transition: transform 0.6s cubic-bezier(0.3, 0.8, 0.3, 1);
  cursor: pointer;
}
.card-3d.is-flipped {
  transform: translateY(0) rotateY(180deg);
}

@keyframes card-fly {
  from {
    opacity: 0;
    transform: translateY(56px) rotateY(0deg) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) rotateY(0deg) scale(1);
  }
}

.card-face {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  padding: 16px;
  text-align: center;
  backface-visibility: hidden;
  border: 1px solid rgba(255, 255, 255, 0.12);
}
.card-front {
  background: linear-gradient(160deg, rgba(79, 140, 255, 0.22), rgba(17, 21, 35, 0.96) 55%);
  box-shadow: 0 24px 48px -24px rgba(79, 140, 255, 0.85);
}
.card-back {
  transform: rotateY(180deg);
  background: linear-gradient(160deg, rgba(124, 92, 252, 0.28), rgba(17, 21, 35, 0.96) 55%);
  box-shadow: 0 24px 48px -24px rgba(124, 92, 252, 0.8);
}

.guide-submit {
  width: 100%;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4f8cff 0%, #3d6fd9 100%);
  font-size: 15px;
  font-weight: 500;
  color: #fff;
  transition: transform 0.15s ease;
}
.guide-submit:active {
  transform: scale(0.99);
}

.guide-ghost {
  width: 100%;
  height: 44px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 13px;
  color: #8892a8;
  transition: background 0.15s ease;
}
.guide-ghost:hover {
  background: rgba(255, 255, 255, 0.05);
}

/* 尊重系统"减少动效"：不抖不飞，直接给结果 */
@media (prefers-reduced-motion: reduce) {
  .box-shake,
  .box-open,
  .card-3d {
    animation: none !important;
  }
}
</style>
