<template>
  <div class="mfm" style="min-height: 100vh; position: relative; background: var(--bg); overflow: hidden;">
    <div class="mood-blob drift" style="width: 700px; height: 700px; right: -200px; top: -200px; opacity: 0.3;" />

    <div style="position: sticky; top: 0; z-index: 5; background: var(--bg); border-bottom: 1px solid var(--rule); display: flex; align-items: center; justify-content: space-between;"
      :style="{ padding: isMobile ? '16px 22px' : '22px 56px' }">
      <button class="btn-pill" @click="$router.back()">← 返回</button>
      <div class="meta">PLATFORM · 平台绑定</div>
      <button class="btn-pill" @click="$router.push('/home')">完成 →</button>
    </div>

    <div :style="{
      position: 'relative', zIndex: 2,
      padding: isMobile ? '24px 22px 60px' : '48px 56px 80px',
      display: 'grid',
      gridTemplateColumns: isMobile ? '1fr' : '1.2fr 1fr',
      gap: '48px',
    }">
      <div>
        <div class="meta">SECTION II · 接入</div>
        <h1 class="display" :style="{ fontSize: isMobile ? '52px' : '108px', margin: '12px 0 0' }">
          Plug in.
        </h1>
        <div class="display-cn" :style="{ fontSize: isMobile ? '22px' : '32px', marginTop: '8px', color: 'var(--ink-2)' }">
          把你的曲库接进来。
        </div>
        <p :style="{ marginTop: '18px', fontSize: '14px', lineHeight: 1.7, color: 'var(--ink-2)', maxWidth: '480px', fontFamily: 'var(--serif-cn)' }">
          支持网易云音乐与 QQ 音乐。我们读取你的曲库与红心，跨平台编排——所有凭证都本地加密，可随时解绑。
        </p>

        <div style="margin-top: 32px; display: grid; grid-template-columns: 1fr; gap: 14px;">
          <div v-for="p in platforms" :key="p.id"
            :style="{
              padding: '20px',
              border: '1px solid var(--rule)',
              borderRadius: '18px',
              background: 'var(--paper)',
              position: 'relative',
              opacity: p.disabled ? 0.5 : 1,
            }">
            <div class="row" style="gap: 14px;">
              <div :style="{
                width: '48px', height: '48px', borderRadius: '50%',
                background: p.bound ? 'linear-gradient(135deg, var(--mood-a), var(--mood-c))' : 'var(--bg-2)',
                color: p.bound ? '#fff' : 'var(--ink-3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontFamily: 'var(--serif-cn)', fontSize: '20px', fontWeight: 600,
                flexShrink: 0,
              }">{{ p.logo }}</div>
              <div style="flex: 1; min-width: 0;">
                <div class="row" style="gap: 8px;">
                  <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '18px', fontWeight: 500 }">{{ p.name }}</div>
                  <span v-if="p.bound" class="meta" style="padding: 2px 8px; border: 1px solid var(--ink); border-radius: 999px; color: var(--ink);">BOUND</span>
                </div>
                <div class="meta" style="margin-top: 2px;">{{ p.en }}</div>
              </div>
              <button v-if="!p.disabled" @click="openModal(p.id)"
                :class="p.bound ? 'btn-pill' : 'btn'"
                style="height: 38px; padding: 0 16px; flex-shrink: 0;">
                {{ p.bound ? '重新绑定' : '绑定 →' }}
              </button>
            </div>
            <div class="row" style="margin-top: 16px; gap: 24px; padding-top: 16px; border-top: 1px dashed var(--rule);">
              <div>
                <div class="meta" style="color: var(--ink-3);">状态</div>
                <div :style="{
                  fontFamily: 'var(--mono)', fontSize: '13px', marginTop: '4px',
                  color: p.expired ? '#e05252' : p.expiringSoon ? '#d4a017' : 'var(--ink)'
                }">{{ p.status }}</div>
              </div>
            </div>
          </div>
        </div>

        <details style="margin-top: 28px; padding: 18px; border: 1px solid var(--rule); border-radius: 14px; background: var(--paper);">
          <summary :style="{ cursor: 'pointer', fontFamily: 'var(--serif-cn)', fontSize: '14px' }">
            <span class="meta" style="margin-right: 10px;">·</span>
            账号信息如何被加密保护？
          </summary>
          <div style="margin-top: 12px; font-size: 13px; line-height: 1.7; color: var(--ink-2); font-family: var(--serif-cn);">
            所有 Cookie 与 Token 仅存储在你的本地与端到端加密的数据库；服务端永不留存明文。每 30 天自动续期一次，过期会主动提醒你重绑。
          </div>
        </details>
      </div>

      <div v-if="!isMobile" style="position: sticky; top: 100px; align-self: start;">
        <div :style="{
          background: 'var(--paper)',
          borderRadius: '20px',
          border: '1px solid var(--rule)',
          padding: '28px',
        }">
          <div class="between" style="margin-bottom: 18px;">
            <div>
              <div class="meta">PLATFORM</div>
              <div class="display" style="font-size: 36px; margin-top: 4px;">
                <em>{{ activePlatform }}</em>
              </div>
            </div>
          </div>

          <div class="row" style="gap: 0; border-bottom: 1px solid var(--rule); margin-bottom: 24px;">
            <button
              v-for="t in tabs"
              :key="t.k"
              @click="activeTab = t.k"
              :style="{
                background: 'transparent', border: 'none', padding: '12px 14px',
                cursor: 'pointer', position: 'relative',
                fontFamily: 'var(--serif-cn)', fontSize: '14px',
                color: activeTab === t.k ? 'var(--ink)' : 'var(--ink-3)',
              }">
              <span class="mono" style="font-size: 10px; letter-spacing: .16em; color: var(--ink-3); margin-right: 6px;">{{ t.e }}</span>
              {{ t.l }}
              <div v-if="activeTab === t.k" style="position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: var(--ink);" />
            </button>
          </div>

          <div v-if="activeTab === 'qr'" style="text-align: center;">
            <div style="display: inline-block; position: relative; padding: 24px; border: 1px solid var(--rule); border-radius: 18px; background: var(--bg);">
              <div style="width: 200px; height: 200px; position: relative; background: #fff; padding: 8px;">
                <img v-if="qrImageUrl" :src="qrImageUrl" alt="QR Code" style="width: 184px; height: 184px; display: block;" />

                <Transition name="fade">
                  <div v-if="qrStatus === 'expired'"
                    style="position: absolute; inset: 0; background: rgba(255,255,255,0.92); display: flex; flex-direction: column; align-items: center; justify-content: center;">
                    <div class="meta">QR EXPIRED</div>
                    <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', marginTop: '4px', color: 'var(--ink-2)' }">二维码已过期</div>
                    <button class="btn" style="margin-top: 12px; height: 36px;" @click="refreshQr">刷新</button>
                  </div>
                </Transition>
              </div>
            </div>

            <div class="meta" style="margin-top: 18px;">
              STATUS ·
              <span :style="{
                color: qrStatus === 'waiting' ? 'var(--ink)'
                  : qrStatus === 'expired' ? 'var(--mood-b)'
                  : 'var(--mood-c)'
              }">
                <span v-if="qrStatus === 'waiting'">等待扫码</span>
                <span v-else-if="qrStatus === 'scanned'">已扫码 · 请在手机上确认</span>
                <span v-else-if="qrStatus === 'success'">登录成功</span>
                <span v-else>二维码已过期</span>
              </span>
            </div>

            <div v-if="qrStatus === 'waiting' || qrStatus === 'scanned'" :style="{ marginTop: '8px', fontFamily: 'var(--mono)', fontSize: '13px', color: 'var(--ink-2)' }">
              {{ qrCountdown }}s 后过期 · refresh in {{ qrCountdown }}s
            </div>

            <div :style="{ marginTop: '16px', fontFamily: 'var(--serif-cn)', fontSize: '13px', color: 'var(--ink-2)' }">
              打开{{ activePlatform }} App → 我的 → 扫一扫
            </div>
          </div>

          <div v-else-if="activeTab === 'phone'">
            <div style="margin-bottom: 14px;">
              <div class="meta" style="margin-bottom: 8px;">手机号 · PHONE</div>
              <input v-model="phone" class="field" placeholder="+86 138 ····" type="tel" />
            </div>

            <div style="margin-bottom: 18px; position: relative;">
              <div class="meta" style="margin-bottom: 8px;">验证码 · OTP</div>
              <input v-model="phoneCode" class="field" placeholder="6 位" style="padding-right: 130px;" />
              <button @click="sendPhoneCode" :style="{
                position: 'absolute', right: '6px', bottom: '6px',
                height: '36px', padding: '0 14px', borderRadius: '999px',
                background: phoneCountdown > 0 ? 'transparent' : 'var(--ink)',
                color: phoneCountdown > 0 ? 'var(--ink-3)' : 'var(--bg)',
                border: phoneCountdown > 0 ? '1px solid var(--rule)' : 'none',
                cursor: phoneCountdown > 0 ? 'not-allowed' : 'pointer',
                fontFamily: 'var(--mono)', fontSize: '11px', letterSpacing: '.14em', textTransform: 'uppercase',
              }">{{ phoneCountdown > 0 ? `${phoneCountdown}s` : '获取验证码' }}</button>
            </div>

            <div v-if="phoneError" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ phoneError }}</div>

            <button class="btn" style="width: 100%; height: 48px;" :disabled="phoneLoading" @click="submitPhone">
              {{ phoneLoading ? '验证中...' : '验证并绑定 →' }}
            </button>
            <div class="meta" style="margin-top: 14px; color: var(--ink-3);">
              · 与 App 内手机号一致 · 验证后立即同步曲库
            </div>
          </div>

          <div v-else-if="activeTab === 'cookie'">
            <!-- QQ Music: bookmarklet one-click guide -->
            <template v-if="isQQMusic">
              <div style="margin-bottom: 16px;">
                <div class="meta" style="margin-bottom: 10px;">一键绑定 · QUICK BIND</div>
                <div style="font-family: var(--serif-cn); font-size: 13px; color: var(--ink-2); line-height: 2.2;">
                  ① 将下方按钮<strong>拖到浏览器书签栏</strong><br />
                  ② 打开 <a href="https://y.qq.com" target="_blank" rel="noopener" style="color: var(--ink); text-decoration: underline;">y.qq.com</a> 并登录<br />
                  ③ 点击书签栏中的按钮，自动跳回并填入 Cookie
                </div>
                <div style="display: flex; justify-content: center; margin: 14px 0; padding: 16px; border: 2px dashed var(--rule); border-radius: 12px; background: var(--bg);">
                  <a :href="bookmarkletCode"
                     style="padding: 10px 24px; background: var(--ink); color: var(--bg); border-radius: 999px; font-family: var(--mono); font-size: 11px; letter-spacing: .12em; text-decoration: none; cursor: grab; user-select: none; white-space: nowrap;"
                     @click.prevent
                     title="拖到书签栏">
                    🔑 获取 QQ 音乐 Cookie
                  </a>
                </div>
                <div class="meta" style="text-align: center; color: var(--ink-3);">↑ 拖到书签栏 · 然后在 y.qq.com 上点击</div>
              </div>
              <div class="meta" style="margin-bottom: 8px; padding-top: 12px; border-top: 1px dashed var(--rule);">或手动粘贴 · MANUAL PASTE</div>
            </template>
            <template v-else>
              <div class="between" style="margin-bottom: 8px;">
                <div class="meta">COOKIE</div>
              </div>
            </template>

            <div style="margin-bottom: 14px;">
              <textarea
                v-model="cookieValue"
                class="field"
                :placeholder="isQQMusic ? 'uin=o0...; qm_keyst=...; （书签工具会自动填入）' : 'MUSIC_U=...; __csrf=...; NMTID=...;'"
                :style="{ minHeight: '110px', fontFamily: 'var(--mono)', fontSize: '12px' }"
              />
            </div>

            <div v-if="cookieError" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ cookieError }}</div>

            <button class="btn" style="width: 100%; height: 48px;" :disabled="cookieLoading" @click="submitCookie">
              {{ cookieLoading ? '验证中...' : '验证并保存 →' }}
            </button>
            <div class="meta" style="margin-top: 14px; color: var(--ink-3); line-height: 1.6;">
              · Cookie 会本地加密存储 · 服务端不留存明文
            </div>
          </div>
        </div>
      </div>
    </div>

    <Transition name="sheet">
      <div v-if="isMobile && modalOpen"
        style="position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 50; display: flex; align-items: flex-end;"
        @click.self="modalOpen = false">
        <div style="width: 100%; background: var(--bg); border-top-left-radius: 24px; border-top-right-radius: 24px; max-height: 88vh; overflow: auto;">
          <div style="padding: 28px;">
            <div class="between" style="margin-bottom: 18px;">
              <div>
                <div class="meta">PLATFORM</div>
                <div class="display" style="font-size: 36px; margin-top: 4px;">
                  <em>{{ activePlatform }}</em>
                </div>
              </div>
              <button class="btn-pill" @click="modalOpen = false" style="font-size: 18px; line-height: 1; padding: 0 14px;">×</button>
            </div>

            <div class="row" style="gap: 0; border-bottom: 1px solid var(--rule); margin-bottom: 24px;">
              <button
                v-for="t in tabs"
                :key="t.k"
                @click="activeTab = t.k"
                :style="{
                  background: 'transparent', border: 'none', padding: '12px 14px',
                  cursor: 'pointer', position: 'relative',
                  fontFamily: 'var(--serif-cn)', fontSize: '14px',
                  color: activeTab === t.k ? 'var(--ink)' : 'var(--ink-3)',
                }">
                <span class="mono" style="font-size: 10px; letter-spacing: .16em; color: var(--ink-3); margin-right: 6px;">{{ t.e }}</span>
                {{ t.l }}
                <div v-if="activeTab === t.k" style="position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: var(--ink);" />
              </button>
            </div>

            <div v-if="activeTab === 'qr'" style="text-align: center;">
              <div style="display: inline-block; position: relative; padding: 24px; border: 1px solid var(--rule); border-radius: 18px; background: var(--bg);">
                <div style="width: 200px; height: 200px; position: relative; background: #fff; padding: 8px;">
                  <img v-if="qrImageUrl" :src="qrImageUrl" alt="QR Code" style="width: 184px; height: 184px; display: block;" />

                  <Transition name="fade">
                    <div v-if="qrStatus === 'expired'"
                      style="position: absolute; inset: 0; background: rgba(255,255,255,0.92); display: flex; flex-direction: column; align-items: center; justify-content: center;">
                      <div class="meta">QR EXPIRED</div>
                      <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', marginTop: '4px', color: 'var(--ink-2)' }">二维码已过期</div>
                      <button class="btn" style="margin-top: 12px; height: 36px;" @click="refreshQr">刷新</button>
                    </div>
                  </Transition>
                </div>
              </div>

              <div class="meta" style="margin-top: 18px;">
                STATUS ·
                <span :style="{
                  color: qrStatus === 'waiting' ? 'var(--ink)'
                    : qrStatus === 'expired' ? 'var(--mood-b)'
                    : 'var(--mood-c)'
                }">
                  <span v-if="qrStatus === 'waiting'">等待扫码</span>
                  <span v-else-if="qrStatus === 'scanned'">已扫码 · 请在手机上确认</span>
                  <span v-else-if="qrStatus === 'success'">登录成功</span>
                  <span v-else>二维码已过期</span>
                </span>
              </div>

              <div v-if="qrStatus === 'waiting' || qrStatus === 'scanned'" :style="{ marginTop: '8px', fontFamily: 'var(--mono)', fontSize: '13px', color: 'var(--ink-2)' }">
                {{ qrCountdown }}s 后过期 · refresh in {{ qrCountdown }}s
              </div>

              <div :style="{ marginTop: '16px', fontFamily: 'var(--serif-cn)', fontSize: '13px', color: 'var(--ink-2)' }">
                打开{{ activePlatform }} App → 我的 → 扫一扫
              </div>
            </div>

            <div v-else-if="activeTab === 'phone'">
              <div style="margin-bottom: 14px;">
                <div class="meta" style="margin-bottom: 8px;">手机号 · PHONE</div>
                <input v-model="phone" class="field" placeholder="+86 138 ····" type="tel" />
              </div>

              <div style="margin-bottom: 18px; position: relative;">
                <div class="meta" style="margin-bottom: 8px;">验证码 · OTP</div>
                <input v-model="phoneCode" class="field" placeholder="6 位" style="padding-right: 130px;" />
                <button @click="sendPhoneCode" :style="{
                  position: 'absolute', right: '6px', bottom: '6px',
                  height: '36px', padding: '0 14px', borderRadius: '999px',
                  background: phoneCountdown > 0 ? 'transparent' : 'var(--ink)',
                  color: phoneCountdown > 0 ? 'var(--ink-3)' : 'var(--bg)',
                  border: phoneCountdown > 0 ? '1px solid var(--rule)' : 'none',
                  cursor: phoneCountdown > 0 ? 'not-allowed' : 'pointer',
                  fontFamily: 'var(--mono)', fontSize: '11px', letterSpacing: '.14em', textTransform: 'uppercase',
                }">{{ phoneCountdown > 0 ? `${phoneCountdown}s` : '获取验证码' }}</button>
              </div>

              <div v-if="phoneError" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ phoneError }}</div>

              <button class="btn" style="width: 100%; height: 48px;" :disabled="phoneLoading" @click="submitPhone">
                {{ phoneLoading ? '验证中...' : '验证并绑定 →' }}
              </button>
              <div class="meta" style="margin-top: 14px; color: var(--ink-3);">
                · 与 App 内手机号一致 · 验证后立即同步曲库
              </div>
            </div>

            <div v-else-if="activeTab === 'cookie'">
              <!-- QQ Music: bookmarklet one-click guide (mobile) -->
              <template v-if="isQQMusic">
                <div style="margin-bottom: 16px;">
                  <div class="meta" style="margin-bottom: 10px;">一键绑定 · QUICK BIND</div>
                  <div style="font-family: var(--serif-cn); font-size: 13px; color: var(--ink-2); line-height: 2.2;">
                    ① 将下方按钮<strong>拖到浏览器书签栏</strong><br />
                    ② 打开 <a href="https://y.qq.com" target="_blank" rel="noopener" style="color: var(--ink); text-decoration: underline;">y.qq.com</a> 并登录<br />
                    ③ 点击书签栏中的按钮，自动跳回并填入 Cookie
                  </div>
                  <div style="display: flex; justify-content: center; margin: 14px 0; padding: 16px; border: 2px dashed var(--rule); border-radius: 12px; background: var(--bg);">
                    <a :href="bookmarkletCode"
                       style="padding: 10px 24px; background: var(--ink); color: var(--bg); border-radius: 999px; font-family: var(--mono); font-size: 11px; letter-spacing: .12em; text-decoration: none; cursor: grab; user-select: none; white-space: nowrap;"
                       @click.prevent
                       title="拖到书签栏">
                      🔑 获取 QQ 音乐 Cookie
                    </a>
                  </div>
                  <div class="meta" style="text-align: center; color: var(--ink-3);">↑ 拖到书签栏 · 然后在 y.qq.com 上点击</div>
                </div>
                <div class="meta" style="margin-bottom: 8px; padding-top: 12px; border-top: 1px dashed var(--rule);">或手动粘贴 · MANUAL PASTE</div>
              </template>
              <template v-else>
                <div class="between" style="margin-bottom: 8px;">
                  <div class="meta">COOKIE</div>
                </div>
              </template>

              <div style="margin-bottom: 14px;">
                <textarea
                  v-model="cookieValue"
                  class="field"
                  :placeholder="isQQMusic ? 'uin=o0...; qm_keyst=...; （书签工具会自动填入）' : 'MUSIC_U=...; __csrf=...; NMTID=...;'"
                  :style="{ minHeight: '110px', fontFamily: 'var(--mono)', fontSize: '12px' }"
                />
              </div>

              <div v-if="cookieError" class="meta" style="margin-bottom: 12px; color: var(--mood-b);">· {{ cookieError }}</div>

              <button class="btn" style="width: 100%; height: 48px;" :disabled="cookieLoading" @click="submitCookie">
                {{ cookieLoading ? '验证中...' : '验证并保存 →' }}
              </button>
              <div class="meta" style="margin-top: 14px; color: var(--ink-3); line-height: 1.6;">
                · Cookie 会本地加密存储 · 服务端不留存明文
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { platformApi } from '@/api/platform'
import { logger } from '@/utils/logger'

