<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="logo" @click="router.push('/')">
        <span class="logo-icon">🎓</span>
        <span class="logo-text">校园生态平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        mode="horizontal"
        :router="true"
        class="nav-menu"
      >
        <el-menu-item index="/goods">
          <el-icon><GoodsFilled /></el-icon>
          <span>二手交易</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>消息</span>
          <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="msg-badge" />
        </el-menu-item>
        <el-menu-item index="/forum">
          <el-icon><Notebook /></el-icon>
          <span>社区论坛</span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <el-icon><MagicStick /></el-icon>
          <span>AI 咨询</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon>
          <span>我的订单</span>
        </el-menu-item>
        <el-menu-item index="/crazy-thursday">
          <span class="kfc-icon">🍗</span>
          <span>疯狂星期四</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.userInfo?.role === 1 || userStore.userInfo?.role === 2" index="/dispatch">
          <el-icon><Van /></el-icon>
          <span>派单大厅</span>
        </el-menu-item>
      </el-menu>
      <div class="user-area">
        <div class="notif-btn" @click="router.push('/notifications')">
          <el-badge v-if="notifCount > 0" :value="notifCount" :max="99">
            <el-icon :size="18"><Bell /></el-icon>
          </el-badge>
          <el-icon v-else :size="18"><Bell /></el-icon>
        </div>
        <el-dropdown v-if="userStore.userInfo" class="user-dropdown">
          <div class="user-avatar-wrap">
            <el-avatar :size="32" class="user-avatar">
              {{ (userStore.userInfo.nickname || userStore.userInfo.username || 'U').charAt(0) }}
            </el-avatar>
            <span class="user-name">{{ userStore.userInfo.nickname || userStore.userInfo.username }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
              <el-dropdown-item @click="router.push('/my-goods')">我的商品</el-dropdown-item>
              <el-dropdown-item @click="router.push('/favorites')">我的收藏</el-dropdown-item>
              <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
              <el-dropdown-item @click="router.push('/my-reports')">我的举报</el-dropdown-item>
              <el-dropdown-item @click="router.push('/my-disputes')">我的纠纷</el-dropdown-item>
              <el-dropdown-item @click="router.push('/address')">收货地址</el-dropdown-item>
              <el-dropdown-item @click="goAdmin" v-if="userStore.userInfo.role === 1">管理后台</el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>
    <el-main class="main-content">
      <router-view />
    </el-main>
  </el-container>
  <MobileTabBar class="mobile-only" />
</template>

<script setup>
import MobileTabBar from '@/components/MobileTabBar.vue'
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { getUnreadCount } from '@/api/notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const notifCount = ref(0)

const unreadCount = computed(() => chatStore.unreadCount)

// 离开通知页面时刷新未读数
watch(() => route.path, (newPath, oldPath) => {
  if (oldPath === '/notifications') loadNotifCount()
})

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/goods')) return '/goods'
  if (path.startsWith('/chat')) return '/chat'
  if (path.startsWith('/forum')) return '/forum'
  if (path.startsWith('/ai')) return '/ai'
  if (path.startsWith('/orders')) return '/orders'
  return path
})

onMounted(() => {
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchUserInfo()
  }
  chatStore.loadUnreadCount()
  loadNotifCount()
  const token = localStorage.getItem('token')
  if (token) chatStore.connectWebSocket(token)
})

async function loadNotifCount() {
  try {
    const res = await getUnreadCount()
    notifCount.value = res.data?.count || 0
  } catch (e) { /* ignore */ }
}

onUnmounted(() => {
  if (ws) ws.close()
})

function goAdmin() {
  window.location.href = '/admin'
}

async function handleLogout() {
  await userStore.logout()
  router.push('/login')
  window.location.reload()
}
</script>

<style scoped>
.kfc-icon {
  font-size: 18px;
  margin-right: 4px;
}

.layout-container {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  margin-right: 36px;
  white-space: nowrap;
  flex-shrink: 0;
}

.logo-icon {
  font-size: 26px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
  background: transparent;
}

.nav-menu .el-menu-item {
  font-size: 14px;
  font-weight: 500;
  color: #4E5969;
  border-bottom: 2px solid transparent;
  transition: color 0.25s, border-color 0.25s;
}

.nav-menu .el-menu-item:hover {
  color: #5B8FF9;
  background: transparent;
}

.nav-menu .el-menu-item.is-active {
  color: #5B8FF9;
  border-bottom-color: #5B8FF9;
  background: transparent;
}

.msg-badge {
  position: absolute;
  top: 2px;
  right: -4px;
}

.user-area {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.notif-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #4E5969;
  transition: background 0.2s, color 0.2s;
}

.notif-btn:hover {
  background: #F2F3F5;
  color: #5B8FF9;
}

.user-dropdown {
  outline: none;
}

.user-avatar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-avatar-wrap:hover {
  background: #F2F3F5;
}

.user-avatar {
  background: linear-gradient(135deg, #5B8FF9, #6366F1);
  color: #fff;
  font-weight: 600;
  font-size: 14px;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #1D2129;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow-icon {
  font-size: 12px;
  color: #86909C;
  transition: transform 0.2s;
}

.main-content {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding: 20px;
}

.mobile-only {
  display: none;
}

@media (max-width: 768px) {
  .header {
    padding: 0 12px;
  }

  .logo {
    margin-right: 16px;
  }

  .logo-text {
    font-size: 15px;
  }

  .nav-menu {
    display: none;
  }

  .main-content {
    padding: 12px;
    padding-bottom: 70px;
  }

  .mobile-only {
    display: block;
  }
}
</style>
