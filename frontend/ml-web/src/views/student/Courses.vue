<script setup>
import {onMounted, reactive, ref} from 'vue';
import {useRouter} from 'vue-router';
import {studentApi} from '../../api/student.js';
import {getResponseData} from '../../request/index.js';
import {MINIO_COURSE_COVER} from '../../const/index.js';
import './student.scss';

const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const courses = ref([]);
const pageInfo = reactive({pageNum: 1, pageSize: 12, total: 0});

async function page(pageNum = 1) {
  loading.value = true;
  try {
    const data = getResponseData(await studentApi.courseSearch({pageNum, pageSize: pageInfo.pageSize, keyword: keyword.value.trim()}));
    courses.value = data?.records || [];
    pageInfo.pageNum = data?.pageNum || data?.pageNumber || pageNum;
    pageInfo.total = data?.totalRow || data?.total || 0;
  } finally {
    loading.value = false;
  }
}

onMounted(() => page());
</script>

<template>
  <div class="student-page">
    <header class="student-header">
      <div><h2>课程大厅</h2><p>按兴趣和目标查找课程，进入详情后可试看、收藏或购买</p></div>
      <el-input v-model="keyword" clearable placeholder="搜索课程标题" style="width:320px" @keyup.enter="page(1)">
        <template #append><el-button icon="Search" @click="page(1)"/></template>
      </el-input>
    </header>

    <div v-loading="loading" class="course-grid">
      <article v-for="course in courses" :key="course.id" class="course-card">
        <img class="course-cover" :src="MINIO_COURSE_COVER(course.cover)" :alt="course.title">
        <div class="course-body">
          <h3>{{ course.title }}</h3>
          <p>{{ course.info }}</p>
          <div class="course-meta"><span>{{ course.categoryTitle || course.category?.title || course.author }}</span><strong class="price">￥{{ course.price }}</strong></div>
          <el-button type="primary" plain @click="router.push(`/student/course/${course.id}`)">查看详情</el-button>
        </div>
      </article>
    </div>

    <el-pagination layout="prev, pager, next, total" :current-page="pageInfo.pageNum" :page-size="pageInfo.pageSize"
                   :total="pageInfo.total" @current-change="page"/>
  </div>
</template>
