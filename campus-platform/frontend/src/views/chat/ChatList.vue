<template>
  <div class="chat-list">
    <h2>我的消息</h2>
    <div v-if="sessions.length === 0" class="empty">
      <el-empty description="暂无聊天记录，去商品详情页点击[我想要]开始聊天吧" />
    </div>
    <div v-else>
      <!-- 会话列表：点击整行进入聊天室，未读角标由后端按会话聚合 -->
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
            <!-- 未读角标固定在条目右上角（时间右侧） -->
            <el-badge v-if="s.unreadCount > 0" :value="s.unreadCount" :max="99" class="unread-badge" />
          </div>
          <div class="bottom-row">
            <span class="goods-tag" v-if="s.goodsTitle">{{ s.goodsTitle }}</span>
            <span class="last-msg">{{ s.lastMsg }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 我的消息：会话列表页
 * 汇总与各买家/卖家的聊天会话（含关联商品、最后一条消息、未读数），点击进入聊天室
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMySessions } from '@/api/chat'

const router = useRouter()
/** 会话列表，字段含 otherNickname/goodsTitle/goodsImage/lastMsg/unreadCount 等 */
const sessions = ref([])

/** 会话时间显示：当天只显时分，更早的显示月日，避免列表时间列过长 */
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
  padding: 14px 16px;
  border-bottom: 1px solid #F2F3F5;
  cursor: pointer;
  transition: background 0.2s;
  border-radius: 10px;
  margin: 0 4px;
}

.session-item:hover {
  background: #F7F8FA;
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
  border-radius: 8px;
  border: 2px solid #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.top-row {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.name {
  font-weight: 600;
  color: #1D2129;
}

.time {
  margin-left: auto;
  font-size: 12px;
  color: #C9CDD4;
}

.bottom-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #86909C;
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

/* 未读角标：紧贴时间右侧，固定在条目右上角 */
.unread-badge {
  flex-shrink: 0;
  margin-left: 8px;
}
</style>
