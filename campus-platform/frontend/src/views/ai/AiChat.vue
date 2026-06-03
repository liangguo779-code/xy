<template>
  <div class="ai-chat-container">
    <!-- 历史会话侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>历史会话</h3>
        <el-button type="primary" size="small" @click="handleNewSession">
          <el-icon><Plus /></el-icon> 新对话
        </el-button>
      </div>
      <div class="session-list">
        <div v-for="s in sessions" :key="s.id"
             :class="['session-item', { active: s.id === currentSessionId }]"
             @click="loadSession(s.id)">
          <div class="session-title">{{ s.title }}</div>
          <div class="session-preview">{{ s.lastMessage || '新对话' }}</div>
          <el-icon class="delete-btn" @click.stop="handleDeleteSession(s.id)"><Delete /></el-icon>
        </div>
        <el-empty v-if="sessions.length === 0" description="暂无历史会话" :image-size="40" />
      </div>
    </div>

    <!-- 主聊天区 -->
    <div class="main-area">
      <el-card class="chat-card">
        <div class="messages" ref="messagesRef">
          <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
            <div class="avatar">
              <el-icon v-if="msg.role === 'user'" :size="20"><User /></el-icon>
              <el-icon v-else :size="20"><MagicStick /></el-icon>
            </div>
            <div class="bubble">
              <div v-if="msg.role === 'user'" class="text">{{ msg.content }}</div>
              <div v-else class="markdown-body" v-html="renderMarkdown(msg.content)"></div>

              <div v-if="parseSources(msg.sources)?.length" class="sources-section">
                <el-divider content-position="left">
                  <el-icon><Document /></el-icon> 参考来源 ({{ parseSources(msg.sources).length }}条)
                </el-divider>
                <div v-for="(s, j) in parseSources(msg.sources)" :key="j" class="source-item">
                  <div class="source-header" @click="s.expanded = !s.expanded">
                    <el-tag type="info" size="small">来源{{ s.index || j + 1 }}</el-tag>
                    <span class="source-name">《{{ s.source }}》</span>
                    <el-icon class="expand-icon" :class="{ expanded: s.expanded }">
                      <ArrowDown />
                    </el-icon>
                  </div>
                  <el-collapse-transition>
                    <div v-if="s.expanded" class="source-content">
                      <div class="content-label">原文内容：</div>
                      <div class="content-text">{{ s.content }}</div>
                    </div>
                  </el-collapse-transition>
                </div>
              </div>
            </div>
          </div>
          <div v-if="loading" class="message assistant">
            <div class="avatar"><el-icon :size="20"><MagicStick /></el-icon></div>
            <div class="bubble">
              <el-icon class="is-loading"><Loading /></el-icon> 思考中...
            </div>
          </div>
        </div>

        <div class="input-area">
          <el-input v-model="input" placeholder="输入你的问题，例如：挂科怎么重修？"
                    @keyup.enter="handleSend" :disabled="loading" size="large">
            <template #append>
              <el-button type="primary" @click="handleSend" :loading="loading">发送</el-button>
            </template>
          </el-input>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { User, MagicStick, Loading, Document, ArrowDown, Plus, Delete } from '@element-plus/icons-vue'
import { marked } from 'marked'
import { chat, getSessions, getSessionMessages, deleteSession as deleteSessionApi } from '@/api/ai'
import { ElMessage, ElMessageBox } from 'element-plus'

const input = ref('')
const loading = ref(false)
const messages = ref([])
const messagesRef = ref()
const sessions = ref([])
const currentSessionId = ref(null)

