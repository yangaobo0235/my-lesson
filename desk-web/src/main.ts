/**
 * 装配页面、路由与状态容器，不在入口伪造业务身份。
 * @author yangaobo
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'
import { createWorkspaceRouter } from './router'

createApp(App).use(createPinia()).use(createWorkspaceRouter()).mount('#app')
