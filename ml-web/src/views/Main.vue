<script setup>
import router from "../router/index.js";
import {ElNotification} from "element-plus";
import {MINIO_HOST, PROJECT_INFO, PROJECT_SKILLS} from "../const/index.js";
import MyIcon from "../components/MyIcon.vue";
import {computed, ref} from "vue";
import {
  getAccessibleAiNavItems,
  getAccessibleBusinessMenus,
  getAccessibleStudentNavItems,
  getLoginMenus,
  getLoginRoles,
  readJsonStorage
} from "../util/auth.js";
import {applyTheme, getSavedTheme} from "../util/theme.js";

// 当前登录的用户信息
const loginUser = readJsonStorage('loginUser', {});
// 当前登录的菜单列表
const menus = getLoginMenus();
const roles = getLoginRoles();
const aiMenus = computed(() => getAccessibleAiNavItems(roles));
const studentMenus = computed(() => getAccessibleStudentNavItems());
const businessMenus = computed(() => getAccessibleBusinessMenus(menus, roles));
// 当前登录的用户头像
const avatar = MINIO_HOST + '/avatar/' + loginUser['avatar'];
// 项目LOGO
const logo = MINIO_HOST + '/logo.jpg';
// 当前选中菜单的index值：默认选中当前路由路径
let currentMenuIndex = ref(router.currentRoute.value['path']);
// 左侧菜单列表是否折叠：向左收缩
const isCollapse = ref(false);
// 项目信息抽屉 + 项目技术栈抽屉 + 日历抽屉
const projectInfoDrawer = ref();
const projectSkillDrawer = ref();
const calendarDrawer = ref();
const theme = ref(getSavedTheme());
// 日历数据（本地时间）
let calendarData = ref(new Date());

/**
 * 打开信息抽屉
 */
function openProjectInfoDrawer() {
  projectInfoDrawer.value = true;
}

/**
 * 打开技术栈抽屉
 */
function openProjectSkillDrawer() {
  projectSkillDrawer.value = true;
}

/**
 * 打开日历抽屉
 */
function openCalendarDrawer() {
  calendarDrawer.value = true;
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark';
  applyTheme(theme.value);
}

/**
 * 系统通知
 *
 * 1. 使用 ElNotification 在右下角通知：“暂无通知消息”
 */
function notify() {
  ElNotification.info({
    title: '通知列表',
    message: '暂无通知消息',
    position: 'bottom-right'
  });
}

/**
 * 跳入Personal页面
 */
function toPersonal() {
  router.push('/Personal');
}

/**
 * 跳入PersonalUpdate页面
 */
function toPersonalUpdate() {
  router.push('/PersonalUpdate');
}

/**
 * 跳入PersonalUpdatePhone页面
 */
function toPersonalUpdatePhone() {
  router.push('/PersonalUpdatePhone');
}

/**
 * 退出登录
 *
 * 1. 跳转到登录页面（登录页面的加载函数中会清空sessionStorage信息并使用 vuex 修改登录状态，此处无需处理）
 */
function logout() {
  router.push('/');
}
</script>

