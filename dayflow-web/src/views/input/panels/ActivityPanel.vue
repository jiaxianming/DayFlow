<script setup lang="ts">
/**
 * 活动 CRUD 面板：列表 + 新增/编辑弹窗 + 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createActivity,
  deleteActivity,
  listActivities,
  updateActivity,
} from '@/api/activity'
import type {
  IActivityCreateDTO,
  IActivityQuery,
  IActivityUpdateDTO,
  IActivityVO,
} from '@/types/activity'
import type { ActivityCategory } from '@/types/enums'
import { formatDateTime } from '@/utils/format'

/** 活动类别选项（setup 顶层声明，template 可直接用） */
const CATEGORY_OPTIONS: { label: string; value: ActivityCategory }[] = [
  { label: '工作', value: 'WORK' },
  { label: '学习', value: 'STUDY' },
  { label: '会议', value: 'MEETING' },
  { label: '其他', value: 'OTHER' },
]

/** 类别值 → 中文标签 */
function categoryLabel(v: ActivityCategory): string {
  return CATEGORY_OPTIONS.find((o) => o.value === v)?.label || v
}

const list = ref<IActivityVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<IActivityQuery>({ page: 1, size: 10 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listActivities(query)
    list.value = page.records
    total.value = page.total
  } catch {
    // 拦截器已统一 ElMessage.error 提示，此处静默
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{
  id?: string
  content: string
  category: ActivityCategory | ''
  occurredAt?: string
}>({ content: '', category: '' })

const rules: FormRules = {
  content: [{ required: true, message: '请输入活动内容', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.content = ''
  form.category = ''
  form.occurredAt = undefined
  dialogVisible.value = true
}

function openEdit(row: IActivityVO): void {
  isEdit.value = true
  form.id = row.id
  form.content = row.content
  form.category = row.category
  form.occurredAt = row.occurredAt
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      const category = form.category as ActivityCategory
      if (isEdit.value && form.id) {
        const dto: IActivityUpdateDTO = {
          content: form.content,
          category,
          ...(form.occurredAt ? { occurredAt: form.occurredAt } : {}),
        }
        await updateActivity(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: IActivityCreateDTO = {
          content: form.content,
          category,
          ...(form.occurredAt ? { occurredAt: form.occurredAt } : {}),
        }
        await createActivity(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: IActivityVO): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除该活动？', '提示', { type: 'warning' })
  } catch {
    return // 用户取消，静默
  }
  await deleteActivity(row.id)
  ElMessage.success('已删除')
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
      <el-button type="primary" @click="openCreate">新增活动</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="content" label="内容" min-width="200" />
      <el-table-column label="类别" width="100">
        <template #default="{ row }">{{ categoryLabel(row.category as ActivityCategory) }}</template>
      </el-table-column>
      <el-table-column label="发生时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑活动' : '新增活动'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" placeholder="选择类别">
            <el-option v-for="opt in CATEGORY_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="发生时间" prop="occurredAt">
          <el-date-picker v-model="form.occurredAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空取当前时间" />
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
