<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {reactive, ref} from "vue";
import {updateApi} from "../../../api/index.js";
import {RULE, SECKILL_STATUS_OPTIONS} from "../../../const/index.js";
import router from "../../../router/index.js";
import {ElMessage} from "element-plus";

// 获取当前秒杀记录
let seckill = JSON.parse(sessionStorage.getItem('row'));
// 路径导航
const navItems = [
  {icon: 'Goods', label: '营销管理'},
  {icon: 'Stopwatch', label: '秒杀列表', url: '/Seckill'},
  {icon: 'Edit', label: '修改秒杀'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '活动标题', prop: 'title', required: true, span: 12},
  {label: '活动状态', prop: 'status', span: 12, type: 'select', required: true, options: SECKILL_STATUS_OPTIONS, placeholder: '请选择活动状态'},
  {label: '开始时间', prop: 'startTime', type: 'datetime', required: true, span: 12},
  {label: '结束时间', prop: 'endTime', type: 'datetime', required: true, span: 12},
  {label: '活动描述', prop: 'info', required: true, type: 'textarea'},
]);
let params = reactive(seckill);
let rules = {info: RULE.INFO};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Seckill'), 1000);
}
</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="seckill-update-card" header="修改秒杀活动信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'seckill'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.seckill-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
