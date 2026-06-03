<template>
  <el-container class="layout-container">
    <el-header class="header">
      <div class="logo" @click="router.push('/')">校园生态平台</div>
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
        <el-badge v-if="notifCount > 0" :value="notifCount" :max="99" class="notif-badge">
          <el-icon :size="20" style="cursor: pointer" @click="router.push('/notifications')"><Bell /></el-icon>
        </el-badge>
        <el-icon v-else :size="20" style="cursor: pointer; color: #606266" @click="router.push('/notifications')"><Bell /></el-icon>
        <el-dropdown v-if="userStore.userInfo" style="margin-left: 16px">
          <span class="user-name">
            {{ userStore.userInfo.nickname || userStore.userInfo.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
              <el-dropdown-item @click="router.push('/my-goods')">我的商品</el-dropdown-item>
              <el-dropdown-item @click="router.push('/favorites')">我的收藏</el-dropdown-item>
              <el-dropdown-item @click="router.push('/orders')">我的订单</el-dropdown-item>
              <el-dropdown-item @click="router.push('/address')">收货地址</el-dropdown-item>
              <el-dropdown-item @click="router.push('/admin')" v-if="userStore.userInfo.role === 1">管理后台</el-dropdown-item>
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
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
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
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  color: #409eff;
  cursor: pointer;
  margin-right: 40px;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
}

.msg-badge {
  position: absolute;
  top: -4px;
  right: -8px;
}

.el-menu-item {
  position: relative;
}

.user-area {
  margin-left: auto;
}

.user-name {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #606266;
}

.main-content {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  padding: 16px;
}
</style>
