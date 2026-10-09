<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>封禁管理</span>
          <el-button type="danger" @click="showBanDialog = true">封禁用户</el-button>
        </div>
      </template>

      <!-- 筛选 -->
      <div style="margin-bottom: 16px; display: flex; gap: 12px">
        <el-select v-model="filterTargetType" placeholder="封禁类型" clearable @change="loadData" style="width: 120px">
          <el-option label="用户" value="user" />
          <el-option label="IP" value="ip" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable @change="loadData" style="width: 120px">
          <el-option label="生效中" :value="1" />
          <el-option label="已解除" :value="0" />
        </el-select>
      </div>

      <!-- 封禁记录列表：类型 user/ip，能力 all/trade/message/forum，状态 1-生效中 0-已解除 -->
      <el-table :data="list" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="targetType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.targetType === 'user' ? '' : 'warning'" size="small">
              {{ row.targetType === 'user' ? '用户' : 'IP' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetValue" label="目标" width="120" />
        <el-table-column prop="banType" label="封禁能力" width="120">
          <template #default="{ row }">
            <el-tag :type="banTypeTag(row.banType)" size="small">{{ banTypeText(row.banType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" show-overflow-tooltip />
        <el-table-column prop="banUntil" label="截止时间" width="160" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
              {{ row.status === 1 ? '生效中' : '已解除' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" text type="success" size="small" @click="handleUnban(row.id)">
              解封
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 16px" :current-page="page" :page-size="20"
                     :total="total" @current-change="p => { page = p; loadData() }" />
    </el-card>

    <!-- 封禁弹窗 -->
    <el-dialog v-model="showBanDialog" title="封禁用户" width="480">
      <el-form :model="banForm" label-width="100px">
        <el-form-item label="封禁对象">
          <el-radio-group v-model="banForm.targetType">
            <el-radio value="user">用户ID</el-radio>
            <el-radio value="ip">IP地址</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="banForm.targetType === 'user' ? '用户ID' : 'IP地址'">
          <el-input v-model="banForm.targetValue" :placeholder="banForm.targetType === 'user' ? '输入用户ID' : '输入IP地址'" />
        </el-form-item>
        <el-form-item label="封禁能力">
          <el-select v-model="banForm.banType" style="width: 100%">
            <el-option label="全部封禁" value="all" />
            <el-option label="禁止交易" value="trade" />
            <el-option label="禁止私信" value="message" />
            <el-option label="禁止论坛" value="forum" />
          </el-select>
        </el-form-item>
        <el-form-item label="封禁天数">
          <el-input-number v-model="banForm.days" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="封禁原因">
          <el-input v-model="banForm.reason" type="textarea" :rows="2" placeholder="请输入封禁原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBanDialog = false">取消</el-button>
        <el-button type="danger" @click="handleBan" :loading="banning">确认封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 封禁管理：对用户或 IP 封禁（可限定能力范围与天数），并可手动解封
 * targetType user/ip；banType all-全部 trade-交易 message-私信 forum-论坛；status 1-生效中 0-已解除
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const list = ref([])
const page = ref(1)
const total = ref(0)
const filterTargetType = ref('')
/** 状态筛选默认 1-只看生效中，清空则查全部 */
const filterStatus = ref(1)
const showBanDialog = ref(false)
const banning = ref(false)

/** 封禁表单：targetType 决定提交接口与 targetValue 含义（用户 ID 需转数字） */
const banForm = ref({
  targetType: 'user',
  targetValue: '',
  banType: 'all',
  days: 7,
  reason: ''
})

/** 手动拼接的鉴权头，fetch 未走统一请求封装，token 需自行携带 */
const headers = { Authorization: `Bearer ${localStorage.getItem('token')}` }

/** 封禁能力文案映射，未知类型原样展示 */
const banTypeText = (t) => ({ all: '全部', trade: '交易', message: '私信', forum: '论坛' }[t] || t)
/** 封禁能力对应的标签色映射 */
const banTypeTag = (t) => ({ all: 'danger', trade: 'warning', message: '', forum: 'info' }[t] || '')

/** 分页加载封禁记录，筛选项为空时不传对应参数 */
async function loadData() {
  const params = { page: page.value, size: 20 }
  if (filterTargetType.value) params.targetType = filterTargetType.value
  // status 可能为 0-已解除，必须用 !== '' 判断，否则 0 会被当成未筛选而丢参
  if (filterStatus.value !== '') params.status = filterStatus.value

  const query = new URLSearchParams(params).toString()
  const res = await fetch(`/api/admin/bans?${query}`, { headers })
  const data = await res.json()
  list.value = data.data?.records || []
  total.value = data.data?.total || 0
}

/** 提交封禁：用户与 IP 走不同接口且 body 字段不同，后端约定 code 200 为成功 */
async function handleBan() {
  if (!banForm.value.targetValue || !banForm.value.reason) {
    ElMessage.warning('请填写完整信息')
    return
  }
  banning.value = true
  try {
    // 用户封禁要求 userId 为数字，故对 targetValue 做 Number 转换
    const url = banForm.value.targetType === 'user' ? '/api/admin/bans/user' : '/api/admin/bans/ip'
    const body = banForm.value.targetType === 'user'
      ? { userId: Number(banForm.value.targetValue), banType: banForm.value.banType, reason: banForm.value.reason, days: banForm.value.days }
      : { ip: banForm.value.targetValue, banType: banForm.value.banType, reason: banForm.value.reason, days: banForm.value.days }

    const res = await fetch(url, { method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const data = await res.json()
    if (data.code === 200) {
      ElMessage.success('封禁成功')
      showBanDialog.value = false
      banForm.value = { targetType: 'user', targetValue: '', banType: 'all', days: 7, reason: '' }
      loadData()
    } else {
      ElMessage.error(data.msg)
    }
  } finally {
    banning.value = false
  }
}

/** 解封指定记录，后端将状态置为 0-已解除；仅生效中的记录展示该按钮 */
async function handleUnban(id) {
  const res = await fetch(`/api/admin/bans/${id}/unban`, { method: 'PUT', headers })
  const data = await res.json()
  if (data.code === 200) {
    ElMessage.success('已解封')
    loadData()
  }
}

onMounted(loadData)
</script>
