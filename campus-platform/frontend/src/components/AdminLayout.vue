<template>
  <el-container class="admin-layout">
    <!-- 管理端侧边栏：按业务域分组的菜单导航，index 直接对应路由路径 -->
    <el-aside width="220px" class="aside">
      <div class="aside-logo">
        <span class="aside-logo-icon">⚙️</span>
        <span class="aside-logo-text">管理后台</span>
      </div>
      <el-menu :default-active="activeMenu" router :collapse="false" class="aside-menu">
        <el-menu-item-group title="数据中心">
          <el-menu-item index="/admin/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>数据概览</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="内容管理">
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/goods">
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/hot-goods">
            <el-icon><Sell /></el-icon>
            <span>热卖管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/orders">
            <el-icon><List /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/forum">
            <el-icon><Notebook /></el-icon>
            <span>论坛管理</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="运营治理">
          <el-menu-item index="/admin/disputes">
            <el-icon><Warning /></el-icon>
            <span>纠纷处理</span>
          </el-menu-item>
          <el-menu-item index="/admin/reports">
            <el-icon><Bell /></el-icon>
            <span>举报管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/moderation">
            <el-icon><Filter /></el-icon>
            <span>内容安全</span>
          </el-menu-item>
          <el-menu-item index="/admin/bans">
            <el-icon><Lock /></el-icon>
            <span>封禁管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/reviews">
            <el-icon><Star /></el-icon>
            <span>评价管理</span>
          </el-menu-item>
        </el-menu-item-group>
        <el-menu-item-group title="系统">
          <el-menu-item index="/admin/config">
            <el-icon><Setting /></el-icon>
            <span>系统配置</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge">
            <el-icon><Document /></el-icon>
            <span>知识库管理</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>
    </el-aside>
    <el-container>
      <!-- 顶栏：后台标题与返回前台入口 -->
      <el-header class="header">
        <span class="header-title">校园生态平台 · 管理后台</span>
        <el-button text class="back-btn" @click="router.push('/')">
          <el-icon><Back /></el-icon>
          返回前台
        </el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
/**
 * 管理后台布局：左侧分组菜单 + 顶栏 + 内容区，内容区渲染各管理页子路由
 * 菜单项 index 即路由路径，配合 el-menu 的 router 模式完成跳转
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis, User, Goods, List, Warning, Bell, Lock, Setting, Notebook, Back, Document, Star, Filter, Sell
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
/** 当前高亮菜单项，直接取路由路径以对齐菜单 index */
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.admin-layout {
  height: 100vh;
}

.aside {
  background: linear-gradient(180deg, #1E2A3A 0%, #2C3E50 100%);
  overflow-y: auto;
}

.aside-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.aside-logo-icon {
  font-size: 24px;
}

.aside-logo-text {
  font-size: 17px;
  font-weight: 700;
  color: #fff;
}

/* Element Plus 菜单默认浅色，此处覆盖样式以适配深色侧边栏 */
.aside-menu {
  border-right: none;
  background: transparent;
  padding: 8px 0;
}

.aside-menu .el-menu-item-group__title {
  padding: 12px 20px 6px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.35);
  text-transform: uppercase;
  letter-spacing: 1px;
}

.aside-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.65);
  margin: 2px 8px;
  border-radius: 8px;
  height: 40px;
  line-height: 40px;
}

.aside-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.aside-menu .el-menu-item.is-active {
  background: rgba(91, 143, 249, 0.2);
  color: #5B8FF9;
  position: relative;
}

.aside-menu .el-menu-item.is-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: #5B8FF9;
  border-radius: 2px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #F2F3F5;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
  padding: 0 24px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1D2129;
}

.back-btn {
  color: #5B8FF9;
  font-weight: 500;
}

.main {
  background: #F7F8FA;
  padding: 20px;
}
</style>
