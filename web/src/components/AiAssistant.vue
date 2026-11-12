<script setup>
/**
 * 全局 AI 助手（右下角浮球 + 抽屉）。
 *
 * 流式对话走 POST /v1/ai/chat/stream（SSE），首字 ~500ms，能停、能重试。
 * 只在登录后显示（由 App.vue 控制），对话内容不落库，关掉页面就没了 ——
 * 学生问的多半是"我该怎么填"，不留痕更合适。
 */
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { aiApi } from '@/api/ai'
import { streamChat } from '@/utils/sse'
import { humanMessage } from '@/utils/crypto'
import { useAiPanel } from '@/composables/useAiPanel'

const { open, pending, takePending } = useAiPanel()
const route = useRoute()
const input = ref('')
const busy = ref(false)
const model = ref('')
const statusError = ref('')
const listEl = ref(null)
let controller = null

/** 气泡：{ role, content, streaming?, error? } */
const messages = ref([])

const QUICK = ['请假流程是怎样的？', '奖学金怎么申请？', '本周课表在哪看？', '家长怎么关联我的账号？']

const canSend = computed(() => !busy.value && input.value.trim().length > 0)

/** 浮球位置：一级页面要避开 64px 固定底栏，二级页/后台贴右下即可 */
const fabOffsetClass = computed(() =>
  route.meta.layout === 'app'
    ? 'bottom-[calc(80px+env(safe-area-inset-bottom))] lg:bottom-5'
    : 'bottom-5'
)

function toggle() {
  open.value = !open.value
}

/** 从浮球或首页入口打开时都走这里：拉一次状态 + 放一句开场白 */
watch(open, (v) => {
  if (!v) return
  loadStatus()
  if (!messages.value.length) {
    messages.value.push({
      role: 'assistant',
      content:
        '你好，我是校园成长平台的 AI 助手。可以问我请假怎么填、审批到哪一步、奖学金计分规则、课表与评价怎么用。'
    })
  }
})

/** 首页快捷提问 / 其它页面带问题进来：pending 一有值就自动发送 */
watch(pending, (q) => {
  if (!q) return
  const text = takePending()
  if (text) send(text)
})

async function loadStatus() {
  if (model.value) return
  try {
    const st = await aiApi.status()
    model.value = st.enabled ? st.model : ''
    statusError.value = st.enabled ? '' : 'AI 未配置密钥，暂时不可用'
  } catch (e) {
    statusError.value = humanMessage(e?.message, 'AI 状态获取失败')
  }
}

async function scrollToBottom() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

async function send(text) {
  const message = (text ?? input.value).trim()
  if (!message || busy.value) return

  input.value = ''
  messages.value.push({ role: 'user', content: message })
  const answer = { role: 'assistant', content: '', streaming: true }
  messages.value.push(answer)
  busy.value = true
  statusError.value = ''
  await scrollToBottom()

  // 历史只带最近 8 条，避免把整段对话反复传给模型
  const history = messages.value
    .slice(0, -2)
    .slice(-8)
    .map(({ role, content }) => ({ role, content }))

  controller = new AbortController()
  await streamChat(
    message,
    history,
    {
      onOpen: (p) => {
        if (p?.model) model.value = p.model
      },
      onDelta: (d) => {
        answer.content += d
        scrollToBottom()
      },
      onDone: () => {
        answer.streaming = false
      },
      onError: (m) => {
        answer.streaming = false
        answer.error = true
        answer.content = answer.content || `出错了：${humanMessage(m)}`
      }
    },
    controller.signal
  )

  answer.streaming = false
  busy.value = false
  controller = null
  await scrollToBottom()
}

function stop() {
  controller?.abort()
  controller = null
  busy.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) last.streaming = false
}

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

onBeforeUnmount(() => stop())
</script>

