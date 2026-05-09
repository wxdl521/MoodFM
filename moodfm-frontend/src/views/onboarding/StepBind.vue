<template>
  <div>
    <div class="meta">CHAPTER TWO · 必选 / REQUIRED</div>
    <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '108px', margin: '12px 0 0' }">
      Plug in <em>your</em>
    </h1>
    <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '108px', margin: 0 }">
      listening <em>history</em>.
    </h1>
    <div class="display-cn" :style="{ fontSize: isMobile ? '22px' : '30px', marginTop: '12px', color: 'var(--ink-2)' }">
      把你听了多年的曲库接进来——从此电台从你的红心开始。
    </div>

    <div :style="{
      marginTop: isMobile ? '28px' : '48px',
      display: 'grid',
      gridTemplateColumns: isMobile ? '1fr' : '1fr 1fr',
      gap: '16px',
    }">
      <div v-for="p in platforms" :key="p.name"
        :style="{ padding: '24px', border: '1px solid var(--rule)', borderRadius: '18px', background: 'var(--paper)', position: 'relative' }">
        <div v-if="p.recommended" style="position: absolute; top: 14px; right: 14px;" class="meta">RECOMMENDED</div>
        <div class="row" style="gap: 16px;">
          <div :style="{
            width: '56px', height: '56px', borderRadius: '50%',
            background: 'linear-gradient(135deg, var(--mood-a), var(--mood-c))',
            color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--serif-cn)', fontSize: '24px', fontWeight: 600,
          }">{{ p.logo }}</div>
          <div style="flex: 1;">
            <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '20px', fontWeight: 500 }">{{ p.name }}</div>
            <div class="meta" style="margin-top: 2px;">{{ p.en }}</div>
          </div>
        </div>
        <div class="between" style="margin-top: 24px;">
          <div class="meta">STATUS · <span style="color: var(--ink-3);">未绑定</span></div>
          <button class="btn" style="height: 38px; padding: 0 16px;" @click="$emit('bind', p.name)">绑定 →</button>
        </div>
      </div>
    </div>

    <div class="meta" style="margin-top: 28px; color: var(--ink-3); max-width: 500px;">
      · 我们仅读取曲库与红心 · Cookie 加密存储 · 随时可解绑
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ isMobile: boolean }>()
defineEmits<{ bind: [platform: string] }>()

const platforms = [
  { logo: '网', name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', recommended: true },
  { logo: 'Q', name: 'QQ 音乐', en: 'QQ MUSIC', recommended: false },
]
</script>
