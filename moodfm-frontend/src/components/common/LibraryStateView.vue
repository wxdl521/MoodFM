<script setup lang="ts">
interface Props {
  /** Currently loading state */
  loading?: boolean
  /** Error message; falsy means no error */
  error?: string | null
  /** Whether the underlying list is empty (only checked when not loading / no error) */
  empty?: boolean
  /** Number of skeleton rows to render while loading */
  skeletonRows?: number
  /** Layout for skeleton placeholders */
  skeletonLayout?: 'list' | 'grid'
  /** Friendly empty-state title */
  emptyTitle?: string
  /** Optional supporting text */
  emptyDescription?: string
  /** Optional large glyph rendered for the empty state */
  emptyGlyph?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  empty: false,
  skeletonRows: 5,
  skeletonLayout: 'list',
  emptyTitle: '暂无内容',
  emptyDescription: '',
  emptyGlyph: '∅',
})

defineEmits<{
  (e: 'retry'): void
}>()

void props
</script>

<template>
  <!-- Loading: skeleton -->
  <div v-if="loading" class="library-state-skeleton" :data-layout="skeletonLayout">
    <div
      v-for="i in skeletonRows"
      :key="i"
      class="skeleton-row"
      :data-layout="skeletonLayout"
    />
  </div>

  <!-- Error: message + retry -->
  <div v-else-if="error" class="library-state-error">
    <div class="display library-state-glyph">!</div>
    <div class="meta library-state-meta">SOMETHING WENT WRONG · 加载失败</div>
    <div class="library-state-error-msg">{{ error }}</div>
    <button class="btn-pill library-state-retry" @click="$emit('retry')">重试</button>
  </div>

  <!-- Empty: friendly placeholder -->
  <div v-else-if="empty" class="library-state-empty">
    <div class="display library-state-glyph">{{ emptyGlyph }}</div>
    <div class="library-state-empty-title">{{ emptyTitle }}</div>
    <div v-if="emptyDescription" class="meta library-state-empty-desc">
      {{ emptyDescription }}
    </div>
  </div>

  <!-- Real content -->
  <slot v-else />
</template>

<style scoped>
.library-state-skeleton {
  padding: 16px 0;
}

.library-state-skeleton[data-layout='grid'] {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  padding: 8px 0;
}

.skeleton-row {
  height: 56px;
  border-radius: 8px;
  background: var(--bg-2);
  margin-bottom: 8px;
  animation: lib-state-pulse 1.6s ease-in-out infinite;
}

.skeleton-row[data-layout='grid'] {
  height: 280px;
  margin-bottom: 0;
  border-radius: 12px;
}

@keyframes lib-state-pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.65; }
}

.library-state-error,
.library-state-empty {
  padding: 80px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.library-state-glyph {
  font-size: 72px;
  line-height: 1;
  opacity: 0.22;
}

.library-state-meta {
  color: var(--ink-3);
}

.library-state-error-msg {
  font-family: var(--serif-cn);
  font-size: 15px;
  color: var(--ink-2);
  max-width: 380px;
  line-height: 1.5;
}

.library-state-retry {
  margin-top: 8px;
  cursor: pointer;
}

.library-state-empty-title {
  font-family: var(--serif-cn);
  font-size: 20px;
  color: var(--ink-2);
  margin-top: 4px;
}

.library-state-empty-desc {
  color: var(--ink-3);
  max-width: 420px;
  line-height: 1.6;
}
</style>
