<script setup>
import { onMounted, ref } from 'vue'
import {
  Search,
  UserRound,
  UsersRound,
  XCircle
} from 'lucide-vue-next'
import { api, assetUrl, queryString } from '../../services/api'
import { useUiStore } from '../../stores/ui'
import LoadingPanel from '../../components/LoadingPanel.vue'
import EmptyState from '../../components/EmptyState.vue'
import PaginationBar from '../../components/PaginationBar.vue'
import ModalDialog from '../../components/ModalDialog.vue'

const ui = useUiStore()
const users = ref([])
const loading = ref(true)
const status = ref('')
const keyword = ref('')
const page = ref(1)
const totalPages = ref(1)
const total = ref(0)
const userStats = ref({ total: 0, normal: 0, disabled: 0 })
const selected = ref(null)

function formatDate(value) {
  return value ? String(value).slice(0, 10) : '--'
}

async function loadUserStats() {
  try {
    const [allRes, normalRes, disabledRes] = await Promise.all([
      api.get('/admin/users?role=user&page=1&pageSize=1'),
      api.get('/admin/users?role=user&status=normal&page=1&pageSize=1'),
      api.get('/admin/users?role=user&status=disabled&page=1&pageSize=1')
    ])
    userStats.value = {
      total: allRes.data?.total || 0,
      normal: normalRes.data?.total || 0,
      disabled: disabledRes.data?.total || 0
    }
  } catch (error) {
    // 统计读取失败时保留上一次结果，避免数字被清零
  }
}

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, pageSize: 20, role: 'user' }
    if (status.value) params.status = status.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    const result = await api.get(`/admin/users${queryString(params)}`)
    users.value = result.data?.list || []
    totalPages.value = result.data?.totalPages || 1
    total.value = result.data?.total || 0
  } catch (error) {
    ui.toast(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

async function toggleStatus(user) {
  const next = user.status === 'normal' ? 'disabled' : 'normal'
  const confirmed = await ui.confirm({
    message: next === 'disabled'
      ? `确定禁用用户「${user.username}」吗？其店铺也会关闭。`
      : `确定恢复用户「${user.username}」吗？`,
    danger: next === 'disabled'
  })
  if (!confirmed) return
  try {
    await api.put(`/admin/users/${user.id}/status`, { status: next })
    user.status = next
    if (selected.value?.id === user.id) selected.value.status = next
    if (status.value) {
      if (page.value > 1 && users.value.length === 1) page.value -= 1
      await load()
    }
    await loadUserStats()
    ui.toast('用户状态已更新', 'success')
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

async function viewDetail(user) {
  try {
    selected.value = (await api.get(`/admin/users/${user.id}`)).data
  } catch (error) {
    ui.toast(error.message, 'error')
  }
}

onMounted(async () => {
  await Promise.all([load(), loadUserStats()])
})
</script>

<template>
  <div class="admin-page user-page">
    <header class="admin-page-head">
      <div>
        <h1>用户管理</h1>
      </div>
    </header>

    <div class="admin-metric-row user-metrics">
      <article>
        <span>全部</span>
        <b>{{ userStats.total.toLocaleString('zh-CN') }}</b>
      </article>
      <article>
        <span>正常</span>
        <b>{{ userStats.normal.toLocaleString('zh-CN') }}</b>
      </article>
      <article>
        <span>禁用</span>
        <b>{{ userStats.disabled.toLocaleString('zh-CN') }}</b>
      </article>
    </div>

    <form class="admin-table-shell user-table-shell" @submit.prevent="search">
      <div class="admin-table-toolbar">
        <div class="admin-tabs" aria-label="账号状态筛选">
          <button type="button" :class="{ active: status === '' }" @click="status = ''; search()">
            全部 <b>{{ userStats.total.toLocaleString('zh-CN') }}</b>
          </button>
          <button type="button" :class="{ active: status === 'normal' }" @click="status = 'normal'; search()">
            正常 <b>{{ userStats.normal.toLocaleString('zh-CN') }}</b>
          </button>
          <button type="button" :class="{ active: status === 'disabled' }" @click="status = 'disabled'; search()">
            已禁用 <b>{{ userStats.disabled.toLocaleString('zh-CN') }}</b>
          </button>
        </div>
        <div class="admin-toolbar">
          <label class="admin-search">
            <Search :size="16" />
            <input v-model="keyword" aria-label="搜索用户名、昵称或手机号" placeholder="搜索用户名或昵称">
          </label>
          <button class="btn btn-primary" type="submit">查询</button>
        </div>
      </div>

      <LoadingPanel v-if="loading" label="正在读取用户" />
      <EmptyState v-else-if="!users.length" title="没有匹配用户" />
      <template v-else>
        <table class="admin-data-table">
          <thead>
            <tr>
              <th>用户</th>
              <th>城市</th>
              <th>注册时间</th>
              <th>状态</th>
              <th class="right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>
                <div class="admin-person">
                  <img v-if="user.avatar" :src="assetUrl(user.avatar)" alt="">
                  <span v-else class="admin-avatar-mark"><UserRound :size="17" /></span>
                  <div>
                    <b>{{ user.nickname || user.username }}</b>
                    <small>@{{ user.username }}</small>
                  </div>
                </div>
              </td>
              <td>{{ user.city || '--' }}</td>
              <td>{{ formatDate(user.createdAt) }}</td>
              <td>
                <span class="admin-status" :class="user.status === 'normal' ? 'sage' : 'danger'">
                  {{ user.status === 'normal' ? '正常' : '禁用' }}
                </span>
              </td>
              <td>
                <div class="admin-row-actions">
                  <button class="admin-row-btn" type="button" @click="viewDetail(user)">详情</button>
                  <button
                    class="admin-row-btn"
                    :class="{ danger: user.status === 'normal' }"
                    type="button"
                    @click="toggleStatus(user)"
                  >
                    {{ user.status === 'normal' ? '禁用' : '恢复' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="admin-table-footer">
          <span>显示第 {{ page }} 页，共 {{ total.toLocaleString('zh-CN') }} 位用户</span>
          <PaginationBar :page="page" :total-pages="totalPages" @change="page = $event; load()" />
        </div>
      </template>
    </form>

    <ModalDialog :open="!!selected" title="用户详情" width="680px" @close="selected = null">
      <div v-if="selected" class="user-detail">
        <div class="profile-summary">
          <img v-if="selected.avatar" :src="assetUrl(selected.avatar)" alt="">
          <span v-else class="admin-avatar-mark large"><UsersRound :size="23" /></span>
          <div>
            <h3>{{ selected.nickname || selected.username }}</h3>
            <p>@{{ selected.username }}</p>
          </div>
          <span class="admin-status" :class="selected.status === 'normal' ? 'sage' : 'danger'">
            {{ selected.status === 'normal' ? '正常' : '禁用' }}
          </span>
        </div>
        <div class="detail-grid">
          <div><span>用户名</span><b>{{ selected.username }}</b></div>
          <div><span>昵称</span><b>{{ selected.nickname || '--' }}</b></div>
          <div><span>状态</span><b>{{ selected.status }}</b></div>
          <div><span>手机号</span><b>{{ selected.phone || '--' }}</b></div>
          <div><span>邮箱</span><b>{{ selected.email || '--' }}</b></div>
          <div><span>城市</span><b>{{ selected.city || '--' }}</b></div>
          <div><span>注册时间</span><b>{{ selected.createdAt }}</b></div>
        </div>
        <div class="detail-actions">
          <button
            class="btn"
            :class="selected.status === 'normal' ? 'btn-danger' : 'btn-primary'"
            type="button"
            @click="toggleStatus(selected)"
          >
            <XCircle v-if="selected.status === 'normal'" :size="15" />
            {{ selected.status === 'normal' ? '禁用账号' : '恢复账号' }}
          </button>
        </div>
      </div>
    </ModalDialog>
  </div>
</template>

<style scoped>
.user-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.admin-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.admin-tabs b {
  font-size: 0.72rem;
  font-weight: var(--font-weight-extra-bold);
  color: var(--color-primary-strong);
  font-variant-numeric: tabular-nums;
}

.admin-tabs button.active b {
  color: var(--color-text);
}

.right {
  text-align: right;
}

.pet-pill {
  display: inline-flex;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-secondary);
  background: var(--color-surface-warm);
  font-size: 0.78rem;
  white-space: nowrap;
}

.user-table-shell {
  display: block;
}

.user-table-shell .pagination {
  margin: 0;
}

.user-detail {
  display: grid;
  gap: 15px;
}

.profile-summary {
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-warm);
}

.profile-summary img,
.admin-avatar-mark.large {
  width: 52px;
  height: 52px;
  border-radius: 15px;
}

.profile-summary > div {
  min-width: 0;
  flex: 1;
}

.profile-summary h3,
.profile-summary p {
  margin: 0;
}

.profile-summary h3 {
  font-size: 1.1rem;
}

.profile-summary p {
  color: var(--color-text-muted);
  font-size: 0.8rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.detail-grid div {
  min-width: 0;
  padding: 11px;
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-sm);
  background: var(--color-surface-ivory);
}

.detail-grid .full {
  grid-column: 1 / -1;
}

.detail-grid span,
.detail-grid b {
  display: block;
}

.detail-grid span {
  color: var(--color-text-muted);
  font-size: 0.76rem;
}

.detail-grid b {
  margin-top: 2px;
  word-break: break-word;
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 760px) {
  .user-metrics {
    grid-template-columns: 1fr;
  }

  .user-metrics article {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .user-metrics article:last-child {
    border-bottom: 0;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-grid .full {
    grid-column: auto;
  }
}
</style>
