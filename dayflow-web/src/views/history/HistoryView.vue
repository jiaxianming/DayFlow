<script setup lang="ts">
/**
 * 历史报告列表：分页 + 状态 tag + 跳详情 + 删除 + 生成报告（对话框选今日/本周/指定日期）
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

/** 生成对话框可见性 */
const dialogVisible = ref(false)
/** 生成范围：今日(日报) / 本周(周报) / 指定日期(日报) */
const mode = ref<'today' | 'week' | 'custom'>('today')
const date = ref<string>(todayString())

/** 指定日期模式下禁止选择未来日期 */
function disabledFuture(d: Date): boolean {
  return d.getTime() > Date.now()
}

/** 据所选范围解析报告类型：本周 → WEEKLY，其余 → DAILY */
function resolveType(): 'WEEKLY' | 'DAILY' {
  return mode.value === 'week' ? 'WEEKLY' : 'DAILY'
}

/** 据所选范围解析目标日期：今日/本周 → 当天；指定日期 → 用户所选 */
function resolveDate(): string {
  return mode.value === 'custom' ? date.value : todayString()
}

/** 打开生成对话框：每次重置为今日，避免上次选择残留 */
function openGenerate(): void {
  mode.value = 'today'
  date.value = todayString()
  dialogVisible.value = true
}

/** 确认生成：按所选日期触发，成功后关对话框并跳详情 */
async function onGenerate(): Promise<void> {
  try {
    const id = await reportStore.triggerGenerate({ type: resolveType(), date: resolveDate() })
    dialogVisible.value = false
    router.push('/reports/' + id)
  } catch {
    // 拦截器已提示；失败时保留对话框供重试
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
      <el-button type="primary" :loading="reportStore.isGenerating" @click="openGenerate">
        生成报告
      </el-button>
    </div>

    <!-- 生成报告对话框：先选范围（今日 / 本周 / 指定日期）再确认，避免一进来就误触生成 -->
    <el-dialog v-model="dialogVisible" title="生成报告" width="420px">
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
        style="width: 100%; margin-top: 12px"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="reportStore.isGenerating"
          :disabled="mode === 'custom' && !date"
          @click="onGenerate"
        >
          生成
        </el-button>
      </template>
    </el-dialog>
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