function renderMarkdown(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

function parseSources(sources) {
  if (!sources) return []
  if (typeof sources === 'string') {
    try { return JSON.parse(sources).map(s => ({ ...s, expanded: false })) } catch { return [] }
  }
  return sources.map(s => ({ ...s, expanded: false }))
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

async function loadSessions() {
  try {
    const res = await getSessions()
    sessions.value = res.data || []
  } catch (e) { /* ignore */ }
}

async function loadSession(sessionId) {
  currentSessionId.value = sessionId
  try {
    const res = await getSessionMessages(sessionId)
    messages.value = (res.data || []).map(m => ({
      role: m.role,
      content: m.content,
      sources: parseSources(m.sources)
    }))
    scrollToBottom()
  } catch (e) { /* ignore */ }
}

function handleNewSession() {
  currentSessionId.value = null
  messages.value = []
}

async function handleDeleteSession(sessionId) {
  try {
    await ElMessageBox.confirm('确定删除此会话？', '提示', { type: 'warning' })
    await deleteSessionApi(sessionId)
    ElMessage.success('已删除')
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = null
      messages.value = []
    }
    loadSessions()
  } catch (e) { /* cancelled */ }
}

async function handleSend() {
  const question = input.value.trim()
  if (!question || loading.value) return

  messages.value.push({ role: 'user', content: question })
  input.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await chat({ question, sessionId: currentSessionId.value })
    const data = res.data

    // 更新 sessionId
    if (data.sessionId && !currentSessionId.value) {
      currentSessionId.value = data.sessionId
      loadSessions()
    }

    messages.value.push({
      role: 'assistant',
      content: data.answer,
      sources: parseSources(data.sources)
    })
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，暂时无法回答您的问题，请稍后重试。'
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

onMounted(loadSessions)
</script>

<style scoped>
.ai-chat-container {
  display: flex;
  height: calc(100vh - 100px);
  gap: 0;
}

/* 侧边栏 */
.sidebar {
  width: 260px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 15px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 4px;
  position: relative;
  transition: background 0.2s;
}

.session-item:hover { background: #ecf5ff; }
.session-item.active { background: #d9ecff; }

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 20px;
}

.session-preview {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  position: absolute;
  top: 12px;
  right: 8px;
  color: #c0c4cc;
  display: none;
  cursor: pointer;
}

.session-item:hover .delete-btn { display: block; }
.delete-btn:hover { color: #f56c6c; }

/* 主聊天区 */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: none;
  border-radius: 0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.message.user { flex-direction: row-reverse; }

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message.user .avatar { background: #409eff; color: white; }

.bubble {
  max-width: 80%;
  padding: 12px 16px;
  border-radius: 12px;
  background: #f0f2f5;
  line-height: 1.6;
}

.message.user .bubble { background: #409eff; color: white; }

/* Markdown */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 12px 0 8px; font-weight: 600; }
.markdown-body :deep(h1) { font-size: 18px; }
.markdown-body :deep(h2) { font-size: 16px; }
.markdown-body :deep(h3) { font-size: 15px; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.markdown-body :deep(pre) { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 8px; overflow-x: auto; margin: 8px 0; }
.markdown-body :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-body :deep(blockquote) { border-left: 3px solid #409eff; padding-left: 12px; margin: 8px 0; color: #606266; }
.markdown-body :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #dcdfe6; padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: #f5f7fa; font-weight: 600; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid #e4e7ed; margin: 12px 0; }
.markdown-body :deep(strong) { font-weight: 600; }

/* 参考来源 */
.sources-section { margin-top: 12px; border-top: 1px dashed #dcdfe6; padding-top: 8px; }
.source-item { margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; }
.source-header { display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer; background: #fafafa; transition: background 0.2s; }
.source-header:hover { background: #f0f2f5; }
.source-name { font-weight: 500; color: #303133; flex: 1; }
.expand-icon { transition: transform 0.3s; color: #c0c4cc; }
.expand-icon.expanded { transform: rotate(180deg); }
.source-content { padding: 12px; background: #fff; border-top: 1px solid #ebeef5; }
.content-label { font-size: 12px; color: #909399; margin-bottom: 8px; font-weight: 500; }
.content-text { font-size: 13px; color: #606266; line-height: 1.8; white-space: pre-wrap; background: #f9f9f9; padding: 12px; border-radius: 6px; max-height: 300px; overflow-y: auto; }

.input-area { padding: 16px; border-top: 1px solid #ebeef5; }
</style>
