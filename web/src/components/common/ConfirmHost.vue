<!--
  全局确认弹窗宿主（配合 composables/useConfirm.js）

  挂在 App.vue 里唯一一份。组件本身不主动弹，只渲染 confirmState，
  业务代码一律 await confirm({...}) —— 这样"二次确认"只有一种写法。
-->
<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { confirmState, settleConfirm } from '@/composables/useConfirm'

const toneClass = computed(() =>
  confirmState.tone === 'danger'
    ? 'bg-rose-500 hover:bg-rose-600'
    : 'bg-brand-500 hover:bg-brand-600'
)

function onKey(e) {
  if (!confirmState.visible) return
  if (e.key === 'Escape') settleConfirm(false)
  if (e.key === 'Enter') settleConfirm(true)
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="confirmState.visible"
      class="fixed inset-0 z-[100] flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-sm"
      @click.self="settleConfirm(false)"
    >
      <div
        class="w-full max-w-[340px] animate-pop-in rounded-2xl bg-white p-5 shadow-pop"
        role="dialog"
        aria-modal="true"
      >
        <h3 class="text-center text-[15px] font-semibold text-slate-900">{{ confirmState.title }}</h3>
        <p v-if="confirmState.content" class="mt-2 text-center text-[13px] leading-relaxed text-slate-500">
          {{ confirmState.content }}
        </p>

        <div class="mt-5 flex gap-2.5">
          <button
            type="button"
            class="btn-ghost flex-1"
            @click="settleConfirm(false)"
          >
            {{ confirmState.cancelText }}
          </button>
          <button
            type="button"
            class="btn flex-1 text-white"
            :class="toneClass"
            @click="settleConfirm(true)"
          >
            {{ confirmState.confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
