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
            <el-button :loading="rebuilding" @click="handleRebuild">重建索引</el-button>
          </div>
        </div>
      </template>

      <div style="margin-bottom: 16px;">
        <el-tag type="info">支持格式: txt, pdf, docx, doc, md（非 MD 文件自动转换后入库）</el-tag>
      </div>

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
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="handleEdit(row.name)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row.name)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
import { ref, onMounted } from 'vue'
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

const editVisible = ref(false)
const editFilename = ref('')
const editContent = ref('')
const saving = ref(false)

function renderPreview(text) {
  if (!text) return ''
  return marked.parse(text, { breaks: true })
}

async function loadList() {
  loading.value = true
  try {
    const res = await getKnowledgeList()
    files.value = res.data?.files || []
  } finally {
    loading.value = false
  }
}

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
  return false
}

async function handleToggle(filename) {
  try {
    const res = await toggleKnowledge(filename)
    ElMessage.success(res.message)
    loadList()
  } catch (e) {
    // handled
  }
}

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

async function handleRebuild() {
  rebuilding.value = true
  try {
    const res = await rebuildKnowledge()
    if (res.status === 'running') {
      ElMessage.warning('重建正在进行中')
      rebuilding.value = false
      return
    }
    ElMessage.info(res.message || '开始重建...')

    // 轮询状态直到完成
    const poll = setInterval(async () => {
      try {
        const statusRes = await getRebuildStatus()
        if (statusRes.completed) {
          clearInterval(poll)
          rebuilding.value = false
          if (statusRes.error) {
            ElMessage.error('重建失败: ' + statusRes.error)
          } else {
            ElMessage.success(`重建完成: ${statusRes.result?.docs || 0} 个文档, ${statusRes.result?.chunks || 0} 个 chunk`)
          }
          loadList()
        } else {
          // 可以在这里更新进度提示
          console.log('重建进度:', statusRes.progress)
        }
      } catch {
        clearInterval(poll)
        rebuilding.value = false
      }
    }, 2000)
  } catch (e) {
    rebuilding.value = false
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
