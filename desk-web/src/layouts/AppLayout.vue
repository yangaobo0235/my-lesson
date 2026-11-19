<script setup lang="ts">
import { computed, onMounted, provide, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ElAlert, ElButton } from 'element-plus'
import { ROLE_LABELS } from '../api/auth/types'
import { useAuthStore } from '../stores/authStore'
import { useOperation } from '../composables/useOperation'
import {
  hasWorkspaceAccess,
  STAFF_ROLES,
  TICKET_ROLES,
  WORKSPACE_SECTIONS,
} from '../router/workspaceAccess'
import { knowledgeRevisionKey } from '../router/knowledgeRevision'
import {
  acceptanceTicketKey,
  useAcceptanceWorkspace,
} from '../composables/useAcceptanceWorkspace'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const { run, error, busy } = useOperation()
const menuOpen = ref(false)
const customer = computed(() => auth.has('CUSTOMER_USER'))
const canSeeNotifications = computed(() =>
  hasWorkspaceAccess(auth.user, TICKET_ROLES),
)
const roleLabel = computed(() =>
  STAFF_ROLES.every((role) => auth.has(role))
    ? '全角色工作台'
    : auth.user?.roles.map((role) => ROLE_LABELS[role]).join(' · '),
)
const activeSection = computed(() => route.meta.section ?? route.name)
const title = computed(
  () => (customer.value && route.meta.customerTitle) || route.meta.title,
)
const routes = new Map(router.getRoutes().map(({ name, meta }) => [name, meta]))
const sections = computed(() =>
  WORKSPACE_SECTIONS.flatMap((section) => {
    const links = section.entries.flatMap((name) => {
      const meta = routes.get(name)
      if (!meta?.title || !hasWorkspaceAccess(auth.user, meta.roles)) return []
      return [
        { name, title: (customer.value && meta.customerTitle) || meta.title },
      ]
    })
    const first = links[0]
    if (!first) return []
    return [
      {
        id: section.id,
        name: first.name,
        title:
          customer.value && 'customerTitle' in section
            ? section.customerTitle
            : section.title,
        links,
      },
    ]
  }),
)
const section = computed(() =>
  sections.value.find(({ id }) => id === activeSection.value),
)
const revision = ref<string>()
provide(knowledgeRevisionKey, revision)
watch(
  () => route.fullPath,
  () => {
    const document = route.query.document
    revision.value =
      typeof document === 'string' && /^[0-9a-f]{32}$/.test(document)
        ? document
        : undefined
  },
  { immediate: true },
)
const acceptance = useAcceptanceWorkspace()
const acceptanceView = acceptance.view
provide(acceptanceTicketKey, acceptance.ticket)
onMounted(acceptance.load)
watch([acceptanceView, () => route.fullPath], acceptance.rememberTicket)

function switchView() {
  void run(async () => acceptance.change(await auth.token()))
}

function logout() {
  void run(async () => {
    if (acceptanceView.value) {
      await acceptance.leave(await auth.token())
      auth.clear()
    } else {
      await auth.signOut()
    }
    await router.replace({ name: 'login' })
  })
}
</script>

