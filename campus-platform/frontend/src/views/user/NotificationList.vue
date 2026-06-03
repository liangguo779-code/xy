<template>
  <div>
    <div class="page-header">
      <h2>消息通知</h2>
      <el-button text type="primary" @click="handleMarkAllRead">全部已读</el-button>
    </div>
    <div v-for="n in list" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }"
         @click="handleRead(n)">
      <div class="notif-icon">
        <el-icon :size="20" :color="typeColor(n.type)">
          <Bell v-if="n.type === 'system'" />
          <Goods v-else-if="n.type === 'order_status'" />
          <ChatDotRound v-else />
        </el-icon>
      </div>
      <div class="notif-body">
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-content">{{ n.content }}</div>
        <div class="notif-time">{{ n.createTime }}</div>
      </div>
    </div>
    <el-empty v-if="list.length === 0" description="暂无通知" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getNotifications, markRead, markAllRead } from '@/api/notification'

const list = ref([])

const typeColor = (t) => ({
  system: '#409eff', order_status: '#67c23a', new_message: '#e6a23c'
}[t] || '#909399')

async function loadData() {
  const res = await getNotifications({ page: 1, size: 50 })
  list.value = res.data?.records || []
}

async function handleRead(n) {
  if (!n.isRead) {
    await markRead(n.id)
    n.isRead = 1
  }
}

async function handleMarkAllRead() {
  await markAllRead()
  list.value.forEach(n => n.isRead = 1)
}

onMounted(loadData)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.notif-item { display: flex; gap: 12px; padding: 14px; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.notif-item.unread { background: #ecf5ff; }
.notif-icon { width: 36px; height: 36px; border-radius: 50%; background: #f0f2f5; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.notif-body { flex: 1; }
.notif-title { font-weight: 500; margin-bottom: 4px; }
.notif-content { font-size: 13px; color: #606266; margin-bottom: 4px; }
.notif-time { font-size: 12px; color: #c0c4cc; }
</style>
