<template>
  <!-- 根组件仅做路由出口，页面切换时用 fade-slide 过渡 -->
  <router-view v-slot="{ Component }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
