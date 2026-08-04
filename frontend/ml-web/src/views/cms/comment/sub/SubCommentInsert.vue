<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {insertApi, simpleListApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const";
import {ElMessage} from "element-plus";
import router from "../../../../router";

// 当前父评论ID，父评论内容，视频集ID，视频集标题
let insertPageParam = JSON.parse(sessionStorage.getItem('insertPageParam'));
let pid = insertPageParam['pid'];
let parentContent = insertPageParam['parentContent'];
let episodeId = insertPageParam['episodeId'];
let episodeTitle = insertPageParam['episodeTitle'];
// 用户列表选项
let userOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理', url: '/Course'},
  {icon: 'Flag', label: '评论列表', url: '/Comment'},
  {icon: 'Flag', label: '回复列表', url: '/SubComment'},
  {icon: 'Plus', label: '添加回复'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '视频集', prop: 'episodeTitle', disabled: true, span: 12},
  {label: '父评论内容', prop: 'parentContent', disabled: true, type: 'textarea'},
  {label: '评论人', prop: 'fkUserId', required: true, span: 12, type: 'select', options: userOptions, placeholder: '请选择评论人'},
  {label: '评论内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive({
  fkEpisodeId: episodeId,
  episodeTitle,
  parentContent,
  pid,
  info: '暂无描述'
});
let rules = {content: RULE.CONTENT};

/* ==================== 添加成功后 ==================== */

function insertSuccess() {
  ElMessage.success('添加记录成功！');
  setTimeout(() => router.push('/SubComment'), 1000);
}

/* ==================== 加载函数 ==================== */

onMounted(async () => {
  // 查询全部用户并添加到下拉菜单选项中
  Object.values(getResponseData(await simpleListApi(null, {module: 'user'}))).forEach(user => {
    userOptions.value.push({label: user['nickname'], value: user['id']});
  });
});
</script>

<template v-if="userOptions">
  <my-nav :items="navItems"/>
  <el-card class="sub-comment-insert-card" header="添加二级评论">
    <my-form type="insert"
             :items="items"
             :rules="rules"
             :params="params"
             :api="insertApi"
             :args="{module: 'comment'}"
             :callback="insertSuccess"/>
  </el-card>
</template>

<style scoped lang="scss">
.sub-comment-insert-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