<template>
  <!-- 浮球：一级页面避让底部 tab（AppShell 有 64px 固定导航），详情页仍贴右下 -->
  <button
    v-if="!open"
    class="fixed right-5 z-40 h-14 w-14 rounded-2xl bg-gradient-to-br from-brand-500 to-indigo-500 text-white shadow-lg shadow-brand-500/30 transition hover:scale-105 active:scale-95"
    :class="fabOffsetClass"
    aria-label="打开 AI 助手"
    @click="toggle"
  >
    <span class="text-xl font-bold">AI</span>
    <span class="absolute -top-1 -right-1 h-3 w-3 rounded-full bg-emerald-400 ring-2 ring-white" />
  </button>

  <!-- 抽屉 -->
  <transition name="ai-panel">
    <section
      v-if="open"
      class="fixed bottom-0 right-0 z-40 flex h-[78vh] w-full flex-col rounded-t-2xl bg-white shadow-2xl sm:bottom-5 sm:right-5 sm:h-[600px] sm:w-[400px] sm:rounded-2xl"
    >
      <header class="flex items-center gap-2 border-b border-slate-100 px-4 py-3">
        <div class="grid h-8 w-8 place-items-center rounded-xl bg-brand-500 text-sm font-bold text-white">AI</div>
        <div class="min-w-0 flex-1">
          <p class="text-sm font-semibold">AI 助手</p>
          <p class="truncate text-[11px] text-slate-400">
            {{ model ? `模型 ${model} · 流式回答` : '校园业务问答' }}
          </p>
        </div>
        <button class="rounded-lg px-2 py-1 text-xs text-slate-400 hover:bg-slate-100" @click="open = false">
          收起
        </button>
      </header>

      <div ref="listEl" class="flex-1 space-y-3 overflow-y-auto px-4 py-3">
        <div
          v-for="(m, i) in messages"
          :key="i"
          class="flex"
          :class="m.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[86%] whitespace-pre-wrap break-words rounded-2xl px-3.5 py-2.5 text-[13px] leading-relaxed"
            :class="
              m.role === 'user'
                ? 'rounded-br-md bg-brand-500 text-white'
                : m.error
                  ? 'rounded-bl-md bg-rose-50 text-rose-700'
                  : 'rounded-bl-md bg-slate-100 text-slate-700'
            "
          >
            {{ m.content }}<span v-if="m.streaming" class="ai-caret">▍</span>
          </div>
        </div>

        <p v-if="statusError" class="rounded-xl bg-amber-50 px-3 py-2 text-[12px] text-amber-700">
          {{ statusError }}
        </p>
      </div>

      <!-- 快捷问题 -->
      <div v-if="messages.length <= 1" class="flex flex-wrap gap-1.5 px-4 pb-2">
        <button
          v-for="q in QUICK"
          :key="q"
          class="rounded-full border border-slate-200 px-2.5 py-1 text-[11px] text-slate-500 transition hover:border-brand-500 hover:text-brand-600"
          @click="send(q)"
        >
          {{ q }}
        </button>
      </div>

      <footer class="border-t border-slate-100 p-3">
        <div class="flex items-end gap-2">
          <textarea
            v-model="input"
            rows="1"
            class="max-h-24 min-h-[40px] flex-1 resize-none rounded-xl border border-slate-200 px-3 py-2 text-[13px] outline-none focus:border-brand-500"
            placeholder="问点什么…（Enter 发送，Shift+Enter 换行）"
            @keydown="onKeydown"
          />
          <button
            v-if="busy"
            class="h-10 shrink-0 rounded-xl border border-slate-200 px-3 text-xs text-slate-500"
            @click="stop"
          >
            停止
          </button>
          <button
            v-else
            class="h-10 shrink-0 rounded-xl bg-brand-500 px-4 text-sm font-medium text-white disabled:opacity-40"
            :disabled="!canSend"
            @click="send()"
          >
            发送
          </button>
        </div>
        <p class="mt-1.5 text-center text-[10px] text-slate-300">
          AI 生成内容仅供参考，政策口径以学校正式通知为准
        </p>
      </footer>
    </section>
  </transition>
</template>

<style scoped>
.ai-caret {
  animation: blink 1s step-end infinite;
  color: #4f7cff;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.ai-panel-enter-active,
.ai-panel-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.ai-panel-enter-from,
.ai-panel-leave-to {
  opacity: 0;
  transform: translateY(16px);
}
</style>