const isMobile = ref(window.innerWidth < 768)
const modalOpen = ref(false)
const activePlatform = ref('网易云音乐')
const activeTab = ref<'qr' | 'phone' | 'cookie'>('qr')

function handleResize() {
  isMobile.value = window.innerWidth < 768
}
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  clearQrTimers()
  if (phoneTimer) clearInterval(phoneTimer)
})

const platforms = reactive([
  { id: '网易云音乐', logo: '网', name: '网易云音乐', en: 'NETEASE CLOUD MUSIC', status: '未绑定', bound: false, disabled: false, expiringSoon: false, expired: false },
  { id: 'QQ音乐', logo: 'Q', name: 'QQ 音乐', en: 'QQ MUSIC', status: '未绑定', bound: false, disabled: false, expiringSoon: false, expired: false },
  { id: 'Bilibili', logo: 'B', name: 'Bilibili 音乐', en: 'BILIBILI MUSIC', status: '即将上线', bound: false, disabled: true, expiringSoon: false, expired: false },
])

const tabs = [
  { k: 'qr' as const, l: '扫码登录', e: 'QR' },
  { k: 'phone' as const, l: '手机号', e: 'PHONE' },
  { k: 'cookie' as const, l: 'Cookie', e: 'ADV' },
]

function openModal(platformId: string) {
  activePlatform.value = platformId
  activeTab.value = platformId === 'QQ音乐' ? 'cookie' : 'qr'
  if (isMobile.value) {
    modalOpen.value = true
  }
  if (activeTab.value === 'qr') {
    refreshQr()
  }
}

