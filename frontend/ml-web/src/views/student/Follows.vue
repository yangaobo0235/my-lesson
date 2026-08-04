<script setup>
import {onMounted, reactive, ref} from 'vue';
import {ElMessage, ElMessageBox} from 'element-plus';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {readJsonStorage} from '../../util/auth.js';
import './student.scss';

const loginUser = readJsonStorage('loginUser', {});
const records = ref([]);
const pageInfo = reactive({pageNum: 1, pageSize: 10, total: 0});

async function page(pageNum = pageInfo.pageNum) {
  const data = getResponseData(await studentApi.follows({pageNum, pageSize: pageInfo.pageSize, fkUserId: loginUser.id})) || {};
  records.value = data.records || [];
  pageInfo.pageNum = data.pageNumber || data.pageNum || pageNum;
  pageInfo.total = data.totalRow || data.total || 0;
}

async function remove(id) {
  await ElMessageBox.confirm('确定取消收藏吗？');
  if (getResponseData(await studentApi.removeFollow(id))) {
    ElMessage.success('已取消收藏');
    await page();
  }
}

onMounted(() => page(1));
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div><h2>我的收藏</h2><p>收藏的课程集次会集中在这里，便于后续学习时快速定位</p></div>
    </header>
    <section class="panel">
      <el-table :data="records">
        <el-table-column prop="episode.title" label="集次" min-width="180"/>
        <el-table-column prop="episode.season.title" label="季次" min-width="160"/>
        <el-table-column prop="episode.season.course.title" label="课程" min-width="180"/>
        <el-table-column prop="username" label="用户" width="140"/>
        <el-table-column label="操作" width="120">
          <template #default="{row}">
            <el-button text type="danger" @click="remove(row.id)">取消收藏</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination layout="prev, pager, next, total" :current-page="pageInfo.pageNum" :page-size="pageInfo.pageSize"
                     :total="pageInfo.total" @current-change="page"/>
    </section>
  </div>
</template>
