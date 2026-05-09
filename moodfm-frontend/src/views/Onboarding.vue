<template>
  <div class="mfm" style="min-height: 100vh; position: relative; background: var(--bg); overflow: hidden;">
    <div class="mood-blob drift" :style="{
      width: isMobile ? '500px' : '800px',
      height: isMobile ? '500px' : '800px',
      right: isMobile ? '-200px' : '-150px',
      top: '-200px',
      opacity: 0.4,
    }" />

    <div style="position: relative; z-index: 2; display: flex; align-items: center; justify-content: space-between;"
      :style="{ padding: isMobile ? '20px 22px' : '28px 56px' }">
      <div class="row" style="gap: 10px;">
        <Logo :size="isMobile ? 20 : 24" />
        <div :style="{ fontFamily: 'var(--serif-en)', fontSize: isMobile ? '16px' : '20px', fontStyle: 'italic' }">MoodFM</div>
      </div>
      <div class="meta">STEP {{ String(step).padStart(2, '0') }} / {{ String(total).padStart(2, '0') }}</div>
      <a @click="finish" style="cursor: pointer;" class="meta">SKIP →</a>
    </div>

    <div class="row" :style="{ position: 'relative', zIndex: 2, gap: '4px', padding: isMobile ? '0 22px' : '0 56px' }">
      <div v-for="s in total" :key="s"
        :style="{ flex: 1, height: '2px', background: s <= step ? 'var(--ink)' : 'var(--rule)', transition: 'background .3s' }" />
    </div>

    <div :style="{
      position: 'relative', zIndex: 2,
      padding: isMobile ? '24px 22px 100px' : '40px 56px 100px',
      minHeight: 'calc(100vh - 200px)',
    }">
      <div v-if="step === 1">
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
          <div v-for="c in welcomeCards" :key="c.n" :style="{ background: 'var(--paper)', padding: isMobile ? '24px 20px' : '32px 28px' }">
            <div class="meta">№ {{ c.n }}</div>
            <div class="serif-en" :style="{ fontSize: isMobile ? '44px' : '60px', marginTop: '8px' }">
              {{ c.en }}<span style="color: var(--ink-3);">.</span>
            </div>
            <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '22px', fontWeight: 500, marginTop: '4px' }">{{ c.cn }}</div>
            <p style="color: var(--ink-2); font-size: 14px; line-height: 1.7; margin-top: 14px;">{{ c.body }}</p>
          </div>
        </div>
      </div>

      <div v-else-if="step === 2">
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
          <div v-for="p in bindPlatforms" :key="p.name"
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
              <button class="btn" style="height: 38px; padding: 0 16px;" @click="goToBind">绑定 →</button>
            </div>
          </div>
        </div>

        <div class="meta" style="margin-top: 28px; color: var(--ink-3); max-width: 500px;">
          · 我们仅读取曲库与红心 · Cookie 加密存储 · 随时可解绑
        </div>
      </div>

      <div v-else-if="step === 3">
        <div class="meta">CHAPTER THREE · 偏好</div>
        <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '96px', margin: '12px 0 0' }">
          What's in your
        </h1>
        <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '96px', margin: 0 }">
          <em>weather</em>?
        </h1>
        <div class="display-cn" :style="{ fontSize: isMobile ? '20px' : '26px', marginTop: '12px', color: 'var(--ink-2)' }">
          说说你的"音乐天气"——这些会成为电台的初始风向。
        </div>

        <div style="margin-top: 36px;">
          <div class="meta" style="margin-bottom: 12px;">FIELD A · 流派 / GENRES (多选)</div>
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <button
              v-for="g in allGenres"
              :key="g"
              :class="['btn-pill', { active: selectedGenres.has(g) }]"
              @click="toggle(selectedGenres, g)"
            >{{ g }}</button>
          </div>
        </div>

        <div style="margin-top: 28px;">
          <div class="meta" style="margin-bottom: 12px;">FIELD B · 语言 / LANGUAGES (多选)</div>
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <button
              v-for="l in allLang"
              :key="l"
              :class="['btn-pill', { active: selectedLang.has(l) }]"
              @click="toggle(selectedLang, l)"
            >{{ l }}</button>
          </div>
        </div>

        <div style="margin-top: 28px;">
          <div class="meta" style="margin-bottom: 12px;">FIELD C · 默认场景 / DEFAULT SCENE (单选)</div>
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <button
              v-for="s in allScene"
              :key="s"
              :class="['btn-pill', { active: selectedScene === s }]"
              @click="selectedScene = s"
            >{{ s }}</button>
          </div>
        </div>
      </div>

      <div v-else-if="step === 4" :style="{
        display: 'flex',
        flexDirection: isMobile ? 'column' : 'row',
        alignItems: 'center',
        gap: isMobile ? '32px' : '56px',
        paddingTop: isMobile ? '20px' : '60px',
      }">
        <MoodBlob :size="isMobile ? 280 : 380" :drift="true" geometry="blob" />
        <div>
          <div class="meta">CHAPTER FOUR · 准备好了</div>
          <h1 class="display" :style="{ fontSize: isMobile ? '60px' : '120px', margin: '12px 0 0' }">
            You're <em>tuned</em>.
          </h1>
          <div class="display-cn" :style="{ fontSize: isMobile ? '26px' : '36px', marginTop: '12px' }">
            调好了。今晚第一档电台准备启动。
          </div>
          <p :style="{ marginTop: '20px', fontSize: '15px', lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: '460px', fontFamily: 'var(--serif-cn)' }">
            已绑定 1 个平台 · 偏好已记录 · 你的播放历史会持续优化推荐。
            点"进入电台"——我们已经替你准备了第一首。
          </p>
          <div class="row" style="margin-top: 28px; gap: 10px; color: var(--ink-3);">
            <span class="meta">FIRST UP →</span>
            <span :style="{ fontFamily: 'var(--serif-en)', fontSize: '22px', fontStyle: 'italic', color: 'var(--ink)' }">weightless</span>
            <span :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', color: 'var(--ink-2)' }">· Marconi Union</span>
          </div>
        </div>
      </div>
    </div>

    <div style="position: fixed; left: 0; right: 0; bottom: 0; z-index: 5; background: var(--bg); border-top: 1px solid var(--rule); display: flex; align-items: center; justify-content: space-between; gap: 12px;"
      :style="{ padding: isMobile ? '14px 22px' : '20px 56px' }">
      <button class="btn-pill" @click="prev" :style="{ visibility: step === 1 ? 'hidden' : 'visible' }">
        ← 上一步
      </button>
      <div class="meta" style="color: var(--ink-3);">
        <span v-if="step === 1">欢迎介绍</span>
        <span v-else-if="step === 2">绑定平台 · 必选</span>
        <span v-else-if="step === 3">偏好设置</span>
        <span v-else>准备就绪</span>
      </div>
      <button class="btn" @click="next">
        <template v-if="step < total">下一步 →</template>
        <template v-else>进入电台 →</template>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Logo from '@/components/common/Logo.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'