const PLATFORM_ID_MAP: Record<string, 'netease' | 'qqmusic'> = {
  '网易云音乐': 'netease',
  'QQ音乐': 'qqmusic',
}

function getPlatformId(name: string): 'netease' | 'qqmusic' | null {
  return PLATFORM_ID_MAP[name] ?? null
}

const isQQMusic = computed(() => activePlatform.value === 'QQ音乐')

// Bookmarklet: runs on y.qq.com, extracts cookie, opens MoodFM with it in the URL fragment
const bookmarkletCode = `javascript:(function(){if(location.hostname.indexOf('qq.com')<0){alert('请先打开 y.qq.com 并登录');return;}var c=document.cookie;if(!c){alert('未检测到 Cookie，请确认已在 y.qq.com 登录');return;}window.open('${window.location.origin}/bind?auto=qqmusic#cookie='+encodeURIComponent(c),'_blank');})();`

const qrStatus = ref<'waiting' | 'scanned' | 'success' | 'expired'>('waiting')
const qrCountdown = ref(90)
const qrImageUrl = ref('')
const qrKey = ref('')
let qrTimer: ReturnType<typeof setInterval> | null = null
let qrPollTimer: ReturnType<typeof setInterval> | null = null

function clearQrTimers() {
  if (qrTimer) { clearInterval(qrTimer); qrTimer = null }
  if (qrPollTimer) { clearInterval(qrPollTimer); qrPollTimer = null }
}

