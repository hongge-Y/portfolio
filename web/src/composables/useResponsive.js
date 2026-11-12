/**
 * 响应式断点（布局地基）
 *
 * 页面里判断"要不要用桌面形态"一律从这里取，不要各写 window.innerWidth。
 * 宽度是模块级单例，整个应用只绑一次 resize 监听。
 */
import { computed, ref } from 'vue'

/** 与 tailwind.config.js 的 screens 一致，改动需两边同步 */
export const BREAKPOINTS = { sm: 640, md: 768, lg: 1024, xl: 1280 }

const width = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)
let bound = false

function bindResizeOnce() {
  if (bound || typeof window === 'undefined') return
  bound = true
  window.addEventListener(
    'resize',
    () => {
      width.value = window.innerWidth
    },
    { passive: true }
  )
}

export function useResponsive() {
  bindResizeOnce()
  return {
    width: computed(() => width.value),
    /** 手机：< 768 */
    isMobile: computed(() => width.value < BREAKPOINTS.md),
    /** 平板及以上：>= 768（两栏卡片流） */
    isTablet: computed(() => width.value >= BREAKPOINTS.md),
    /** 桌面形态：>= 1024（顶部导航 + 多栏） */
    isDesktop: computed(() => width.value >= BREAKPOINTS.lg),
    /** 宽屏：>= 1280（可以再开一列侧栏） */
    isWide: computed(() => width.value >= BREAKPOINTS.xl)
  }
}
