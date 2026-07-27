<script setup lang="ts">
/**
 * 任务 CRUD 面板：列表 + 状态筛选 + 新增/编辑弹窗 + 删除 + 完成
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { completeTask, createTask, deleteTask, listTasks, updateTask } from '@/api/task'
import type { ITaskCreateDTO, ITaskQuery, ITaskUpdateDTO, ITaskVO } from '@/types/task'
import type { TaskStatus } from '@/types/enums'
import { formatDateTime } from '@/utils/format'

/** 任务状态选项（setup 顶层声明，template 可直接用） */
const STATUS_OPTIONS: { label: string; value: TaskStatus; tagType: 'info' | 'success' | 'warning' }[] = [
  { label: '待办', value: 'TODO', tagType: 'info' },
  { label: '进行中', value: 'DOING', tagType: 'warning' },
  { label: '已完成', value: 'DONE', tagType: 'success' },
]

/** 状态值 → 中文标签 */
function statusLabel(v: TaskStatus): string {
  return STATUS_OPTIONS.find((o) => o.value === v)?.label || v
}

/** 状态值 → el-tag type */
function statusTagType(v: TaskStatus): 'info' | 'success' | 'warning' {
  return STATUS_OPTIONS.find((o) => o.value === v)?.tagType ?? 'info'
}

const list = ref<ITaskVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<ITaskQuery>({ page: 1, size: 10 })
const statusFilter = ref<TaskStatus | ''>('')

async function load(): Promise<void> {
  loading.value = true
  try {
    const q: ITaskQuery = { page: query.page, size: query.size }
    if (statusFilter.value) {
      q.status = statusFilter.value
    }
    const page = await listTasks(q)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

function onFilter(): void {
  query.page = 1
  load()
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ id?: string; title: string; status: TaskStatus }>({
  title: '',
  status: 'TODO',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入任务标题', trigger: 'blur' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.title = ''
  form.status = 'TODO'
  dialogVisible.value = true
}

function openEdit(row: ITaskVO): void {
  isEdit.value = true
  form.id = row.id
  form.title = row.title
  form.status = row.status
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value && form.id) {
        const dto: ITaskUpdateDTO = { title: form.title, status: form.status }
        await updateTask(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: ITaskCreateDTO = { title: form.title, status: form.status }
        await createTask(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: ITaskVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该任务？', '提示', { type: 'warning' })
  await deleteTask(row.id)
  ElMessage.success('已删除')
  await load()
}

async function onComplete(row: ITaskVO): Promise<void> {
  await completeTask(row.id)
  ElMessage.success('已标记完成')
  await load()
}

function onPageChange(p: number): void {
  query.page = p
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel-toolbar">
      <el-button type="primary" @click="openCreate">新增任务</el-button>
      <el-select
        v-model="statusFilter"
        placeholder="按状态筛选"
        clearable
        style="margin-left: 12px; width: 140px"
        @change="onFilter"
      >
        <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status as TaskStatus)">
            {{ statusLabel(row.status as TaskStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完成时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.completedAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== 'DONE'"
            link
            type="success"
            @click="onComplete(row)"
          >
            完成
          </el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      class="panel-pagination"
      background
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="onPageChange"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑任务' : '新增任务'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel-toolbar {
  margin-bottom: 12px;
}
.panel-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
