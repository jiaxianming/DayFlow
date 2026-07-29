<script setup lang="ts">
/**
 * 报告查看页：顶部生成触发区 + 双栏（左 MarkdownView 按状态切换 / 右 AgentTimeline）
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useReportStore } from '@/stores/report'
import { useReportPolling } from '@/composables/useReportPolling'
import MarkdownView from '@/components/MarkdownView.vue'
import AgentTimeline from '@/components/AgentTimeline.vue'
import { todayString } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const reportStore = useReportStore()

/** 当前报告 id（来自路由 /reports/:id） */
const reportId = computed<string | undefined>(() => route.params.id as string | undefined)

/** 生成范围：今日(日报) / 本周(周报) / 指定日期(日报) */
const mode = ref<'today' | 'week' | 'custom'>('today')
const date = ref<string>(todayString())
const generating = ref(false)

/** 指定日期模式下禁止选择未来日期 */
function disabledFuture(d: Date): boolean {
  return d.getTime() > Date.now()
}

const { report, traces, isRunning, start, stop } = useReportPolling(reportId)

/** 据所选范围解析报告类型：本周 → WEEKLY，其余 → DAILY */
function resolveType(): 'WEEKLY' | 'DAILY' {
  return mode.value === 'week' ? 'WEEKLY' : 'DAILY'
}

/** 据所选范围解析目标日期：今日/本周 → 当天；指定日期 → 用户所选 */
function resolveDate(): string {
  return mode.value === 'custom' ? date.value : todayString()
}

/** 生成报告：triggerGenerate → 跳新 reportId */
async function onGenerate(): Promise<void> {
  generating.value = true
  try {
    const id = await reportStore.triggerGenerate({ type: resolveType(), date: resolveDate() })
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  if (reportId.value) {
    start()
  }
})

watch(reportId, (id) => {
  if (id) {
    start()
  } else {
    stop()
  }
})

onUnmounted(() => stop())
</script>

<template>
  <div class="report-view">
    <el-card class="generate-bar" shadow="never">
      <el-radio-group v-model="mode">
        <el-radio-button value="today">今日</el-radio-button>
        <el-radio-button value="week">本周</el-radio-button>
        <el-radio-button value="custom">指定日期</el-radio-button>
      </el-radio-group>
      <el-date-picker
        v-if="mode === 'custom'"
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        :disabled-date="disabledFuture"
        style="margin-left: 12px; width: 180px"
      />
      <el-button
        type="primary"
        :loading="generating || reportStore.isGenerating"
        :disabled="mode === 'custom' && !date"
        style="margin-left: 12px"
        @click="onGenerate"
      >
        生成报告
      </el-button>
    </el-card>

    <el-row :gutter="16" class="report-body">
      <el-col :span="15">
        <el-card shadow="never">
          <!-- 未加载/未生成 -->
          <div v-if="!report" class="report-empty">
            选择「今日 / 本周 / 指定日期」，点「生成报告」生成报告
          </div>
          <!-- 生成中 -->
          <div v-else-if="report.status === 'GENERATING'">
            <el-skeleton :rows="6" animated />
            <p class="report-hint">4 Agent 协作中… 已产出 {{ traces.length }} 条轨迹</p>
          </div>
          <!-- 生成成功 -->
          <MarkdownView
            v-else-if="report.status === 'GENERATED'"
            :content="report.content"
          />
          <!-- 生成失败 -->
          <el-result
            v-else-if="report.status === 'FAILED'"
            icon="error"
            title="报告生成失败"
            :sub-title="report.errorMsg || '请稍后重试'"
          >
            <template #extra>
              <el-button type="primary" @click="onGenerate">重新生成</el-button>
            </template>
          </el-result>
        </el-card>
      </el-col>
      <el-col :span="9">
        <el-card shadow="never">
          <template #header>Agent 协作时间线</template>
          <AgentTimeline :traces="traces" :active="isRunning" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.report-view {
  padding: 0 0 16px;
}
.generate-bar {
  margin-bottom: 16px;
}
.report-body {
  align-items: stretch;
}
.report-empty {
  padding: 48px 0;
  text-align: center;
  color: #909399;
}
.report-hint {
  margin-top: 12px;
  color: #606266;
}
</style>
