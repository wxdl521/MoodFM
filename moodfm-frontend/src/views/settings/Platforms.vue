<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { platformApi } from '@/api/platform'

const router = useRouter()
const loading = ref(true)
const error = ref<string | null>(null)

interface Binding {
  platform: string
  valid: boolean
  platformUsername?: string
  default?: boolean
  expiresAt?: string
}

const bindings = ref<Binding[]>([])
const pendingPlatform = ref<string | null>(null)

const PLATFORMS = [
  { key: 'netease', label: '网易云音乐', icon: '🎵' },
  { key: 'qqmusic', label: 'QQ 音乐', icon: '🎶' },
]

onMounted(async () => {
  try {
    const data = await platformApi.getBindings()
    bindings.value = data as Binding[]
  } catch (e: any) {
    error.value = e?.message ?? '加载失败'
  } finally {
    loading.value = false
  }
})

function getBinding(key: string) {
  return bindings.value.find(b => b.platform === key && b.valid)
}

function expiryStatus(b: Binding): { label: string; color: string } | null {
  if (!b.expiresAt) return null
  const days = Math.ceil((new Date(b.expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24))
  if (days < 0) return { label: '已过期', color: '#e05252' }
  if (days <= 7) return { label: `${days}天后过期`, color: '#d4a017' }
  return null
}

async function handleSetDefault(key: string) {
  if (pendingPlatform.value) return
  pendingPlatform.value = key
  try {
    await platformApi.setDefault(key as 'netease' | 'qqmusic')
    bindings.value = bindings.value.map(b => ({ ...b, isDefault: b.platform === key }))
  } catch (e: any) {
    alert('操作失败：' + (e?.message ?? ''))
  } finally {
    pendingPlatform.value = null
  }
}

async function handleUnbind(key: string) {
  if (pendingPlatform.value) return
  if (!confirm(`确定解绑 ${key === 'netease' ? '网易云音乐' : 'QQ 音乐'} 吗？历史记录将保留。`)) return
  pendingPlatform.value = key
  try {
    await platformApi.unbind(key as 'netease' | 'qqmusic')
    bindings.value = bindings.value.map(b =>
      b.platform === key ? { ...b, valid: false, platformUsername: undefined } : b
    )
  } catch (e: any) {
    alert('解绑失败：' + (e?.message ?? ''))
  } finally {
    pendingPlatform.value = null
  }
}
</script>

<template>
  <div style="min-height:100vh;background:var(--bg);padding-bottom:80px;">
    <div style="position:sticky;top:0;z-index:5;background:var(--bg);padding:22px 56px;
                border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;align-items:center;">
      <button class="btn-pill" @click="router.back()">← 返回</button>
      <div class="meta">PLATFORMS · 平台管理</div>
      <div style="width:80px;" />
    </div>

    <div style="max-width:680px;margin:0 auto;padding:48px 56px;">
      <div class="meta">YOUR CONNECTED PLATFORMS · 已绑定平台</div>
      <h1 class="display" style="font-size:72px;margin:10px 0 8px;">Music <em>sources</em>.</h1>
      <div style="font-family:var(--serif-cn);font-size:18px;color:var(--ink-2);margin-bottom:36px;">
        管理你绑定的音乐平台，切换默认音源
      </div>

      <div v-if="loading" class="meta" style="color:var(--ink-3);">加载中…</div>
      <div v-else-if="error" class="meta" style="color:var(--ink-3);">{{ error }}</div>

      <div v-else style="display:flex;flex-direction:column;gap:16px;">
        <div
          v-for="p in PLATFORMS"
          :key="p.key"
          :style="{
            padding: '24px 28px',
            border: '1px solid var(--rule)',
            borderRadius: '14px',
            background: 'var(--paper)',
          }"
        >
          <div class="row" style="align-items:center;gap:14px;margin-bottom:16px;">
            <span style="font-size:28px;">{{ p.icon }}</span>
            <div style="flex:1;">
              <div style="font-family:var(--serif-cn);font-size:17px;font-weight:500;">{{ p.label }}</div>
              <div class="meta" style="margin-top:3px;color:var(--ink-3);">
                <template v-if="getBinding(p.key)">
                  {{ getBinding(p.key)?.platformUsername ?? '已绑定' }}
                  <template v-if="getBinding(p.key)?.default"> · 默认音源</template>
                  <template v-if="expiryStatus(getBinding(p.key)!)">
                    <span :style="{ color: expiryStatus(getBinding(p.key)!)?.color }">
                      · {{ expiryStatus(getBinding(p.key)!)?.label }}
                    </span>
                  </template>
                </template>
                <template v-else>未绑定</template>
              </div>
            </div>
            <span
              v-if="getBinding(p.key)?.default"
              class="meta"
              style="padding:3px 10px;background:var(--ink);color:var(--bg);border-radius:999px;"
            >默认</span>
          </div>

          <div class="row" style="gap:8px;">
            <template v-if="getBinding(p.key)">
              <button
                v-if="!getBinding(p.key)?.default"
                class="btn-pill"
                style="flex:1;"
                :disabled="pendingPlatform === p.key"
                @click="handleSetDefault(p.key)"
              >{{ pendingPlatform === p.key ? '…' : '设为默认' }}</button>
              <button
                class="btn-pill"
                style="flex:1;color:#e05;border-color:#e05;"
                :disabled="pendingPlatform === p.key"
                @click="handleUnbind(p.key)"
              >{{ pendingPlatform === p.key ? '…' : '解绑' }}</button>
            </template>
            <template v-else>
              <button
                class="btn"
                style="flex:1;height:40px;"
                @click="router.push('/bind')"
              >去绑定 →</button>
            </template>
          </div>
        </div>
      </div>

      <details style="margin-top:36px;font-size:13px;color:var(--ink-3);">
        <summary style="cursor:pointer;font-family:var(--serif-cn);">你的账号信息如何被保护？</summary>
        <p style="margin-top:10px;line-height:1.8;">
          你的 Cookie 使用 AES-256-GCM 加密后存储在本地服务器，不上传至任何第三方。
          前端仅展示绑定状态，不展示完整 Cookie。你可以随时解绑。
        </p>
      </details>
    </div>
  </div>
</template>
