<script setup lang="ts">
/**
 * Agent 协作时间线（核心卖点）
 * el-timeline 按 step 升序渲染 4 Agent 轨迹：四色区分、返工徽章、折叠摘要
 */
import { computed } from 'vue'
import type { IAgentTraceVO } from '@/types/report'
import type { AgentName } from '@/types/enums'

const props = defineProps<{
  /** 轨迹列表（将按 step 升序渲染） */
  traces: IAgentTraceVO[]
  /** 是否生成进行中（影响空轨迹占位文案） */
  active?: boolean
}>()

/** 各 Agent 角色元信息：中文标签 + 主题色 */
const AGENT_META: Record<AgentName, { label: string; color: string }> = {
  PLANNER: { label: '规划师', color: '#409eff' },
  COLLECTOR: { label: '采集员', color: '#67c23a' },
  WRITER: { label: '撰写员', color: '#e6a23c' },
  REVIEWER: { label: '评审员', color: '#f56c6c' },
}

/** 按 step 升序排序（不改原数组） */
const sortedTraces = computed<IAgentTraceVO[]>(() =>
  [...props.traces].sort((a, b) => a.step - b.step),
)
</script>

<template>
  <div class="agent-timeline">
    <div v-if="sortedTraces.length === 0" class="timeline-empty">
      {{ active ? 'Agent 即将开始协作…' : '暂无协作轨迹' }}
    </div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="(t, idx) in sortedTraces"
        :key="t.id"
        :color="AGENT_META[t.agentName]?.color || '#909399'"
        :timestamp="`耗时 ${t.latencyMs}ms · ${t.tokens} tokens`"
        placement="top"
        :hollow="active && idx === sortedTraces.length - 1"
      >
        <div class="trace-head">
          <span class="trace-agent" :style="{ color: AGENT_META[t.agentName]?.color }">
            {{ AGENT_META[t.agentName]?.label || t.agentName }}
          </span>
          <span class="trace-step">Step {{ t.step }}</span>
          <el-tag v-if="t.retryCount > 0" type="warning" size="small">
            返工 #{{ t.retryCount }}
          </el-tag>
        </div>
        <el-collapse>
          <el-collapse-item title="输入摘要">
            <div class="trace-summary">{{ t.inputSummary }}</div>
          </el-collapse-item>
          <el-collapse-item title="输出摘要">
            <div class="trace-summary">{{ t.outputSummary }}</div>
          </el-collapse-item>
        </el-collapse>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.agent-timeline {
  min-height: 120px;
}
.timeline-empty {
  padding: 32px 0;
  text-align: center;
  color: #909399;
}
.trace-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.trace-agent {
  font-weight: 600;
}
.trace-step {
  color: #606266;
  font-size: 13px;
}
.trace-summary {
  white-space: pre-wrap;
  word-break: break-word;
  color: #606266;
  font-size: 13px;
}
</style>
