<script setup lang="ts">
/**
 * 数据录入页：Activity / Note / Task 三 tab
 */
import { ref } from 'vue'
import ActivityPanel from './panels/ActivityPanel.vue'
import NotePanel from './panels/NotePanel.vue'
import TaskPanel from './panels/TaskPanel.vue'

const activeTab = ref<'activity' | 'note' | 'task'>('activity')
</script>

<template>
  <el-tabs v-model="activeTab">
    <el-tab-pane label="活动" name="activity">
      <ActivityPanel />
    </el-tab-pane>
    <el-tab-pane label="笔记" name="note">
      <NotePanel />
    </el-tab-pane>
    <el-tab-pane label="任务" name="task">
      <TaskPanel />
    </el-tab-pane>
  </el-tabs>
</template>
