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
  <main class="login">
    <section class="login-brand" aria-labelledby="login-brand-title">
      <strong class="login-name">Service Desk</strong>
      <div>
        <div class="login-symbol" aria-hidden="true">SD</div>
        <h2 id="login-brand-title">办公 IT 服务台</h2>
        <p>报修、排查和服务进度，一处查看。</p>
      </div>
    </section>
    <section class="login-form" aria-labelledby="login-title">
      <h1 id="login-title">登录工作空间</h1>
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
      <p class="subtle">账号由管理员开通。</p>
    </section>
  </main>
</template>

<style scoped>
.login {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  align-items: stretch;
  min-height: 100vh;
  max-width: none;
  margin: 0;
  padding: 0;
}
.login-brand {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 18px;
  padding: clamp(20px, 4vw, 52px);
  background:
    radial-gradient(ellipse at 83% 15%, #dbe3ff, transparent 60%),
    linear-gradient(128deg, #f3f8ff, #eeebff);
}
.login-name {
  color: var(--desk-accent-text);
  font-size: 18px;
}
.login-brand > div {
  align-self: center;
  text-align: center;
}
.login-symbol {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  margin: 0 auto 18px;
  border-radius: 20px;
  background: var(--desk-paper);
  color: var(--desk-accent-text);
  box-shadow: var(--desk-shadow);
  font-size: 23px;
  font-weight: 750;
}
.login-brand h2 {
  margin: 0;
  font-size: clamp(25px, 3vw, 40px);
}
.login-brand p {
  color: var(--desk-muted);
}
.login-form {
  display: grid;
  align-content: center;
  gap: 16px;
  width: min(100%, 420px);
  margin: auto;
  padding: clamp(20px, 4vw, 46px);
}
.login-form :is(h1, p) {
  margin: 0;
}
.login-form form,
.login-form label {
  display: grid;
  gap: 7px;
}
.login-form form {
  gap: 14px;
}
@media (max-width: 620px) {
  .login {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto 1fr;
  }
  .login-form {
    align-content: start;
    margin-block: 10px auto;
  }
}
</style>