function startQrTimer() {
  if (qrTimer) clearInterval(qrTimer)
  qrTimer = setInterval(() => {
    if (qrStatus.value !== 'waiting' && qrStatus.value !== 'scanned') return
    qrCountdown.value--
    if (qrCountdown.value <= 0) {
      qrStatus.value = 'expired'
      clearQrTimers()
    }
  }, 1000)
}

function startQrPolling(platform: string) {
  if (qrPollTimer) clearInterval(qrPollTimer)
  const platformId = getPlatformId(platform)
  if (!platformId || !qrKey.value) return

  qrPollTimer = setInterval(async () => {
    try {
      const res = await platformApi.checkQRStatus(platformId, qrKey.value)
      if (res.status === 'scanned') {
        qrStatus.value = 'scanned'
      } else if (res.status === 'success') {
        qrStatus.value = 'success'
        clearQrTimers()
        loadBindings()
      } else if (res.status === 'expired') {
        qrStatus.value = 'expired'
        clearQrTimers()
      } else if (res.status === 'error') {
        qrStatus.value = 'expired'
        clearQrTimers()
      }
    } catch (err) {
      // silent: 轮询单次失败属于正常情况，下一周期继续，不弹 toast 骚扰
      logger.warn('bind:qr-poll', err)
    }
  }, 2500)
}

