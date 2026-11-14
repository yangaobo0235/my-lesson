<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElInput, ElAlert } from 'element-plus'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
const username = ref('')
const password = ref('')
const auth = useAuthStore()
const router = useRouter()
const { busy, error, run } = useOperation()
/** 成功后按当前改密要求进入页面，不持久化口令。 */
function submit() {
  void run(async () => {
    await auth.signIn(username.value, password.value)
    password.value = ''
    await router.replace(auth.user?.mustChangePassword ? '/password' : '/')
  })
}
</script>
<template>
  <main class="auth-panel">
    <p class="eyebrow">企业 IT 服务台</p>
    <h1>登录工作空间</h1>
    <p>提交问题、跟进处理，与服务团队协作。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <form @submit.prevent="submit">
      <label
        >账号<ElInput
          v-model="username"
          autocomplete="username"
          maxlength="64"
          required /></label
      ><label
        >密码<ElInput
          v-model="password"
          type="password"
          autocomplete="current-password"
          show-password
          required /></label
      ><ElButton type="primary" native-type="submit" :loading="busy"
        >登录</ElButton
      >
    </form>
  </main>
</template>
