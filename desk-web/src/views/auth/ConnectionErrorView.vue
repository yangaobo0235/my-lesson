<script setup lang="ts">
import { ElButton } from 'element-plus'
import { useRouter } from 'vue-router'

/** 身份探测技术失败不视为退出，用户主动重试后再读取当前身份。 @author yangaobo */
const router = useRouter()
</script>

<template>
  <main class="auth-page">
    <section class="panel">
      <h1>暂时无法确认登录状态</h1>
      <p>服务连接失败，请稍后重试。已提交的操作请在恢复后核对结果。</p>
      <ElButton type="primary" @click="router.replace('/')">重新连接</ElButton>
    </section>
  </main>
</template>