async function refreshQr() {
  clearQrTimers()
  qrCountdown.value = 90
  qrStatus.value = 'waiting'
  qrImageUrl.value = ''

  const platformId = getPlatformId(activePlatform.value)
  if (!platformId) return

  try {
    const res = await platformApi.generateQR(platformId)
    qrKey.value = res.key
    qrImageUrl.value = res.qrimg
    startQrTimer()
    startQrPolling(activePlatform.value)
  } catch (err) {
    // 用户主动操作：二维码生成失败，UI 已用 expired 状态提示，再记日志
    logger.warn('bind:qr-generate', err)
    qrStatus.value = 'expired'
  }
}

function daysUntil(dateStr?: string): number | null {
  if (!dateStr) return null
  const diff = new Date(dateStr).getTime() - Date.now()
  return Math.ceil(diff / (1000 * 60 * 60 * 24))
}

async function loadBindings() {
  try {
    const bindings = await platformApi.getBindings()
    for (const b of bindings) {
      const p = platforms.find(pp => PLATFORM_ID_MAP[pp.id] === b.platform)
      if (p) {
        const days = daysUntil(b.expiresAt)
        p.expired = !b.valid
        p.expiringSoon = b.valid && days !== null && days <= 7 && days >= 0
        if (!b.valid) {
          p.bound = false
          p.status = 'Cookie 已过期，请重新绑定'
        } else if (p.expiringSoon && days !== null) {
          p.bound = true
          p.status = b.platformUsername ? `已绑定 · ${b.platformUsername} · ${days}天后过期` : `已绑定 · ${days}天后过期`
        } else {
          p.bound = true
          p.status = b.platformUsername ? `已绑定 · ${b.platformUsername}` : '已绑定'
        }
      }
    }
  } catch (err) {
    // silent: 绑定列表加载失败时保留当前展示状态，避免遮挡 UI
    logger.warn('bind:load-bindings', err)
  }
}

