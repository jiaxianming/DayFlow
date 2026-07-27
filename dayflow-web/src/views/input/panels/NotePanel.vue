<script setup lang="ts">
/**
 * 学习笔记 CRUD 面板：列表 + 新增/编辑弹窗 + 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createNote, deleteNote, listNotes, updateNote } from '@/api/note'
import type { INoteCreateDTO, INoteQuery, INoteUpdateDTO, INoteVO } from '@/types/note'
import { formatDateTime } from '@/utils/format'

const list = ref<INoteVO[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<INoteQuery>({ page: 1, size: 10 })

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listNotes(query)
    list.value = page.records
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<{ id?: string; title: string; content: string; tags: string }>({
  title: '',
  content: '',
  tags: '',
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }],
}

function openCreate(): void {
  isEdit.value = false
  form.id = undefined
  form.title = ''
  form.content = ''
  form.tags = ''
  dialogVisible.value = true
}

function openEdit(row: INoteVO): void {
  isEdit.value = true
  form.id = row.id
  form.title = row.title
  form.content = row.content
  form.tags = row.tags || ''
  dialogVisible.value = true
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      if (isEdit.value && form.id) {
        const dto: INoteUpdateDTO = { title: form.title, content: form.content, tags: form.tags }
        await updateNote(form.id, dto)
        ElMessage.success('已更新')
      } else {
        const dto: INoteCreateDTO = { title: form.title, content: form.content, tags: form.tags }
        await createNote(dto)
        ElMessage.success('已新增')
      }
      dialogVisible.value = false
      await load()
    } catch {
      // 拦截器已提示
    }
  })
}

async function onDelete(row: INoteVO): Promise<void> {
  await ElMessageBox.confirm('确认删除该笔记？', '提示', { type: 'warning' })
  await deleteNote(row.id)
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
      <el-button type="primary" @click="openCreate">新增笔记</el-button>
    </div>
    <el-table v-loading="loading" :data="list" border>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="tags" label="标签" width="160" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑笔记' : '新增笔记'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
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
