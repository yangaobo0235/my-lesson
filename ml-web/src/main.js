import { createApp } from 'vue';
import './style.scss';
import App from './App.vue';
import router from './router';
import vuex from './vuex';
// ElementPlus组件库: 核心对象，核心CSS，显隐CSS，国际化对象
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import 'element-plus/theme-chalk/display.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
// ElementPlus图标库: 导入全部Icons图标
import * as ElementPlusIcons from '@element-plus/icons-vue';
// ElementPlus暗黑模式: 核心CSS
import 'element-plus/theme-chalk/dark/css-vars.css';

const app = createApp(App);
app.use(router);
app.use(vuex);
app.use(ElementPlus, { locale: zhCn });

app.mount('#app');

// 全局注册ElementPlus图标库
for (const [key, component] of Object.entries(ElementPlusIcons)) {
    app.component(key, component)
}

