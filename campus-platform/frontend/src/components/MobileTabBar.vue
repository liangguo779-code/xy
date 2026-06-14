<template>
  <div class="mobile-tabbar">
    <div v-for="tab in tabs" :key="tab.path"
         :class="['tab-item', { active: isActive(tab.path) }]"
         @click="router.push(tab.path)">
      <el-icon :size="22"><component :is="tab.icon" /></el-icon>
      <span class="tab-label">{{ tab.label }}</span>
    </div>
  </div>
</template>

<script setup>
import { useRoute, useRouter } from 'vue-router'
import { GoodsFilled, Notebook, Plus, ChatDotRound, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { path: '/goods', label: '首页', icon: GoodsFilled },
  { path: '/forum', label: '论坛', icon: Notebook },
  { path: '/publish', label: '发布', icon: Plus },
  { path: '/chat', label: '消息', icon: ChatDotRound },
  { path: '/profile', label: '我的', icon: User }
]

function isActive(path) {
  return route.path.startsWith(path)
}
</script>

<style scoped>
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: space-around;
  z-index: 200;
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  cursor: pointer;
  color: #86909C;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
}

.tab-item.active {
  color: #5B8FF9;
}

.tab-label {
  font-size: 10px;
  font-weight: 500;
}

@media (min-width: 769px) {
  .mobile-tabbar {
    display: none;
  }
}
</style>
