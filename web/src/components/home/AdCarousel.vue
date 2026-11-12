<script setup>
/**
 * 广告位：横向 scroll-snap 轮播。
 *
 * 不引轮播库 —— 首页只有 1~2 条广告，CSS snap + 圆点指示器就够。
 * 图片加载失败自动退回渐变底（演示环境里 /static 素材经常不存在，不能出现破图）。
 */
import { computed, reactive, ref } from 'vue'

const props = defineProps({
  /** [{ id, title, subTitle, image, linkType, link }] */
  ads: { type: Array, default: () => [] }
})
const emit = defineEmits(['open'])

const scroller = ref(null)
const active = ref(0)
/** 记录哪些图挂了：id → true */
const broken = reactive({})

const dots = computed(() => props.ads.map((_, i) => i))

/** 后端存的是 /static/...，静态资源实际挂在应用的 context-path（/api）之下 */
function imageUrl(path) {
  if (!path) return ''
  return path.startsWith('/static') ? `/api${path}` : path
}

function onScroll() {
  const el = scroller.value
  if (!el) return
  const cardWidth = el.firstElementChild?.clientWidth ?? el.clientWidth
  active.value = Math.round(el.scrollLeft / (cardWidth + 12))
}

function scrollToIndex(i) {
  const el = scroller.value
  if (!el) return
  const cardWidth = el.firstElementChild?.clientWidth ?? el.clientWidth
  el.scrollTo({ left: i * (cardWidth + 12), behavior: 'smooth' })
}
</script>

<template>
  <section class="relative overflow-hidden">
    <div
      ref="scroller"
      class="flex snap-x snap-mandatory gap-3 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      @scroll.passive="onScroll"
    >
      <button
        v-for="ad in ads"
        :key="ad.id"
        class="relative w-full shrink-0 snap-start overflow-hidden rounded-2xl text-left shadow-card transition active:scale-[0.99]"
        @click="emit('open', ad)"
      >
        <img
          v-if="ad.image && !broken[ad.id]"
          :src="imageUrl(ad.image)"
          :alt="ad.title"
          class="h-28 w-full object-cover"
          loading="lazy"
          @error="broken[ad.id] = true"
        />
        <div v-else class="h-28 w-full bg-gradient-to-br from-brand-500 via-brand-600 to-indigo-400" />

        <div class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/60 via-black/20 to-transparent p-4 pt-10">
          <p class="truncate font-semibold text-white">{{ ad.title }}</p>
          <p v-if="ad.subTitle" class="truncate text-xs text-white/85">{{ ad.subTitle }}</p>
        </div>
        <span
          v-if="ad.linkType === 2"
          class="absolute right-2 top-2 rounded-md bg-black/35 px-1.5 py-0.5 text-[10px] text-white"
        >外部链接 ↗</span>
      </button>
    </div>

    <div v-if="ads.length > 1" class="mt-2 flex justify-center gap-1.5">
      <button
        v-for="i in dots"
        :key="i"
        class="h-1.5 rounded-full transition-all"
        :class="i === active ? 'w-4 bg-brand-500' : 'w-1.5 bg-slate-300'"
        :aria-label="`第 ${i + 1} 张`"
        @click="scrollToIndex(i)"
      />
    </div>
  </section>
</template>
