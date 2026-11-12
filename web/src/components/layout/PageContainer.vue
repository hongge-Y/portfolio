<!--
  内容容器（布局地基）

  作用：限制超宽屏下的行长、统一左右内边距。
  所有页面都不再自己写 max-w-3xl / px-4，宽度只有这一处定义。

  用法：
    <PageContainer>           默认 1120px，桌面 32px 内边距
    <PageContainer size="narrow">   760px，长文阅读
    <PageContainer size="form">     560px，表单
-->
<script setup>
defineProps({
  /** default | narrow | form | wide */
  size: { type: String, default: 'default' },
  /** 是否要默认内边距（自己做了通栏排版的页面可以关掉） */
  padded: { type: Boolean, default: true }
})

const WIDTH = {
  default: 'max-w-content',
  narrow: 'max-w-reading',
  form: 'max-w-form',
  wide: 'max-w-[1320px]'
}
</script>

<template>
  <div
    class="mx-auto w-full"
    :class="[WIDTH[size] || WIDTH.default, padded ? 'px-4 py-4 lg:px-8 lg:py-6' : '']"
  >
    <slot />
  </div>
</template>