<template>
  <div class="workspace">
    <a class="skip-link" href="#workspace-content">跳到页面内容</a>
    <aside class="workspace-sidebar">
      <div class="sidebar-heading">
        <RouterLink
          class="workspace-brand"
          :to="{ name: 'home' }"
          @click="menuOpen = false"
        >
          <span class="brand-mark" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path
                d="M4 13v-2a8 8 0 0 1 16 0v4a4 4 0 0 1-4 4h-3M4 12h2a2 2 0 0 1 2 2v1a2 2 0 0 1-4 0v-3Zm16 0h-2a2 2 0 0 0-2 2v1a2 2 0 0 0 4 0v-3Z"
              />
            </svg>
          </span>
          <span class="brand-copy">
            <strong>Service Desk</strong>
            <small>办公 IT 服务台</small>
          </span>
        </RouterLink>
        <ElButton
          class="mobile-menu"
          :aria-expanded="menuOpen"
          aria-controls="workspace-nav"
          @click="menuOpen = !menuOpen"
          >{{ menuOpen ? '收起导航' : '展开导航' }}</ElButton
        >
      </div>
      <nav
        id="workspace-nav"
        class="workspace-nav"
        :data-expanded="menuOpen"
        aria-label="工作台导航"
      >
        <RouterLink
          v-for="item in sections"
          :key="item.id"
          :to="{ name: item.name }"
          :class="{ 'section-active': activeSection === item.id }"
          :aria-current="activeSection === item.id ? 'location' : undefined"
          @click="menuOpen = false"
          >{{ item.title }}</RouterLink
        >
      </nav>
      <div class="workspace-identity">
        <span class="account-mark" aria-hidden="true">{{
          auth.user?.displayName.slice(0, 1)
        }}</span>
        <span class="account-text"
          ><strong>{{
            acceptanceView === 'staff' ? 'admin' : auth.user?.displayName
          }}</strong
          ><small>{{
            acceptanceView === 'staff'
              ? '服务商 · 验收管理员'
              : acceptanceView === 'customer'
                ? '本地验收企业'
                : roleLabel
          }}</small></span
        >
      </div>
    </aside>
    <div class="workspace-body">
      <header class="workspace-topbar">
        <div class="workspace-location">
          <span v-if="acceptanceView" class="perspective">{{
            acceptanceView === 'staff' ? '服务商视角' : '客户体验'
          }}</span>
          <span
            v-if="section && section.title !== title"
            class="section-crumb"
            >{{ section.title }}</span
          >
          <strong>{{ title }}</strong>
        </div>
        <div class="workspace-utilities">
          <RouterLink
            v-if="canSeeNotifications"
            class="quiet-link"
            :to="{ name: 'notifications' }"
            >通知</RouterLink
          >
          <ElButton
            v-if="acceptanceView"
            class="view-switch"
            :loading="busy"
            @click="switchView"
          >
            {{ acceptanceView === 'staff' ? '进入客户体验' : '返回服务商' }}
          </ElButton>
          <details class="account-menu">
            <summary>账号</summary>
            <div class="account-actions">
              <RouterLink class="quiet-link" :to="{ name: 'password' }"
                >修改密码</RouterLink
              >
              <ElButton text :loading="busy" @click="logout">退出登录</ElButton>
            </div>
          </details>
        </div>
      </header>
      <div id="workspace-content" class="workspace-content">
        <ElAlert v-if="error" :title="error" type="error" :closable="false" />
        <nav
          v-if="section && section.links.length > 1"
          aria-label="当前模块栏目"
          class="section-tabs"
        >
          <RouterLink
            v-for="link in section.links"
            :key="link.name"
            :to="{
              name: link.name,
              query:
                section.id === 'knowledge-admin' && revision
                  ? { document: revision }
                  : {},
            }"
            >{{ link.title }}</RouterLink
          >
        </nav>
        <RouterView :key="route.fullPath" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.workspace {
  display: grid;
  grid-template-columns: 190px minmax(0, 1fr);
  min-height: 100vh;
}
.workspace-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: auto;
  display: flex;
  flex-direction: column;
  padding: 18px 14px 12px;
  border-inline-end: 1px solid var(--desk-border);
  background: linear-gradient(165deg, #f9fbff, #eef1fb);
}
.sidebar-heading,
.workspace-brand,
.workspace-identity,
.workspace-topbar,
.workspace-utilities {
  display: flex;
  align-items: center;
}
.sidebar-heading,
.workspace-topbar {
  justify-content: space-between;
  gap: 14px;
}
.workspace-brand {
  gap: 9px;
  padding: 4px 4px 8px;
  color: var(--desk-text);
  text-decoration: none;
  font-weight: 740;
  white-space: nowrap;
}
.brand-copy {
  display: grid;
  line-height: 1.35;
}
.brand-copy small {
  color: var(--desk-muted);
  font-weight: 500;
}
.brand-mark,
.account-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  color: var(--desk-accent-text);
  background: linear-gradient(145deg, #eef2ff, #dce2ff);
  border-radius: 9px;
}
.brand-mark svg {
  width: 18px;
  height: 18px;
}
.brand-mark {
  color: #fff;
  background: linear-gradient(145deg, #6677e8, var(--desk-accent));
}
.mobile-menu {
  display: none;
}
.workspace-nav {
  display: grid;
  gap: 5px;
  padding-block: 22px;
}
.workspace-nav a {
  padding: 8px 10px;
  color: var(--desk-muted);
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 14px;
  text-decoration: none;
}
.workspace-nav a:hover {
  background: var(--desk-accent-soft);
}
.workspace-nav .section-active {
  background: linear-gradient(110deg, #e3e9ff, #f0f1ff);
  border-color: #d6ddf7;
  color: var(--desk-accent-text);
  font-weight: 700;
}
.workspace-identity {
  gap: 9px;
  margin-top: auto;
  padding: 10px 4px 2px;
  border-top: 1px solid var(--desk-border);
}
.account-mark {
  border-radius: 50%;
}
.account-text {
  display: grid;
  gap: 2px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.account-text small {
  color: var(--desk-muted);
}
.workspace-body {
  min-width: 0;
  background: var(--desk-canvas);
}
.workspace-topbar {
  min-height: 58px;
  padding: 8px var(--desk-gutter);
  background: var(--desk-paper);
  border-bottom: 1px solid var(--desk-border);
}
.workspace-location {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 14px;
}
.perspective,
.section-crumb {
  color: var(--desk-muted);
}
.perspective::after,
.section-crumb::after {
  content: '/';
  margin-inline-start: 8px;
}
.workspace-utilities {
  gap: 14px;
  flex-wrap: wrap;
}
.view-switch {
  color: var(--desk-accent-text);
  background: var(--desk-accent-soft);
}
.quiet-link,
.account-menu summary {
  color: var(--desk-accent-text);
  font-size: 14px;
}
.quiet-link {
  text-decoration: none;
}
.account-menu {
  position: relative;
}
.account-actions {
  display: grid;
  gap: 5px;
  position: absolute;
  z-index: 2;
  inset-inline-end: 0;
  min-width: 112px;
  margin-top: 8px;
  padding: 8px;
  text-align: center;
  background: var(--desk-paper);
  border: 1px solid var(--desk-border);
  border-radius: 9px;
  box-shadow: var(--desk-shadow);
}
.workspace-content {
  padding-inline: var(--desk-gutter);
}
.section-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 15px;
}
.section-tabs a {
  padding: 5px 10px;
  border-radius: 8px;
  color: var(--desk-muted);
  text-decoration: none;
}
.section-tabs a:hover,
.section-tabs .router-link-exact-active {
  background: var(--desk-accent-soft);
  color: var(--desk-accent-text);
}
.section-tabs .router-link-exact-active {
  font-weight: 650;
}
.skip-link {
  position: absolute;
  inset-inline-start: 12px;
  top: 8px;
  z-index: 3;
  background: var(--desk-paper);
  padding: 6px;
}
.skip-link:not(:focus) {
  clip-path: inset(50%);
}
@media (max-width: 760px) {
  .workspace {
    grid-template-columns: minmax(0, 1fr);
  }
  .workspace-sidebar {
    position: static;
    height: auto;
    padding: 8px var(--desk-gutter);
    border-inline-end: 0;
  }
  .workspace-topbar {
    min-height: 44px;
  }
  .workspace-nav:not([data-expanded='true']),
  .workspace-identity {
    display: none;
  }
  .workspace-nav {
    padding-block: 9px;
  }
  .workspace-nav {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
  }
  .mobile-menu {
    display: inline-flex;
  }
}
</style>
