<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 所属秒杀活动ID和秒杀活动标题
let seckillId = sessionStorage.getItem('seckillId');
let seckillTitle = sessionStorage.getItem('seckillTitle');
// 全部课程下拉菜单选项
let courseOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Stopwatch', label: '秒杀列表', url: '/Seckill'},
  {icon: 'Stopwatch', label: `《${seckillTitle}》活动详情`, url: '/SeckillDetail'},
  {icon: 'Plus', label: '添加活动课程'},
];

/* ==================== 添加表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属活动', prop: 'seckillTitle', disabled: true, span: 12},
  {label: '活动课程', prop: 'fkCourseId', required: true, span: 12, type: 'select', options: courseOptions, placeholder: '请选择课程'},
  {label: '秒杀价格', prop: 'skPrice', type: 'number', min: 1, required: true, span: 12},
  {label: '秒杀数量', prop: 'skCount', type: 'number', min: 1, required: true, span: 12},
  {label: '秒杀描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive({seckillTitle, 'fkSeckillId': seckillId});
let rules = {info: RULE.INFO};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加成功！');
  setTimeout(() => router.push('/SeckillDetail'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部课程并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'course'}))).forEach(course => {
    courseOptions.value.push({label: course['title'] + '（￥' + course['price'] + '）', value: course['id']});
  });
});
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card v-if="courseOptions.length > 0" class="seckill-detail-insert-card" header="添加活动课程">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'seckillDetail'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.seckill-detail-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
