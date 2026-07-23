# MyLesson Web

MyLesson 的统一 Web 客户端，包含学生学习端、后台管理端和 AI 学习助手。所有 HTTP 请求默认经 `ml-gateway` 转发，AI 对话使用 Fetch 流读取服务端事件。

## 功能

- 学生端：课程大厅、课程详情、购物车、订单、已购课程、收藏、视频播放、评论与弹幕。
- AI 助手：流式对话、历史会话、课程推荐、学习计划、待确认操作和执行时间线。
- 管理端：用户与权限、课程内容、文章公告、营销活动、订单、知识库状态、RAG 评测和工具审计。
- 权限：根据登录用户角色生成导航，并由网关和后端服务完成最终鉴权。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 框架 | Vue 3.5、Vue Router 4、Vuex 4 |
| 构建 | Vite 6.4、Node.js 20+ |
| UI 与图表 | Element Plus 2.14、ECharts 6.1 |
| 网络 | Axios 1.18、Fetch 流式响应 |
| 媒体 | xgplayer 3 |
| 样式 | Sass Embedded |

依赖版本以 `package.json` 和 `package-lock.json` 为准。

## 本地运行

先启动网关和所需后端服务，然后执行：

```bash
npm ci
npm run dev
```

开发地址为 `http://localhost:24108`。

可配置的前端环境变量：

```dotenv
VITE_GATEWAY_URL=http://127.0.0.1:24101
VITE_MINIO_PUBLIC_URL=http://127.0.0.1:9001/mylesson
```

环境变量可以放在 `ml-web/.env.local`，不要提交包含真实地址或凭据的本地配置。

## 构建

```bash
npm ci
npm run build
npm run preview
```

生产文件输出到 `dist/`。CI 会执行生产依赖审计和构建。

## 目录

```text
src/
├── api/          # 网关、业务服务和 AI 接口封装
├── components/   # 表格、表单、上传、播放器等通用组件
├── router/       # 管理端、学生端和 AI 页面路由
├── util/         # 鉴权、格式化和通用工具
├── views/ai/     # AI 对话、计划、审批、评测与审计
├── views/student/ # 学生学习端
└── views/        # 后台管理页面
```