onMounted(() => {
  loadBindings()
  // Auto-fill cookie from bookmarklet redirect: /bind?auto=qqmusic#cookie=<encoded>
  const params = new URLSearchParams(window.location.search)
  if (params.get('auto') === 'qqmusic' && window.location.hash.startsWith('#cookie=')) {
    const decoded = decodeURIComponent(window.location.hash.slice(8))
    history.replaceState(null, '', '/bind')
    openModal('QQ音乐')
    cookieValue.value = decoded
  }
})

const phone = ref('')
const phoneCode = ref('')
const phoneCountdown = ref(0)
const phoneLoading = ref(false)
const phoneError = ref('')
const phoneTicket = ref('')
let phoneTimer: ReturnType<typeof setInterval> | null = null

async function sendPhoneCode() {
  if (phoneCountdown.value > 0 || !phone.value.trim()) return
  const pid = getPlatformId(activePlatform.value)
  if (!pid) return
  phoneError.value = ''
  try {
    const res = await platformApi.sendPhoneCode(pid, phone.value.trim())
    phoneTicket.value = res.ticket
  } catch (e: any) {
    phoneError.value = e?.response?.data?.message || e?.message || '短信发送失败，请重试'
    return
  }
  phoneCountdown.value = 60
  phoneTimer = setInterval(() => {
    phoneCountdown.value--
    if (phoneCountdown.value <= 0 && phoneTimer) {
      clearInterval(phoneTimer)
      phoneTimer = null
    }
  }, 1000)
}

