<script setup lang="ts">
/**
 * Agent 协作时间线（占位，Task 8 实现）
 */
import type { IAgentTraceVO } from '@/types/report'

defineProps<{
  traces: IAgentTraceVO[]
  active?: boolean
}>()
</script>

<template>
  <div class="agent-timeline-placeholder">Agent 时间线（Task 8 实现）</div>
</template>

<style scoped>
.agent-timeline-placeholder {
  padding: 24px;
  color: #909399;
  text-align: center;
}
</style>