<template>
  <el-container class="main-body" v-if="menus">
    <el-aside class="main-body-left"
              width="collapse" max-width="200px">
      <el-menu class="menus-menu el-menu-vertical-demo" :collapse="isCollapse" :default-active="currentMenuIndex"
               unique-opened router>
        <el-image class="logo" :src="logo"/>
        <el-menu-item class="house-item" index="/Dashboard"
                      title="回到后台项目首页">
          <my-icon icon="House" label="Dashboard"/>
        </el-menu-item>
        <el-sub-menu v-if="aiMenus.length > 0" index="ai-console" title="AI 工作台">
          <template #title>
            <my-icon icon="ChatDotRound" label="AI 工作台"/>
          </template>
          <el-menu-item v-for="item in aiMenus" :key="item.path" :index="item.path">
            <my-icon :icon="item.icon" :label="item.label"/>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="studentMenus.length > 0" index="student-center" title="学习中心">
          <template #title>
            <my-icon icon="Reading" label="学习中心"/>
          </template>
          <el-menu-item v-for="item in studentMenus" :key="item.path" :index="item.path">
            <my-icon :icon="item.icon" :label="item.label"/>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu class="menus" v-for="(menu, i) in businessMenus"
                     :key="menu['id']" :index="i.toString()" :title="menu['info']">
          <template #title>
            <my-icon :icon="menu['icon']" :label="menu['title']"/>
          </template>
          <el-menu-item class="sub-menus" v-for="subMenu in menu['subMenus']"
                        :key="subMenu['id']" :index="subMenu['url']" :title="subMenu['info']">
            <my-icon :icon="subMenu['icon']" :label="subMenu['title']"/>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container class="main-body-right">
      <el-header class="main-body-right-head">
        <el-row class="is-align-middle">
          <el-col class="fold-expand" :span="2">
            <el-radio-group v-model="isCollapse">
              <el-radio-button :label="!isCollapse">
                <my-icon size="20" :icon="!isCollapse ? 'Fold' : 'Expand'"/>
              </el-radio-button>
            </el-radio-group>
          </el-col>
          <el-col class="project-title-col" :span="7">
            <el-popover :content="PROJECT_INFO.info"
                        width="500" placement="bottom-start" trigger="click">
              <template #reference>
                {{ PROJECT_INFO.title }}
              </template>
            </el-popover>
          </el-col>
          <el-col class="operation-btn-col" :span="6" :offset="5">
            <el-divider direction="vertical"/>
            <el-tooltip content="全局搜索">
              <el-button icon="search" size="small" round @click=""/>
            </el-tooltip>
            <el-tooltip content="系统通知">
              <el-button icon="bell" @click="notify"
                         size="small" round/>
            </el-tooltip>
            <el-tooltip :content="theme === 'dark' ? '切换亮色主题' : '切换暗色主题'">
              <el-button :icon="theme === 'dark' ? 'Sunny' : 'Moon'" @click="toggleTheme"
                         size="small" round/>
            </el-tooltip>
            <el-tooltip content="项目基本信息">
              <el-button icon="list" @click="openProjectInfoDrawer"
                         size="small" round/>
            </el-tooltip>
            <el-tooltip content="项目技术信息">
              <el-button icon="management" @click="openProjectSkillDrawer"
                         size="small" round/>
            </el-tooltip>
            <el-tooltip content="系统日历">
              <el-button icon="calendar" @click="openCalendarDrawer"
                         size="small" round/>
            </el-tooltip>
            <el-divider direction="vertical"/>
          </el-col>
          <el-col class="nickname-col" :span="3" v-if="loginUser['nickname']">
            {{ loginUser['nickname'] }}
          </el-col>
          <el-col class="avatar-col" :span="1" v-if="loginUser['avatar']">
            <el-dropdown trigger="click">
              <span class="el-dropdown-link">
                <el-avatar class="avatar" :src="avatar"
                           :size="45"/>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item icon="InfoFilled" @click="toPersonal">查看个人信息</el-dropdown-item>
                  <el-dropdown-item icon="Edit" @click="toPersonalUpdate">修改个人信息</el-dropdown-item>
                  <el-dropdown-item icon="Phone" @click="toPersonalUpdatePhone">换绑手机号码</el-dropdown-item>
                  <el-dropdown-item icon="WarnTriangleFilled" @click="logout">
                    <el-text type="danger">退出登录</el-text>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-col>
        </el-row>
      </el-header>
      <el-main class="main-body-right-main">
        <router-view/>
      </el-main>
    </el-container>
  </el-container>
  <el-drawer title="项目系统信息" v-model="projectInfoDrawer"
             size="50%">
    <el-descriptions border column="1">
      <el-descriptions-item v-for="(v, k) in PROJECT_INFO"
                            :key="k" :label="k">
        {{ v }}
      </el-descriptions-item>
    </el-descriptions>
  </el-drawer>
  <el-drawer title="项目技术栈信息" v-model="projectSkillDrawer"
             size="50%">
    <el-descriptions border column="1">
      <el-descriptions-item v-for="item in PROJECT_SKILLS"
                            :key="item['label']" :label="item['label']">
        {{ item['value'] }} ({{ item['version'] }})
      </el-descriptions-item>
    </el-descriptions>
  </el-drawer>
  <el-drawer title="系统日历" v-model="calendarDrawer"
             size="50%">
    <el-calendar v-model="calendarData"/>
  </el-drawer>
</template>

<style scoped lang="scss">
.main-body {
  height: 100vh;
  overflow: hidden;
}

.main-body-left {

  height: 100vh; // 高度
  border-right: 1px solid var(--ml-border); // 右边框
  background: var(--ml-sidebar);

  .logo {
    padding: 10px; // 内边距
    box-sizing: border-box;
  }

  :deep(.el-menu) {
    background: var(--ml-sidebar);
    border-right: 0;
  }

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    color: var(--ml-text);
  }

  :deep(.el-menu-item:hover),
  :deep(.el-sub-menu__title:hover) {
    background: color-mix(in srgb, var(--ml-primary) 8%, transparent);
  }

  :deep(.el-menu-item.is-active) {
    color: var(--ml-primary);
    background: color-mix(in srgb, var(--ml-primary) 12%, transparent);
  }

  .el-menu-vertical-demo:not(.el-menu--collapse) {
    width: 200px; // 宽度
    height: 100vh; // 高度
    letter-spacing: 0; // 字间距
  }

  .el-icon {
    margin: 0 10px; // 上下外边距 左右外边距
  }
}

.main-body-right-head {
  flex: 0 0 64px;
  height: 64px;
  border-bottom: 1px solid var(--ml-border);
  background: var(--ml-header);
  backdrop-filter: blur(8px);

  .project-title-col {
    font-weight: bolder; // 加粗
    font-size: 1.15rem; // 字号倍率
    color: var(--ml-text);
  }

  :deep(.el-radio-button__inner) {
    background: var(--ml-surface);
    border-color: var(--ml-border);
    color: var(--ml-text);
  }

  :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
    background: var(--ml-primary);
    border-color: var(--ml-primary);
    color: #ffffff;
  }

  :deep(.el-button.is-round) {
    background: var(--ml-surface);
    border-color: var(--ml-border);
    color: var(--ml-text);
  }

  .nickname-col {
    text-align: right; // 右对齐
    height: 50px; // 高度
    display: inline-block; // 内联块
    line-height: 50px; // 行高
  }

  .avatar-col {
    text-align: right; // 右对齐
  }

  .avatar {
    margin: 10px; // 外边距
    outline: 1px solid var(--ml-border); // 边框
    border: 1px solid var(--ml-border); // 边框
  }
}

.main-body-right-main {
  height: calc(100vh - 64px);
  min-height: 0;
  overflow: auto;
  background: var(--ml-bg);
}
</style>
