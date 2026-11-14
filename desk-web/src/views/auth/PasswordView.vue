<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElButton, ElInput, ElAlert } from 'element-plus'
import { changePassword } from '../../api/auth/authApi'
import { useAuthStore } from '../../stores/authStore'
import { useOperation } from '../../composables/useOperation'
const current = ref(''),
  next = ref(''),
  confirm = ref('')
const auth = useAuthStore(),
  router = useRouter()
const { busy, error, run } = useOperation()
/** 修改成功后旧会话失效，重新登录获取新身份。 */
function submit() {
  void run(async () => {
    if (next.value !== confirm.value) throw new Error('两次新密码不一致')
    const bytes = new TextEncoder().encode(next.value).length
    if (
      bytes < 12 ||
      bytes > 72 ||
      !/[a-zA-Z]/.test(next.value) ||
      !/\d/.test(next.value)
    )
      throw new Error('新密码须包含字母与数字，长度为12至72个UTF-8字节')
    await changePassword(current.value, next.value, await auth.token())
    current.value = ''
    next.value = ''
    confirm.value = ''
    auth.clear()
    await router.replace('/login')
  })
}
</script>
<template>
  <main class="auth-panel">
    <h1>修改密码</h1>
    <p>首次登录或管理员重置后，须先修改密码。修改成功后请重新登录。</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <form @submit.prevent="submit">
      <label
        >当前密码<ElInput
          v-model="current"
          type="password"
          autocomplete="current-password"
          required /></label
      ><label
        >新密码<ElInput
          v-model="next"
          type="password"
          autocomplete="new-password"
          required /></label
      ><label
        >确认新密码<ElInput
          v-model="confirm"
          type="password"
          autocomplete="new-password"
          required /></label
      ><ElButton type="primary" native-type="submit" :loading="busy"
        >修改并重新登录</ElButton
      >
    </form>
  </main>
</template>
