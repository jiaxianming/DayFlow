<script setup lang="ts">
/**
 * 404 页
 */
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <el-result icon="warning" title="404" sub-title="您访问的页面不存在">
    <template #extra>
      <el-button type="primary" @click="router.push('/input')">返回首页</el-button>
    </template>
  </el-result>
</template>