const router = useRouter()
const isMobile = ref(window.innerWidth < 768)
const step = ref(1)
const total = 4

function handleResize() { isMobile.value = window.innerWidth < 768 }
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))

function next() {
  if (step.value < total) step.value++
  else finish()
}
function prev() {
  if (step.value > 1) step.value--
}
function finish() {
  router.push('/home')
}
function goToBind() {
  router.push('/bind')
}

const welcomeCards = [
  { n: '01', en: 'Read', cn: '读你', body: '说一句话、选一个场景、或在色盘上标个点。AI 帮你把感觉翻译成可听见的频率。' },
  { n: '02', en: 'Compose', cn: '调台', body: '横跨网易云与 QQ 音乐，从你的红心和全网曲库实时编排——一档只属于此刻的电台。' },
  { n: '03', en: 'Remember', cn: '记得', body: '每次收听都成为你心情地图的一笔。每周一份情绪周报，长图分享给朋友。' },
]

const bindPlatforms = [
  { logo: '网', name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', recommended: true },
  { logo: 'Q', name: 'QQ 音乐', en: 'QQ MUSIC', recommended: false },
]

const allGenres = ['Ambient', 'Classical', 'Folk', 'Indie', 'Electronic', 'Jazz', 'Hip-Hop', 'Rock', 'Pop', 'R&B', 'Post-Rock', 'Bossa Nova']
const allLang = ['中文', 'English', '日本語', '한국어', 'Français', 'Español', 'Instrumental']
const allScene = ['通勤', '学习', '跑步', '写作', '睡前', '深夜', '派对', '咖啡馆']

const selectedGenres = reactive(new Set(['Ambient', 'Folk', 'Indie']))
const selectedLang = reactive(new Set(['中文', 'English', '日本語']))
const selectedScene = ref('深夜')

function toggle(set: Set<string>, value: string) {
  if (set.has(value)) set.delete(value)
  else set.add(value)
}
</script>