async function submitPhone() {
  phoneError.value = ''
  if (!phone.value.trim()) { phoneError.value = '请输入手机号'; return }
  if (!phoneCode.value.trim()) { phoneError.value = '请输入验证码'; return }
  if (!phoneTicket.value) { phoneError.value = '请先获取验证码'; return }
  const pid = getPlatformId(activePlatform.value)
  if (!pid) return
  phoneLoading.value = true
  try {
    await platformApi.bindPhone(pid, { phone: phone.value.trim(), code: phoneCode.value.trim(), ticket: phoneTicket.value })
    phoneTicket.value = ''
    loadBindings()
  } catch (e: any) {
    phoneError.value = e?.response?.data?.message || e?.message || '绑定失败，请重试'
  } finally {
    phoneLoading.value = false
  }
}

const cookieValue = ref('')
const cookieLoading = ref(false)
const cookieError = ref('')

async function submitCookie() {
  cookieError.value = ''
  if (!cookieValue.value.trim()) { cookieError.value = '请粘贴 Cookie 内容'; return }
  const pid = getPlatformId(activePlatform.value)
  if (!pid) return
  cookieLoading.value = true
  try {
    await platformApi.bindCookie(pid, cookieValue.value.trim())
    loadBindings()
  } catch (e: any) {
    cookieError.value = e?.response?.data?.message || e?.message || '验证失败，请检查 Cookie 是否有效'
  } finally {
    cookieLoading.value = false
  }
}
</script>

<style scoped>
.sheet-enter-active, .sheet-leave-active { transition: opacity .2s; }
.sheet-enter-from, .sheet-leave-to { opacity: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
