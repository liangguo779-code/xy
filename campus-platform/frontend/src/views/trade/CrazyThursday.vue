<template>
  <div class="crazy-thursday">
    <!-- 主视觉区：倒计时、报名、开奖结果按活动状态三选一展示 -->
    <div class="hero-section">
      <div class="kfc-icon">🍗</div>
      <h1>疯狂星期四</h1>
      <p class="subtitle">每周四 11:00 开放报名，12:00 系统开奖，限10个名额！</p>

      <!-- 倒计时 -->
      <div v-if="!status.isRegisterTime && !status.winner" class="countdown-box">
        <p class="countdown-label">距离开抢还有</p>
        <div class="countdown">
          <div class="time-unit">
            <span class="num">{{ days }}</span>
            <span class="txt">天</span>
          </div>
          <div class="time-unit">
            <span class="num">{{ hours }}</span>
            <span class="txt">时</span>
          </div>
          <div class="time-unit">
            <span class="num">{{ minutes }}</span>
            <span class="txt">分</span>
          </div>
          <div class="time-unit">
            <span class="num">{{ seconds }}</span>
            <span class="txt">秒</span>
          </div>
        </div>
      </div>

      <!-- 报名区 -->
      <div v-if="status.isRegisterTime && !status.winner" class="register-section">
        <div class="slots-bar">
          <div class="slots-label">名额：{{ status.registeredCount }}/{{ status.maxSlots }}</div>
          <el-progress :percentage="(status.registeredCount / status.maxSlots) * 100"
                       :stroke-width="20" :format="() => `${status.remainingSlots} 个剩余`" />
        </div>

        <div class="participants">
          <div v-for="(p, i) in status.participants" :key="i" class="participant">
            <el-avatar :size="36">U{{ p.userId }}</el-avatar>
            <span class="slot-num">#{{ i + 1 }}</span>
          </div>
          <div v-for="i in status.remainingSlots" :key="'empty-' + i" class="participant empty">
            <el-avatar :size="36" style="background: #f0f0f0; color: #ccc">?</el-avatar>
            <span class="slot-num">#{{ status.registeredCount + i }}</span>
          </div>
        </div>

        <el-button v-if="!status.alreadyRegistered" type="danger" size="large" class="register-btn"
                   @click="handleRegister" :loading="registering" :disabled="status.remainingSlots <= 0">
          <span v-if="status.remainingSlots > 0">🍗 立即报名</span>
          <span v-else>名额已满</span>
        </el-button>
        <el-button v-else type="info" size="large" disabled>
          已报名，等待开奖
        </el-button>
      </div>

      <!-- 开奖结果 -->
      <div v-if="status.winner" class="result-section">
        <div class="winner-card">
          <div class="winner-icon">🎉</div>
          <p class="winner-text">本周幸运儿</p>
          <p class="winner-user">用户 #{{ status.winner.userId }}</p>
          <p class="next-time">恭喜中奖！请私信管理员领取肯德基套餐</p>
        </div>
      </div>
    </div>

    <!-- 规则说明 -->
    <div class="rules-section">
      <el-card>
        <template #header>活动规则</template>
        <ul class="rules-list">
          <li>每周四 11:00 - 12:00 开放报名，限 10 个名额</li>
          <li>周四 12:00 系统自动从报名者中随机抽取 1 名幸运儿</li>
          <li>中奖者请私信管理员领取肯德基套餐</li>
          <li>每人每周限报名一次</li>
          <li>无人报名时奖品累积到下周</li>
        </ul>
      </el-card>
    </div>
  </div>
</template>

<script setup>
/**
 * 疯狂星期四活动页：每周四 11:00 开放报名、12:00 自动开奖，限 10 个名额
 * 倒计时每秒递减，归零后重新拉取状态以切换报名或开奖展示
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

/** 活动状态取自后端 status 接口，countdown 为距下一时段的剩余毫秒数 */
const status = ref({
  countdown: 0,
  isRegisterTime: false,
  alreadyRegistered: false,
  registeredCount: 0,
  maxSlots: 10,
  remainingSlots: 10,
  participants: [],
  winner: null,
  weekKey: '',
  status: 0
})
const registering = ref(false)
/** 倒计时定时器句柄，组件卸载时清除避免泄漏 */
let timer = null

