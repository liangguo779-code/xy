<template>
  <div>
    <div class="page-header">
      <h2>消息通知</h2>
      <el-button text type="primary" @click="handleMarkAllRead">全部已读</el-button>
    </div>
    <!-- 通知条目列表：未读项高亮，点击标记已读并按类型跳转 -->
    <div v-for="n in list" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }"
         @click="handleRead(n)">
      <div class="notif-icon">
        <el-icon :size="20" :color="typeColor(n.type)">
          <Bell v-if="n.type === 'system'" />
          <Goods v-else-if="n.type === 'order_status'" />
          <ChatDotRound v-else-if="n.type === 'new_message'" />
          <Notebook v-else-if="n.type?.startsWith('forum_')" />
          <Warning v-else-if="n.type?.startsWith('report_') || n.type?.startsWith('dispute_')" />
          <Bell v-else />
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
/** 消息通知页：展示站内通知列表，点击条目标记已读并按类型跳转帖子、订单或举报页 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Goods, ChatDotRound, Notebook, Warning } from '@element-plus/icons-vue'
import { getNotifications, markRead, markAllRead } from '@/api/notification'

const router = useRouter()
const list = ref([])

/** 通知类型与图标配色映射：system 系统、order_status 订单、new_message 私信、forum 前缀论坛、report 前缀举报、dispute 前缀纠纷 */
const typeColor = (t) => ({
  system: '#409eff', order_status: '#67c23a', new_message: '#e6a23c',
  forum_comment: '#409eff', forum_reply: '#67c23a', forum_like: '#e6a23c',
  report_handled: '#52C41A', report_rejected: '#F56C6C',
  dispute_resolved: '#52C41A', dispute_rejected: '#F56C6C'
}[t] || '#909399')

/** 仅拉取最近 50 条，通知列表暂不做完整分页 */
async function loadData() {
  const res = await getNotifications({ page: 1, size: 50 })
  list.value = res.data?.records || []
}

/** 点击通知：未读先标记已读（isRead 用 0/1，置 1 同步本地态），再按类型跳转对应页面 */
async function handleRead(n) {
  if (!n.isRead) {
    await markRead(n.id)
    n.isRead = 1
  }
  // 论坛通知跳转到帖子详情
  if (n.type?.startsWith('forum_') && n.extra) {
    try {
      const extra = JSON.parse(n.extra)
      if (extra.postId) router.push(`/forum/${extra.postId}`)
    } catch { /* ignore */ }
  }
  // 纠纷通知跳转到订单详情
  if (n.type?.startsWith('dispute_') && n.extra) {
    try {
      const extra = JSON.parse(n.extra)
      if (extra.orderId) router.push(`/orders/${extra.orderId}`)
    } catch { /* ignore */ }
  }
  // 举报通知跳转到我的举报
  if (n.type?.startsWith('report_')) {
    router.push('/my-reports')
  }
}

/** 批量标记通知已读，成功后本地同步置 1 免重新拉取 */
async function handleMarkAllRead() {
  await markAllRead()
  list.value.forEach(n => n.isRead = 1)
}

onMounted(loadData)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  font-weight: 700;
  color: #1D2129;
  margin: 0;
}

.notif-item {
  display: flex;
  gap: 14px;
  padding: 16px;
  border-bottom: 1px solid #F2F3F5;
  cursor: pointer;
  border-radius: 10px;
  transition: background 0.2s;
  margin: 0 4px;
}

.notif-item:hover {
  background: #F7F8FA;
}

.notif-item.unread {
  background: #E8EEFE;
}

.notif-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #F2F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notif-item.unread .notif-icon {
  background: #C8D9FC;
}

.notif-body { flex: 1; }
.notif-title { font-weight: 600; margin-bottom: 4px; color: #1D2129; }
.notif-content { font-size: 13px; color: #4E5969; margin-bottom: 4px; }
.notif-time { font-size: 12px; color: #C9CDD4; }
</style>
