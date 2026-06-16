<script setup>
import MyNav from "../../../../components/MyNav.vue";
import MyForm from "../../../../components/MyForm.vue";
import {onMounted, reactive, ref} from "vue";
import {simpleListApi, updateApi} from "../../../../api/index.js";
import {getResponseData} from "../../../../request/index.js";
import {RULE} from "../../../../const/index.js";
import {ElMessage} from "element-plus";
import router from "../../../../router/index.js";

// 当前父评论
let comment = JSON.parse(sessionStorage.getItem('row'));
let updatePageParam = JSON.parse(sessionStorage.getItem('updatePageParam'));
comment['parentContent'] = updatePageParam['parentContent'];
comment['episodeTitle'] = updatePageParam['episodeTitle'];
// 用户列表选项
let userOptions = ref([]);
// 路径导航
const navItems = [
  {icon: 'Notebook', label: '课程管理', url: '/Course'},
  {icon: 'Flag', label: '评论列表', url: '/Comment'},
  {icon: 'Flag', label: '回复列表', url: '/SubComment'},
  {icon: 'Edit', label: '修改回复记录'},
];
// 表单项 + 表单值 + 表单规则
let items = ref([
  {label: '视频集', prop: 'episodeTitle', disabled: true, span: 12},
  {label: '父评论内容', prop: 'parentContent', disabled: true, type: 'textarea'},
  {label: '评论人', prop: 'fkUserId', required: true, span: 12, type: 'select', options: userOptions, placeholder: '请选择评论人'},
  {label: '评论内容', prop: 'content', required: true, type: 'textarea'},
]);
let params = reactive(comment);
let rules = {content: RULE.CONTENT};

/* ==================== 修改成功后 ==================== */

function updateSuccess() {
  ElMessage.success('修改记录成功！');
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
  <el-card class="sub-comment-update-card" header="修改回复记录">
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
.sub-comment-update-card {
  width: 60%; // 宽度
  margin: 65px auto 0; // 外边距
}
</style>
