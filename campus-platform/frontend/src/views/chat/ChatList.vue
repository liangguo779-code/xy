<template>
  <div class="chat-list">
    <h2>我的消息</h2>
    <div v-if="sessions.length === 0" class="empty">
      <el-empty description="暂无聊天记录，去商品详情页点击[我想要]开始聊天吧" />
    </div>
    <div v-else>
      <div v-for="s in sessions" :key="s.id" class="session-item"
           @click="router.push(`/chat/${s.id}`)">
        <div class="session-avatars">
          <el-avatar :size="44">{{ s.otherNickname?.charAt(0) }}</el-avatar>
          <el-image v-if="s.goodsImage" :src="s.goodsImage" fit="cover"
                    class="goods-thumb" />
        </div>
        <div class="session-info">
          <div class="top-row">
            <span class="name">{{ s.otherNickname }}</span>
            <span class="time">{{ formatTime(s.lastTime) }}</span>
          </div>
          <div class="bottom-row">
            <span class="goods-tag" v-if="s.goodsTitle">{{ s.goodsTitle }}</span>
            <span class="last-msg">{{ s.lastMsg }}</span>
          </div>
        </div>
        <el-badge v-if="s.unreadCount > 0" :value="s.unreadCount" :max="99" class="unread-badge" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMySessions } from '@/api/chat'

const router = useRouter()
const sessions = ref([])

function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  if (d.toDateString() === now.toDateString()) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

onMounted(async () => {
  const res = await getMySessions()
  sessions.value = res.data || []
})
</script>

<style scoped>
.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-avatars {
  position: relative;
  flex-shrink: 0;
  width: 56px;
  height: 44px;
}

.goods-thumb {
  position: absolute;
  bottom: 0;
  right: -6px;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.top-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.name {
  font-weight: 500;
  color: #303133;
}

.time {
  font-size: 12px;
  color: #c0c4cc;
}

.bottom-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #909399;
  font-size: 13px;
}

.goods-tag {
  color: #409eff;
  white-space: nowrap;
}

.last-msg {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-badge {
  flex-shrink: 0;
  align-self: center;
}
</style>
