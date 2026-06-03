<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="logo">管理后台</div>
      <el-menu :default-active="activeMenu" router :collapse="false">
        <el-menu-item index="/admin/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>
        <el-menu-item index="/admin/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/goods">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/orders">
          <el-icon><List /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/disputes">
          <el-icon><Warning /></el-icon>
          <span>纠纷处理</span>
        </el-menu-item>
        <el-menu-item index="/admin/reports">
          <el-icon><Bell /></el-icon>
          <span>举报管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/bans">
          <el-icon><Lock /></el-icon>
          <span>封禁管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/config">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>校园生态平台 - 管理后台</span>
        <el-button text @click="router.push('/')">返回前台</el-button>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis, User, Goods, List, Warning, Bell, Lock, Setting
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.admin-layout { height: 100vh; }
.aside { background: #304156; overflow-y: auto; }
.logo { color: #fff; font-size: 18px; font-weight: bold; padding: 20px; text-align: center; border-bottom: 1px solid rgba(255,255,255,0.1); }
.el-menu { border-right: none; }
.header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #e4e7ed; background: #fff; }
.main { background: #f0f2f5; padding: 20px; }
</style>
