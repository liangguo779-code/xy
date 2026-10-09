<template>
  <div class="comment-item">
    <div class="comment-header">
      <span class="author">用户 #{{ comment.userId }}</span>
      <span class="time">{{ comment.createTime }}</span>
    </div>
    <p class="comment-content">{{ comment.content }}</p>
    <!-- 评论图片：多图共享预览列表，点击可查看大图 -->
    <div v-if="getImages(comment).length" class="comment-images">
      <el-image v-for="(img, i) in getImages(comment)" :key="i" :src="img"
                fit="contain" style="max-width: 200px; max-height: 200px; margin: 4px 4px 0 0; border-radius: 6px"
                :preview-src-list="getImages(comment)" :initial-index="i" />
    </div>
    <!-- 操作区：点赞/回复/举报统一上抛父组件处理，递归时透传 -->
    <div class="comment-actions">
      <span class="action-btn" @click="$emit('like', comment)">
        <el-icon :size="14" :color="comment.liked ? '#409eff' : '#909399'">
          <component :is="comment.liked ? 'StarFilled' : 'Star'" />
        </el-icon>
        {{ comment.likeCount || 0 }}
      </span>
      <span class="action-btn" @click="$emit('reply', comment)">回复</span>
      <span class="action-btn" @click="$emit('report', comment)">举报</span>
    </div>

    <!-- 嵌套回复 -->
    <div v-if="comment.children && comment.children.length" class="comment-children">
      <CommentItem v-for="child in comment.children" :key="child.id"
                   :comment="child" @like="$emit('like', $event)" @reply="$emit('reply', $event)" @report="$emit('report', $event)" />
    </div>
  </div>
</template>

<script setup>
/**
 * 单条评论项：展示用户、时间、内容与图片，提供点赞/回复/举报操作
 * 子回复通过 children 字段递归渲染自身，事件逐层透传至页面根组件处理
 */
import { Star, StarFilled } from '@element-plus/icons-vue'

/** 评论对象，含 userId/createTime/images/liked/likeCount 及 children 子回复 */
defineProps({
  comment: { type: Object, required: true }
})

/** like/reply/report 事件，均携带触发操作的评论对象 */
defineEmits(['like', 'reply', 'report'])

/** images 字段后端可能返回 JSON 字符串或数组，统一转成数组，解析失败按无图处理 */
function getImages(c) {
  if (!c.images) return []
  try {
    return typeof c.images === 'string' ? JSON.parse(c.images) : c.images
  } catch { return [] }
}
</script>

<style scoped>
.comment-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.comment-header .author {
  font-weight: bold;
  color: #303133;
  font-size: 13px;
}

.comment-header .time {
  color: #c0c4cc;
  font-size: 12px;
}

.comment-content {
  margin: 0 0 6px;
  line-height: 1.6;
  color: #303133;
}

.comment-images {
  margin-bottom: 6px;
}

.comment-actions {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  cursor: pointer;
  transition: color 0.2s;
}

.action-btn:hover {
  color: #409eff;
}

.comment-children {
  margin-left: 24px;
  border-left: 2px solid #f0f0f0;
  padding-left: 12px;
}
</style>
