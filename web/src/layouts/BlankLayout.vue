<!--
  空白外壳 BlankLayout（布局地基）

  给登录页、免登录分享页这类"整屏自己画"的页面用：不注入导航、不加内边距、不加底色。
-->
<template>
  <div class="min-h-screen">
    <slot />
  </div>
</template>
