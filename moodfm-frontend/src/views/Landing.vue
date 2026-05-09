<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Logo from '@/components/common/Logo.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'

const router = useRouter()

const isMobile = ref(window.innerWidth < 768)

function handleResize() {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))

function enter() {
  router.push('/auth')
}

const features = [
  { n: '01', en: 'Read', cn: '读你', body: '一句话、一个场景、或一片色盘——AI 把你此刻的情绪翻译成可听见的频率。' },
  { n: '02', en: 'Compose', cn: '调台', body: '横跨多个平台，从你的红心、推荐池与全网曲库里编排一档专属节目。' },
  { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一部分，每周生成一份情绪周报。' },
]
</script>

<template>
  <div style="position:relative;overflow:hidden;min-height:100vh;background:var(--bg);">
    <div class="mood-blob drift" style="width:900px;height:900px;right:-200px;top:-260px;opacity:.55;" />
    <div class="mood-blob drift" style="width:620px;height:620px;left:-160px;bottom:-220px;opacity:.45;" />

    <div style="position:relative;z-index:2;padding:28px 56px;display:flex;align-items:center;justify-content:space-between;">
      <div class="row" style="gap:10px;">
        <Logo :size="26" />
        <span style="font-family:var(--serif-en);font-size:22px;font-style:italic;">
          Mood<span style="color:var(--ink-3)">FM</span>
        </span>
      </div>
      <div class="row mono" style="gap:28px;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:var(--ink-2);">
        <span>About</span><span>How it works</span><span>Manifesto</span>
      </div>
      <div class="row" style="gap:10px;">
        <button class="btn-pill" @click="enter">登录 · Sign in</button>
        <button class="btn" @click="enter">开始 · Start</button>
      </div>
    </div>

    <div style="position:relative;z-index:2;padding:40px 56px 0;display:grid;grid-template-columns:1.3fr 1fr;gap:56px;align-items:start;">
      <div>
        <div class="meta" style="margin-bottom:22px;">ISSUE №01 · 2026 / SPRING — A PRIVATE RADIO STATION</div>
        <h1 class="display" style="font-size:132px;margin:0;color:var(--ink);line-height:0.95;">
          <span style="display:block;">Tune into</span>
          <span style="display:block;">the <em style="color:transparent;-webkit-text-stroke:1px var(--ink);">weather</em></span>
          <span style="display:block;">of your <em>heart.</em></span>
        </h1>
        <div class="display-cn" style="font-size:44px;margin-top:28px;color:var(--ink);max-width:620px;">
          为此刻的你，<br/>调一台只属于你的电台。
        </div>
        <p style="margin-top:32px;max-width:460px;color:var(--ink-2);font-size:17px;line-height:1.6;font-family:var(--serif-cn);">
          MoodFM 读取你的情绪，跨越网易云与 QQ 音乐，从你的曲库与海量乐池中实时编排。
          一段文字、一张色盘、或者一个按钮——交给我们，剩下的就听吧。
        </p>
        <div class="row" style="gap:12px;margin-top:40px;flex-wrap:wrap;">
          <button class="btn" @click="enter">进入电台 · Enter →</button>
          <button class="btn btn-ghost">观看示例 · Watch demo</button>
        </div>
        <div class="meta" style="margin-top:56px;color:var(--ink-3);">▼ SCROLL · 三种方式开始你的电台</div>
      </div>

      <div style="display:flex;justify-content:flex-end;">
        <div style="position:relative;display:inline-block;">
          <MoodBlob :size="isMobile ? 280 : 460" :drift="true" geometry="blob" />
          <div style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;color:rgba(255,255,255,0.92);text-shadow:0 1px 12px rgba(0,0,0,0.2);pointer-events:none;">
            <div class="mono" style="font-size:11px;letter-spacing:.2em;opacity:.85;">NOW PLAYING · 北京 · 23:14</div>
            <div :style="{ fontFamily:'var(--serif-en)', fontStyle:'italic', fontSize: isMobile ? '32px' : '56px', marginTop:'8px' }">weightless</div>
            <div :style="{ fontFamily:'var(--serif-cn)', fontSize: isMobile ? '14px' : '16px', marginTop:'4px', opacity:0.85 }">晚归 · Late return</div>
          </div>
        </div>
      </div>
    </div>

    <hr class="rule" style="margin:60px 56px 0;" />
    <div style="position:relative;z-index:2;padding:32px 56px 80px;display:grid;grid-template-columns:repeat(3,1fr);">
      <div v-for="(f, i) in features" :key="f.n"
           :style="{ padding:'0 28px 0 0', borderRight: i < 2 ? '1px solid var(--rule)' : 'none' }">
        <div class="meta">CHAPTER {{ f.n }}</div>
        <div style="font-family:var(--serif-en);font-style:italic;font-size:48px;margin-top:6px;color:var(--ink);">
          {{ f.en }}<span style="color:var(--ink-3)">.</span>
        </div>
        <div style="font-family:var(--serif-cn);font-size:22px;font-weight:500;margin-top:4px;">{{ f.cn }}</div>
        <p style="color:var(--ink-2);font-size:14px;line-height:1.6;margin-top:12px;max-width:320px;">{{ f.body }}</p>
      </div>
    </div>

    <div style="position:absolute;left:0;right:0;bottom:0;padding:14px 56px;border-top:1px solid var(--rule);display:flex;justify-content:space-between;font-family:var(--mono);font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:var(--ink-3);">
      <span>© MoodFM 2026 / a private station</span>
      <span>网易云音乐 · QQ 音乐 · BiliBili Music</span>
      <span>v1.0 — issue spring</span>
    </div>
  </div>
</template>
