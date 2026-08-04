<script setup>
import MyNav from "../../../components/MyNav.vue";
import MyForm from "../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../api/index.js";
import {getResponseData} from "../../../request/index.js";
import {RULE} from "../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../router/index.js";

// 获取当前评论记录
let comment = JSON.parse(sessionStorage.getItem('row'));
// 集次列表选项
let episodeOptions = ref([]);
// 用户列表选项
let userOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理'},
  {icon: 'Comment', label: '评论列表', url: '/Comment'},
  {icon: 'Edit', label: '修改评论'},
];

/* ==================== 修改表单 ==================== */

// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '被评论视频集', prop: 'fkEpisodeId', required: true, type: 'select', options: episodeOptions, placeholder: '请选择要评论的视频'},
  {label: '评论人', prop: 'fkUserId', required: true, type: 'select', options: userOptions, placeholder: '请选择要评论人'},
  {label: '评论内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive(comment);
let rules = {content: RULE.CONTENT};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
  setTimeout(() => router.push('/Comment'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部集次并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'episode'}))).forEach(episode => {
    episodeOptions.value.push({label: episode['title'], value: episode['id']});
  });
  // 查询全部用户并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'user'}))).forEach(user => {
    userOptions.value.push({label: user['nickname'], value: user['id']});
  });
});

</script>

<template>
  <my-nav :items="navItems"/>
  <el-card class="comment-update-card" header="修改评论信息">
    <my-form type="update"
             :items="items"
             :rules="rules"
             :params="params"
             :api="updateApi"
             :args="{module: 'comment'}"
             :callback="updateSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.comment-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
