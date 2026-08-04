<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 获取当前秒杀详情记录
let seckillDetail = JSON.parse(sessionStorage.getItem('row'));
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
  {icon: 'Edit', label: '修改秒杀详情'},
];

/* ==================== 修改表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '所属活动', prop: 'seckillTitle', disabled: true, span: 12},
  {label: '活动课程', prop: 'fkCourseId', required: true, span: 12, type: 'select', options: courseOptions, placeholder: '请选择课程'},
  {label: '秒杀价格', prop: 'skPrice', type: 'number', min: 1, required: true, span: 12},
  {label: '秒杀数量', prop: 'skCount', type: 'number', min: 1, required: true, span: 12},
  {label: '秒杀描述', prop: 'info', type: 'textarea'},
]);
let params = reactive(seckillDetail);
let rules = {info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
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
  <el-card v-if="courseOptions.length > 0" class="sub-seckill-detail-update-card" header="修改秒杀详情信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'seckillDetail'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.sub-seckill-detail-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
