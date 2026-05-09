<template>
  <div>
    <div class="meta">CHAPTER ONE</div>
    <h1 class="display" :style="{ fontSize: isMobile ? '56px' : '120px', margin: '12px 0 0' }">
      Three <em>verbs</em>.
    </h1>
    <div class="display-cn" :style="{ fontSize: isMobile ? '22px' : '32px', marginTop: '8px', color: 'var(--ink-2)' }">
      关于这台电台，三个动词。
    </div>

    <div :style="{
      marginTop: isMobile ? '32px' : '56px',
      display: 'grid',
      gridTemplateColumns: isMobile ? '1fr' : 'repeat(3, 1fr)',
      gap: '1px',
      background: 'var(--rule)',
      border: '1px solid var(--rule)',
    }">
      <div v-for="c in cards" :key="c.n" :style="{ background: 'var(--paper)', padding: isMobile ? '24px 20px' : '32px 28px' }">
        <div class="meta">№ {{ c.n }}</div>
        <div class="serif-en" :style="{ fontSize: isMobile ? '44px' : '60px', marginTop: '8px' }">
          {{ c.en }}<span style="color: var(--ink-3);">.</span>
        </div>
        <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '22px', fontWeight: 500, marginTop: '4px' }">{{ c.cn }}</div>
        <p style="color: var(--ink-2); font-size: 14px; line-height: 1.7; margin-top: 14px;">{{ c.body }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ isMobile: boolean }>()

const cards = [
  { n: '01', en: 'Read', cn: '读你', body: '说一句话、选一个场景、或在色盘上标个点。AI 帮你把感觉翻译成可听见的频率。' },
  { n: '02', en: 'Compose', cn: '调台', body: '横跨网易云与 QQ 音乐，从你的红心和全网曲库实时编排——一档只属于此刻的电台。' },
  { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一笔。每周一份情绪周报，长图分享给朋友。' },
]
</script>
