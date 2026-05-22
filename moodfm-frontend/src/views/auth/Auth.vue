<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Logo from '@/components/common/Logo.vue'

const router = useRouter()
const auth = useAuthStore()

const kind = ref<'login' | 'signup'>('login')

const loginId = ref('')
const loginPwd = ref('')
const remember = ref(true)

const signName = ref('')
const signEmail = ref('')
const signPwd = ref('')
const signCode = ref('')
const countdown = ref(0)
let cdTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  if (cdTimer) {
    clearInterval(cdTimer)
    cdTimer = null
  }
})

const loading = ref(false)
const globalError = ref('')

function sendCode() {
  if (countdown.value > 0) return
  countdown.value = 60
  cdTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && cdTimer) { clearInterval(cdTimer); cdTimer = null }
  }, 1000)
}

async function submit() {
  loading.value = true
  globalError.value = ''
  try {
    if (kind.value === 'login') {
      await auth.login({ account: loginId.value, password: loginPwd.value, rememberMe: remember.value })
      router.push('/home')
    } else {
      await auth.register({ username: signName.value, email: signEmail.value, password: signPwd.value })
      router.push('/onboarding')
    }
  } catch (e: any) {
    globalError.value = e?.response?.data?.message || e?.message || (kind.value === 'login' ? '登录失败，请重试' : '注册失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div style="display:grid;grid-template-columns:1fr 1fr;min-height:100vh;">
    <div style="position:relative;overflow:hidden;background:var(--bg);border-right:1px solid var(--rule);">
      <div class="mood-blob drift" style="width:720px;height:720px;left:-120px;top:-160px;opacity:.55;" />
      <div class="mood-blob drift" style="width:480px;height:480px;right:-100px;bottom:-120px;opacity:.4;" />
      <div style="position:absolute;inset:0;padding:56px;display:flex;flex-direction:column;justify-content:space-between;z-index:2;">
        <div class="row" style="gap:10px;">
          <Logo :size="28" />
          <span style="font-family:var(--serif-en);font-size:22px;font-style:italic;">MoodFM</span>
        </div>
        <div>
          <div class="meta">ISSUE №01 · {{ kind === 'signup' ? 'NEW LISTENERS' : 'WELCOME BACK' }}</div>
          <h1 class="display" style="font-size:96px;margin:14px 0 0;line-height:.95;">
            <template v-if="kind === 'signup'">Tune <em>in</em>.</template>
            <template v-else>Welcome <em>home</em>.</template>
          </h1>
          <div class="display-cn" style="font-size:32px;margin-top:16px;color:var(--ink-2);">
            {{ kind === 'signup' ? '今晚开始，你也有一座电台。' : '欢迎回来。电台已替你温着。' }}
          </div>
          <p style="margin-top:24px;font-size:15px;line-height:1.7;color:var(--ink-2);max-width:380px;font-family:var(--serif-cn);">
            {{ kind === 'signup'
              ? '我们读取你的情绪，跨越平台编排音乐——你只需要说一句话，或者什么都别说。'
              : '上一次你听到的是 Marconi Union 的《weightless》，进度 1:24。准备好继续了吗？' }}
          </p>
        </div>
        <div class="between" style="font-family:var(--mono);font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:var(--ink-3);">
          <span>SPRING / 2026</span><span>A PRIVATE STATION</span>
        </div>
      </div>
    </div>

    <div style="padding:64px 80px;display:flex;flex-direction:column;justify-content:center;">
      <div class="meta">{{ kind === 'signup' ? 'NEW ACCOUNT · 创建账号' : 'SIGN IN · 登录' }}</div>
      <h2 class="display" style="font-size:72px;margin:10px 0 0;">
        {{ kind === 'signup' ? 'Begin.' : 'Hi, again.' }}
      </h2>
      <div class="display-cn" style="font-size:28px;margin-top:6px;color:var(--ink-2);">
        {{ kind === 'signup' ? '注册 · 用一句话描述你' : '登录 · 继续昨晚的电台' }}
      </div>

      <div v-if="globalError" style="margin-top:16px;padding:12px 16px;border-radius:12px;background:rgba(232,90,138,0.08);border:1px solid var(--mood-b);">
        <span class="meta" style="color:var(--mood-b);">· {{ globalError }}</span>
      </div>

      <div v-if="kind === 'login'" style="margin-top:40px;">
        <div style="margin-bottom:14px;">
          <div class="meta" style="margin-bottom:8px;">邮箱 / 手机 · EMAIL OR PHONE</div>
          <input v-model="loginId" class="field" placeholder="you@somewhere.com" />
        </div>
        <div style="margin-bottom:14px;">
          <div class="meta" style="margin-bottom:8px;">密码 · PASSWORD</div>
          <input v-model="loginPwd" class="field" type="password" placeholder="·······" />
        </div>
        <label class="row" style="gap:10px;font-family:var(--serif-cn);font-size:13px;color:var(--ink-2);margin-bottom:18px;cursor:pointer;" @click="remember = !remember">
          <span :style="{ width:'16px', height:'16px', borderRadius:'4px', border:'1px solid var(--ink)',
                          background: remember ? 'var(--ink)' : 'transparent', display:'inline-flex',
                          alignItems:'center', justifyContent:'center', flexShrink:0 }">
            <span v-if="remember" style="width:8px;height:8px;background:var(--bg);border-radius:2px;" />
          </span>
          30 天内记住我 · Remember me
        </label>
        <button class="btn" style="width:100%;height:50px;" :disabled="loading" @click="submit">
          {{ loading ? '登录中...' : '登录 · SIGN IN →' }}
        </button>
        <div class="row" style="margin-top:24px;gap:12px;align-items:center;">
          <hr class="rule" style="flex:1;" /><span class="meta">OR</span><hr class="rule" style="flex:1;" />
        </div>
        <div class="row" style="gap:10px;margin-top:20px;">
          <button class="btn-pill" style="flex:1;height:44px;">网易云账号</button>
          <button class="btn-pill" style="flex:1;height:44px;">QQ 音乐账号</button>
        </div>
      </div>

      <div v-else style="margin-top:36px;">
        <div style="margin-bottom:14px;">
          <div class="meta" style="margin-bottom:8px;">昵称 · NAME</div>
          <input v-model="signName" class="field" placeholder="你叫什么" />
        </div>
        <div style="margin-bottom:14px;">
          <div class="meta" style="margin-bottom:8px;">邮箱 · EMAIL</div>
          <input v-model="signEmail" class="field" placeholder="you@somewhere.com" />
        </div>
        <div style="margin-bottom:14px;">
          <div class="meta" style="margin-bottom:8px;">密码 · PASSWORD</div>
          <input v-model="signPwd" class="field" type="password" placeholder="至少 8 位" />
        </div>
        <div style="margin-bottom:14px;position:relative;">
          <div class="meta" style="margin-bottom:8px;">验证码 · ONE-TIME CODE</div>
          <input v-model="signCode" class="field" placeholder="6 位" style="padding-right:130px;" />
          <button @click="sendCode"
            :style="{ position:'absolute', right:'6px', bottom:'6px', height:'36px', padding:'0 14px',
                      borderRadius:'999px', fontFamily:'var(--mono)', fontSize:'11px', letterSpacing:'.14em',
                      textTransform:'uppercase', border:'none', cursor: countdown > 0 ? 'not-allowed' : 'pointer',
                      background: countdown > 0 ? 'transparent' : 'var(--ink)',
                      color: countdown > 0 ? 'var(--ink-3)' : 'var(--bg)' }">
            {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
          </button>
        </div>
        <button class="btn" style="width:100%;height:50px;margin-top:8px;" :disabled="loading" @click="submit">
          {{ loading ? '注册中...' : '注册并启动 · CREATE ACCOUNT →' }}
        </button>
      </div>

      <div class="between" style="margin-top:24px;font-family:var(--serif-cn);font-size:14px;color:var(--ink-2);">
        <span>
          {{ kind === 'signup' ? '已有账号？' : '还没有账号？' }}
          <a style="color:var(--ink);text-decoration:underline;text-underline-offset:3px;cursor:pointer;"
             @click="kind = kind === 'signup' ? 'login' : 'signup'">
            {{ kind === 'signup' ? '登录' : '注册' }} →
          </a>
        </span>
        <a v-if="kind === 'login'" style="color:var(--ink-3);cursor:pointer;">忘记密码？</a>
      </div>

      <div class="meta" style="margin-top:auto;padding-top:40px;color:var(--ink-3);">
        BY CONTINUING YOU AGREE TO OUR
        <span style="color:var(--ink-2);text-decoration:underline;">TERMS</span> ·
        <span style="color:var(--ink-2);text-decoration:underline;">PRIVACY</span>
      </div>
    </div>
  </div>
</template>
