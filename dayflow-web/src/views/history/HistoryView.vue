<script setup lang="ts">
/**
 * 历史报告列表：分页 + 状态 tag + 跳详情 + 删除 + 生成今日日报
 */
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteReport, pageReports } from '@/api/report'
import { useReportStore } from '@/stores/report'
import type { IReportQuery, IReportVO } from '@/types/report'
import type { ReportStatus } from '@/types/enums'
import { formatDateTime, todayString } from '@/utils/format'

const router = useRouter()
const reportStore = useReportStore()

const list = ref<IReportVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<IReportQuery>({ page: 1, size: 10 })

/** 状态 → tag 文案与颜色 */
const STATUS_TAG: Record<ReportStatus, { label: string; type: 'info' | 'success' | 'danger' }> = {
  GENERATING: { label: '生成中', type: 'info' },
  GENERATED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await pageReports(query)
    list.value = p.records
    total.value = p.total
  } catch {
    // 拦截器已 ElMessage.error 提示
  } finally {
    loading.value = false
  }
}

function onView(row: IReportVO): void {
  router.push('/reports/' + row.id)
}

async function onDelete(row: IReportVO): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除该报告？', '提示', { type: 'warning' })
    await deleteReport(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 用户取消确认：静默；API 失败由 axios 拦截器统一 ElMessage.error 提示
  }
}

async function onGenerate(): Promise<void> {
  try {
    const id = await reportStore.triggerGenerate({ type: 'DAILY', date: todayString() })
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示
  }
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="history-toolbar">
      <el-button type="primary" :loading="reportStore.isGenerating" @click="onGenerate">
        生成今日日报
      </el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ row.type === 'DAILY' ? '日报' : '周报' }}</template>
      </el-table-column>
      <el-table-column label="周期" width="200">
        <template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status as ReportStatus].type">
            {{ STATUS_TAG[row.status as ReportStatus].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tokenUsage" label="Token" width="100" />
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="onView(row)">查看</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="history-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />
  </div>
</template>

<style scoped>
.history-toolbar {
  margin-bottom: 12px;
}
.history-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
