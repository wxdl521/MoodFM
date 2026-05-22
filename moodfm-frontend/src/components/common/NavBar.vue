<template>
  <header class="navbar">
    <div class="navbar__inner">
      <!-- Left: logo + brand + nav links -->
      <div class="navbar__left">
        <RouterLink to="/home" class="navbar__brand">
          <Logo :size="28" />
          <span class="navbar__brand-name">MoodFM</span>
        </RouterLink>

        <nav class="navbar__links" aria-label="Main navigation">
          <RouterLink
            v-for="link in navLinks"
            :key="link.to"
            :to="link.to"
            class="navbar__link"
            active-class="navbar__link--active"
          >
            {{ link.label }}
          </RouterLink>
        </nav>
      </div>

      <!-- Right: platform tag + icons + avatar -->
      <div class="navbar__right">
        <!-- Platform bind button -->
        <RouterLink to="/bind" class="navbar__platform-btn" aria-label="平台绑定">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
            <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
          </svg>
          <span class="navbar__platform-btn-label">平台</span>
        </RouterLink>

        <!-- Search -->
        <button class="navbar__icon-btn" aria-label="搜索" @click="$router.push('/search')">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="7.5" />
            <line x1="16.8" y1="16.8" x2="22" y2="22" />
          </svg>
        </button>

        <!-- Notifications -->
        <button class="navbar__icon-btn" aria-label="通知">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
            <path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
        </button>

        <!-- Avatar -->
        <button class="navbar__avatar" aria-label="个人中心" @click="$router.push('/profile')">
          <img
            v-if="user?.avatarUrl"
            :src="user.avatarUrl"
            :alt="user.username"
            class="navbar__avatar-img"
          />
          <span v-else class="navbar__avatar-fallback">
            {{ userInitial }}
          </span>
        </button>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Logo from './Logo.vue'

const authStore = useAuthStore()
const user = computed(() => authStore.user)

const userInitial = computed(() =>
  user.value?.username?.charAt(0).toUpperCase() ?? '?',
)

const navLinks = [
  { to: '/home',      label: '电台' },
  { to: '/playlists', label: '歌单' },
  { to: '/history',   label: '历史' },
  { to: '/insights',  label: '画像' },
]
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 50;
  background: var(--bg);
  border-bottom: 1px solid var(--rule);
  height: 62px;
}

.navbar__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 56px;
}

@media (max-width: 768px) {
  .navbar__inner {
    padding: 0 22px;
  }
}

/* Brand */
.navbar__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: var(--ink);
  flex-shrink: 0;
}

.navbar__brand-name {
  font-family: var(--serif-en);
  font-size: 1.1rem;
  font-weight: 400;
  letter-spacing: 0.01em;
  line-height: 1;
}

/* Nav links — hidden on mobile */
.navbar__left {
  display: flex;
  align-items: center;
  gap: 32px;
}

.navbar__links {
  display: flex;
  align-items: center;
  gap: 28px;
}

@media (max-width: 768px) {
  .navbar__links {
    display: none;
  }
}

.navbar__link {
  font-family: var(--serif-cn);
  font-size: 0.875rem;
  color: var(--ink-2);
  text-decoration: none;
  transition: color 0.15s;
}

.navbar__link:hover {
  color: var(--ink);
}

.navbar__link--active {
  color: var(--ink);
  font-weight: 600;
}

/* Right side */
.navbar__right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.navbar__platform-badge {
  font-family: var(--mono);
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--bg-2);
  color: var(--ink-2);
  border: 1px solid var(--rule);
}

.navbar__platform-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid var(--rule);
  background: var(--bg-2);
  color: var(--ink-2);
  text-decoration: none;
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  transition: color 0.15s, border-color 0.15s, background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
  white-space: nowrap;
}

.navbar__platform-btn:hover {
  color: var(--ink);
  border-color: var(--ink-3);
  background: var(--bg);
}

.navbar__platform-btn:active {
  transform: scale(0.96);
  transition-duration: 0.07s;
}

.navbar__platform-btn-label {
  display: inline;
}

@media (max-width: 480px) {
  .navbar__platform-btn-label {
    display: none;
  }
}

.navbar__icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  transition: color 0.15s, background 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.navbar__icon-btn:hover {
  color: var(--ink);
  background: var(--bg-2);
  transform: scale(1.1);
}

.navbar__icon-btn:active {
  transform: scale(0.9);
  transition-duration: 0.07s;
}

.navbar__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1.5px solid var(--rule);
  background: var(--bg-2);
  color: var(--ink);
  cursor: pointer;
  overflow: hidden;
  padding: 0;
  transition: border-color 0.15s, transform 0.12s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.navbar__avatar:hover {
  border-color: var(--ink-3);
  transform: scale(1.06);
}

.navbar__avatar:active {
  transform: scale(0.94);
  transition-duration: 0.07s;
}

.navbar__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.navbar__avatar-fallback {
  font-family: var(--mono);
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--ink);
}
</style>
