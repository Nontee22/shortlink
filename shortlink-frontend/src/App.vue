<template>
  <div class="app-container">
    <header class="header">
      <div class="header-inner">
        <router-link to="/" class="logo">
          <el-icon :size="22"><Link /></el-icon>
          <span>ShortLink</span>
        </router-link>
        <nav class="nav">
          <router-link to="/" class="nav-item" :class="{ active: activeMenu === '/' }">
            <el-icon><House /></el-icon>
            <span>创建短链</span>
          </router-link>
          <router-link to="/list" class="nav-item" :class="{ active: activeMenu === '/list' }">
            <el-icon><List /></el-icon>
            <span>短链列表</span>
          </router-link>
        </nav>
      </div>
    </header>

    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <footer class="footer">
      <span>ShortLink &copy; 2026</span>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { House, List, Link } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => {
  if (route.path.startsWith('/stats')) return '/list'
  return route.path
})
</script>

<style>
:root {
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-text: #1d1d1f;
  --color-text-secondary: #6b7280;
  --color-text-muted: #9ca3af;
  --color-bg: #f8f9fb;
  --color-bg-card: #fff;
  --color-border: #e5e7eb;
  --color-success: #16a34a;
  --color-danger: #dc2626;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-card: 0 1px 3px rgba(0, 0, 0, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-card-hover: 0 4px 12px rgba(0, 0, 0, 0.08);
  --shadow-header: 0 1px 0 rgba(0, 0, 0, 0.06);
  --radius-sm: 6px;
  --radius-card: 12px;
  --radius-lg: 16px;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
  background-color: var(--color-bg);
  color: var(--color-text);
  -webkit-font-smoothing: antialiased;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* Element Plus card override */
.el-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-card) !important;
  box-shadow: var(--shadow-card) !important;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.el-card:hover {
  box-shadow: var(--shadow-card-hover) !important;
}

/* Element Plus button primary override */
.el-button--primary {
  --el-button-bg-color: var(--color-primary);
  --el-button-border-color: var(--color-primary);
  --el-button-hover-bg-color: var(--color-primary-hover);
  --el-button-hover-border-color: var(--color-primary-hover);
}

/* Table row hover */
.el-table .el-table__row:hover > td {
  background-color: #f8f9fb !important;
}

.el-table .el-table__row {
  transition: background-color 0.15s ease;
}

/* Route transition */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>

<style scoped>
.header {
  background-color: var(--color-bg-card);
  box-shadow: var(--shadow-header);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 48px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
  text-decoration: none;
  letter-spacing: -0.02em;
}

.logo:hover {
  opacity: 0.8;
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.nav-item:hover {
  color: var(--color-text);
  background-color: #f3f4f6;
}

.nav-item.active {
  color: var(--color-primary);
  background-color: #eff6ff;
}

.main-content {
  flex: 1;
  padding: 32px 24px;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}

.footer {
  text-align: center;
  color: var(--color-text-muted);
  font-size: 13px;
  padding: 24px;
}
</style>