/** 将剩余毫秒拆分为天/时/分/秒展示 */
const days = computed(() => Math.floor(status.value.countdown / 86400000))
const hours = computed(() => Math.floor((status.value.countdown % 86400000) / 3600000))
const minutes = computed(() => Math.floor((status.value.countdown % 3600000) / 60000))
const seconds = computed(() => Math.floor((status.value.countdown % 60000) / 1000))

/** 拉取活动状态（报名名单、剩余名额、中奖结果） */
async function loadStatus() {
  try {
    const res = await request.get('/api/crazy-thursday/status')
    status.value = res.data
  } catch (e) { /* ignore */ }
}

/** 每秒递减倒计时，归零后重新拉取状态以响应时段切换 */
function startCountdown() {
  timer = setInterval(() => {
    if (status.value.countdown > 0) {
      status.value.countdown -= 1000
    } else {
      // 归零意味着刚进入报名期或已开奖，以服务端状态为准刷新展示
      loadStatus()
    }
  }, 1000)
}

/** 提交报名，res.data.success 区分业务失败（重复报名、名额已满等） */
async function handleRegister() {
  registering.value = true
  try {
    const res = await request.post('/api/crazy-thursday/register')
    if (res.data.success) {
      ElMessage.success(res.data.message)
      loadStatus()
    } else {
      ElMessage.error(res.msg || '报名失败')
    }
  } catch (e) { /* handled */ } finally {
    registering.value = false
  }
}

onMounted(() => {
  loadStatus()
  startCountdown()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.crazy-thursday {
  max-width: 600px;
  margin: 0 auto;
  padding: 20px;
}

.hero-section {
  text-align: center;
  padding: 40px 0;
}

.kfc-icon { font-size: 80px; margin-bottom: 16px; }
.hero-section h1 { font-size: 36px; color: #f56c6c; margin-bottom: 8px; }
.subtitle { color: #909399; font-size: 16px; margin-bottom: 32px; }

.countdown-box { margin: 32px 0; }
.countdown-label { color: #606266; margin-bottom: 16px; }
.countdown { display: flex; justify-content: center; gap: 16px; }
.time-unit { display: flex; flex-direction: column; align-items: center; }
.num {
  font-size: 48px; font-weight: 700; color: #f56c6c;
  background: #fff0f0; border-radius: 12px;
  width: 80px; height: 80px;
  display: flex; align-items: center; justify-content: center;
}
.txt { font-size: 14px; color: #909399; margin-top: 4px; }

.register-section { margin: 32px 0; }
.slots-bar { margin-bottom: 24px; }
.slots-label { font-size: 16px; font-weight: 500; margin-bottom: 8px; }

.participants {
  display: flex; flex-wrap: wrap; gap: 12px;
  justify-content: center; margin: 24px 0;
}
.participant {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
}
.slot-num { font-size: 12px; color: #909399; }

/* pulse 呼吸动画持续强调报名按钮，引导用户点击 */
.register-btn {
  font-size: 20px; padding: 16px 48px; height: auto;
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.result-section { margin: 32px 0; }
.winner-card {
  background: linear-gradient(135deg, #ffecd2, #fcb69f);
  border-radius: 16px; padding: 32px;
}
.winner-icon { font-size: 48px; }
.winner-text { font-size: 18px; color: #303133; margin: 8px 0; }
.winner-user { font-size: 24px; font-weight: 700; color: #f56c6c; }
.next-time { color: #909399; margin-top: 8px; }

.rules-section { margin-top: 32px; }
.rules-list { padding-left: 20px; line-height: 2; color: #606266; }
</style>
