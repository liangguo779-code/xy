<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>知识库管理</span>
          <div style="display: flex; gap: 8px;">
            <el-upload
              :show-file-list="false"
              :before-upload="handleUpload"
              accept=".txt,.pdf,.docx,.doc,.md"
            >
              <el-button type="primary" :loading="uploading">上传文档</el-button>
            </el-upload>
            <el-button :loading="rebuilding" @click="handleRebuild">{{ rebuildProgress || '重建索引' }}</el-button>
          </div>
        </div>
      </template>

      <div style="margin-bottom: 16px;">
        <el-tag type="info">支持格式: txt, pdf, docx, doc, md（非 MD 文件自动转换后入库）</el-tag>
      </div>

      <!-- 文档列表：开关控制该文档是否参与 AI 检索，编辑保存后后端会同步重新入库 -->
      <el-table :data="files" stripe v-loading="loading">
        <el-table-column prop="name" label="文件名" min-width="200" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== false"
              size="small"
              @change="handleToggle(row.name)"
            />
            <span style="margin-left: 4px; font-size: 12px; color: #999;">
              {{ row.enabled !== false ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button text type="success" size="small" @click="handleView(row.name)">查看</el-button>
            <el-button text type="primary" size="small" @click="handleEdit(row.name)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row.name)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 查看弹窗 -->
    <el-dialog v-model="viewVisible" :title="`查看: ${viewFilename}`" width="70%" top="5vh" destroy-on-close>
      <div class="view-container" v-loading="viewLoading">
        <div class="markdown-preview" v-html="viewHtml"></div>
      </div>
    </el-dialog>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="`编辑: ${editFilename}`" width="90%" top="3vh">
      <div class="edit-container">
        <div class="edit-left">
          <div class="edit-label">Markdown 源码</div>
          <el-input
            v-model="editContent"
            type="textarea"
            :rows="25"
            placeholder="Markdown 内容"
            style="font-family: monospace;"
          />
        </div>
        <div class="edit-right">
          <div class="edit-label">预览</div>
          <div class="markdown-preview" v-html="renderPreview(editContent)"></div>
        </div>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存并重新入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/** AI 知识库管理页：上传/启停/在线编辑/删除知识文档，重建向量索引并轮询进度；文档内容统一按 Markdown 存储与预览 */
import { ref, computed, onMounted } from 'vue'
import { marked } from 'marked'
import {
  getKnowledgeList, uploadKnowledge, getKnowledgeContent,
  updateKnowledgeContent, toggleKnowledge, deleteKnowledge,
  rebuildKnowledge, getRebuildStatus
} from '@/api/admin'
import { ElMessage, ElMessageBox } from 'element-plus'

const files = ref([])
const loading = ref(false)
const uploading = ref(false)
const rebuilding = ref(false)
/** 重建过程的进度文案，直接覆盖"重建索引"按钮的显示 */
const rebuildProgress = ref('')

const editVisible = ref(false)
const editFilename = ref('')
const editContent = ref('')
const saving = ref(false)

const viewVisible = ref(false)
const viewFilename = ref('')
const viewContent = ref('')
const viewLoading = ref(false)

/** 查看弹窗的 Markdown 渲染结果 */
const viewHtml = computed(() => {
  if (!viewContent.value) return ''
  return marked.parse(viewContent.value, { breaks: true })
})

/** 编辑区实时预览渲染；breaks 让单个换行也生效，贴近编辑手感 */
function renderPreview(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

/** 拉取知识库文件列表（含大小与启用状态） */
async function loadList() {
  loading.value = true
  try {
    const res = await getKnowledgeList()
    files.value = res.data?.files || []
  } finally {
    loading.value = false
  }
}

/** 上传文档并轮询等待后台入库；上传接口只负责落盘，分块入库在后台队列异步执行 */
async function handleUpload(file) {
  uploading.value = true
  const expectedName = file.name.replace(/\.[^.]+$/, '') // 去掉扩展名
  try {
    const res = await uploadKnowledge(file)
    ElMessage.success(res.message || '上传成功，后台处理中...')

    // 轮询列表，直到新文件出现（最多等 30 秒）
    for (let i = 0; i < 15; i++) {
      await new Promise(r => setTimeout(r, 2000))
      const listRes = await getKnowledgeList()
      const currentFiles = listRes.data?.files || []
      const found = currentFiles.some(f => f.name.includes(expectedName))
      if (found) {
        files.value = currentFiles
        ElMessage.success('文档已入库')
        break
      }
    }
  } catch (e) {
    // handled by interceptor
  } finally {
    uploading.value = false
  }
  // 返回 false 阻止 el-upload 的默认上传行为，实际上传由 uploadKnowledge 完成
  return false
}

/** 切换单文档启用状态，禁用后 RAG 检索会跳过该文档的分块 */
async function handleToggle(filename) {
  try {
    const res = await toggleKnowledge(filename)
    ElMessage.success(res.message)
    loadList()
  } catch (e) {
    // handled
  }
}

/** 查看文档内容，接口统一返回 Markdown（PDF/Word 也会转换） */
async function handleView(filename) {
  viewFilename.value = filename
  viewContent.value = ''
  viewLoading.value = true
  viewVisible.value = true
  try {
    const res = await getKnowledgeContent(filename)
    viewContent.value = res.data?.content || ''
  } catch (e) {
    viewContent.value = '加载失败'
  } finally {
    viewLoading.value = false
  }
}

/** 打开编辑弹窗，进入前先取最新内容再回填 */
async function handleEdit(filename) {
  try {
    const res = await getKnowledgeContent(filename)
    editFilename.value = filename
    editContent.value = res.data?.content || ''
    editVisible.value = true
  } catch (e) {
    // handled
  }
}

/** 保存内容，后端同步重新入库，接口返回时索引已刷新，无需再手动重建 */
async function handleSave() {
  saving.value = true
  try {
    const res = await updateKnowledgeContent(editFilename.value, editContent.value)
    ElMessage.success(res.message || '保存成功')
    editVisible.value = false
    loadList()
  } catch (e) {
    // handled
  } finally {
    saving.value = false
  }
}

/** 删除文档及其索引分块，弹窗仍提示重建索引以兜底清理残留数据 */
async function handleDelete(filename) {
  try {
    await ElMessageBox.confirm(`确认删除 "${filename}"？删除后需重建索引清理残留数据。`, '删除文档', { type: 'warning' })
    const res = await deleteKnowledge(filename)
    ElMessage.success(res.message || '删除成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') {
      // handled
    }
  }
}

/** 全量重建向量索引：后端异步执行，前端每 2 秒轮询进度，5 分钟超时兜底 */
async function handleRebuild() {
  rebuilding.value = true
  rebuildProgress.value = '重建中...'
  try {
    const res = await rebuildKnowledge()
    // running 表示已有重建任务在执行，直接退出避免重复触发
    if (res.status === 'running') {
      ElMessage.warning('重建正在进行中')
      rebuilding.value = false
      rebuildProgress.value = ''
      return
    }
    ElMessage.info(res.message || '开始重建...')

    let pollCount = 0
    const maxPolls = 150  // 2秒/次 × 150 = 5分钟超时

    // 轮询状态直到完成
    const poll = setInterval(async () => {
      pollCount++
      if (pollCount >= maxPolls) {
        clearInterval(poll)
        rebuilding.value = false
        rebuildProgress.value = ''
        ElMessage.warning('重建超时，请稍后刷新页面查看结果')
        return
      }
      try {
        const statusRes = await getRebuildStatus()
        if (statusRes.completed) {
          clearInterval(poll)
          rebuilding.value = false
          rebuildProgress.value = ''
          if (statusRes.error) {
            ElMessage.error('重建失败: ' + statusRes.error)
          } else {
            const removed = statusRes.result?.removed || 0
            const removedText = removed > 0 ? `, 清理残留 ${removed} 个来源` : ''
            ElMessage.success(`重建完成: ${statusRes.result?.docs || 0} 个文档, ${statusRes.result?.chunks || 0} 个 chunk${removedText}`)
          }
          loadList()
        } else {
          rebuildProgress.value = statusRes.progress || '重建中...'
        }
      } catch {
        clearInterval(poll)
        rebuilding.value = false
        rebuildProgress.value = ''
      }
    }, 2000)
  } catch (e) {
    rebuilding.value = false
    rebuildProgress.value = ''
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

onMounted(loadList)
</script>

<style scoped>
.view-container {
  max-height: 70vh;
  overflow-y: auto;
}
.edit-container {
  display: flex;
  gap: 16px;
  height: 65vh;
}
.edit-left {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.edit-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}
.edit-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
  padding: 0 4px;
}
.edit-left :deep(.el-textarea) {
  flex: 1;
}
.edit-left :deep(.el-textarea__inner) {
  height: 100%;
  resize: none;
}
.markdown-preview {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  background: #fff;
}
/* 预览内容由 v-html 生成，不带 scoped 属性，需 :deep 穿透才能修饰其内部标签 */
.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) { margin: 12px 0 8px; font-weight: 600; }
.markdown-preview :deep(h1) { font-size: 20px; }
.markdown-preview :deep(h2) { font-size: 17px; }
.markdown-preview :deep(h3) { font-size: 15px; }
.markdown-preview :deep(p) { margin: 8px 0; }
.markdown-preview :deep(ul),
.markdown-preview :deep(ol) { padding-left: 20px; margin: 8px 0; }
.markdown-preview :deep(table) { border-collapse: collapse; margin: 8px 0; width: 100%; }
.markdown-preview :deep(th),
.markdown-preview :deep(td) { border: 1px solid #dcdfe6; padding: 8px 12px; text-align: left; }
.markdown-preview :deep(th) { background: #f5f7fa; font-weight: 600; }
.markdown-preview :deep(code) { background: rgba(0,0,0,0.06); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.markdown-preview :deep(pre) { background: #1e1e1e; color: #d4d4d4; padding: 12px; border-radius: 8px; overflow-x: auto; }
.markdown-preview :deep(pre code) { background: none; padding: 0; color: inherit; }
.markdown-preview :deep(blockquote) { border-left: 3px solid #409eff; padding-left: 12px; margin: 8px 0; color: #606266; }
</style>
