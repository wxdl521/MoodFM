<template>
  <div style="min-height: 100vh; background: var(--bg); padding-bottom: 100px;">

    <div class="page-pad" style="max-width: 640px;">
      <div class="row" style="align-items: center; gap: 12px; margin-bottom: 8px;">
        <button class="btn-pill" @click="$router.back()">‹ 返回</button>
        <div class="meta">SECTION · PROFILE · 个人信息</div>
      </div>
      <h1 class="display" style="font-size: clamp(48px, 6vw, 96px); margin: 8px 0 0; line-height: 0.95;">Profile.</h1>
      <div class="display-cn" style="font-size: clamp(18px, 2vw, 26px); color: var(--ink-2); margin-bottom: 36px;">
        你的信息 · 个人设置
      </div>

      <div v-if="loading" class="meta" style="color: var(--ink-3); padding: 24px 0;">加载中…</div>

      <div v-else>
        <div class="section-card" style="margin-bottom: 32px;">
          <div class="section-header">
            <span class="meta">AVATAR · 头像</span>
          </div>
          <div class="row" style="gap: 20px; align-items: center; padding: 20px;">
            <div style="position: relative; display: inline-block;">
              <div
                v-if="!avatarPreview"
                style="width: 80px; height: 80px; border-radius: 50%; overflow: hidden; border: 2px solid var(--rule); flex-shrink: 0; cursor: pointer;"
                @click="triggerAvatarInput"
              >
                <MoodBlob :size="80" geometry="circle" :drift="false" />
              </div>
              <img
                v-else
                :src="avatarPreview"
                alt="avatar"
                style="width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 2px solid var(--rule); display: block; cursor: pointer;"
                @click="triggerAvatarInput"
              />
              <button
                class="avatar-upload-btn"
                aria-label="上传头像"
                @click="triggerAvatarInput"
              >
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                  <polyline points="17 8 12 3 7 8" />
                  <line x1="12" y1="3" x2="12" y2="15" />
                </svg>
              </button>
              <input ref="avatarInput" type="file" accept="image/*" style="display: none;" @change="onAvatarChange" />
            </div>
            <div>
              <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '18px', fontWeight: 500 }">{{ form.username || '未设置' }}</div>
              <div class="meta" style="margin-top: 4px; color: var(--ink-3);">{{ form.email }}</div>
            </div>
          </div>
        </div>

        <div class="section-card" style="margin-bottom: 32px;">
          <div class="section-header">
            <span class="meta">PROFILE INFO · 基本信息</span>
          </div>
          <div style="padding: 20px; display: flex; flex-direction: column; gap: 16px;">
            <div class="field">
              <label class="field-label">用户名</label>
              <input
                v-model="form.username"
                type="text"
                class="field-input"
                placeholder="你的用户名"
                maxlength="32"
              />
            </div>
            <div class="field">
              <label class="field-label">邮箱 · Email</label>
              <input
                :value="form.email"
                type="email"
                class="field-input field-input--readonly"
                readonly
                tabindex="-1"
              />
            </div>
            <div class="field">
              <label class="field-label">个人简介</label>
              <textarea
                v-model="form.bio"
                class="field-input"
                style="height: 80px; resize: vertical;"
                placeholder="写点什么…"
                maxlength="200"
              />
            </div>
          </div>
          <div style="padding: 0 20px 20px;">
            <button class="btn" :disabled="savingProfile" @click="saveProfile">
              {{ savingProfile ? '保存中…' : '保存信息' }}
            </button>
            <span v-if="profileSaved" class="meta" style="margin-left: 12px; color: var(--mood-a);">已保存 ✓</span>
          </div>
        </div>

        <div class="section-card" style="margin-bottom: 32px;">
          <div class="section-header">
            <span class="meta">SECURITY · 账号安全</span>
          </div>
          <div style="padding: 20px; display: flex; flex-direction: column; gap: 16px;">
            <div class="field">
              <label class="field-label">旧密码</label>
              <input v-model="pwForm.old" type="password" class="field-input" placeholder="当前密码" autocomplete="current-password" />
            </div>
            <div class="field">
              <label class="field-label">新密码</label>
              <input v-model="pwForm.new1" type="password" class="field-input" placeholder="至少 8 位" autocomplete="new-password" />
            </div>
            <div class="field">
              <label class="field-label">确认新密码</label>
              <input v-model="pwForm.new2" type="password" class="field-input" placeholder="再次输入新密码" autocomplete="new-password" />
            </div>
            <div v-if="pwError" class="meta" style="color: var(--mood-b);">{{ pwError }}</div>
          </div>
          <div style="padding: 0 20px 20px;">
            <button class="btn" :disabled="savingPw" @click="changePassword">
              {{ savingPw ? '修改中…' : '修改密码' }}
            </button>
            <span v-if="pwSaved" class="meta" style="margin-left: 12px; color: var(--mood-a);">密码已修改 ✓</span>
          </div>
        </div>

        <div class="section-card danger-zone">
          <div class="section-header">
            <span class="meta" style="color: var(--mood-b);">DANGER ZONE · 危险区</span>
          </div>
          <div style="padding: 20px;">
            <div :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', color: 'var(--ink-2)', marginBottom: '16px', lineHeight: 1.6 }">
              注销账号将永久删除你的所有数据，包括播放历史、心情地图、歌单等，此操作不可撤销。
            </div>
            <button
              class="btn"
              style="background: var(--mood-b); border-color: var(--mood-b); color: #fff;"
              @click="showDeleteConfirm = true"
            >
              注销账号
            </button>
          </div>
        </div>
      </div>
    </div>

    <div
      v-if="showDeleteConfirm"
      style="position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; padding: 24px;"
      @click.self="showDeleteConfirm = false"
    >
      <div style="background: var(--paper); border: 1px solid var(--rule); border-radius: 20px; padding: 32px; max-width: 380px; width: 100%;">
        <div class="meta" style="color: var(--mood-b);">危险操作 · DANGER ZONE</div>
        <h3 :style="{ fontFamily: 'var(--serif-cn)', fontSize: '20px', margin: '12px 0 8px' }">确认注销账号？</h3>
        <p :style="{ fontFamily: 'var(--serif-cn)', fontSize: '14px', color: 'var(--ink-2)', lineHeight: 1.6, margin: '0 0 24px' }">
          此操作无法撤销，你的全部数据将被永久删除。
        </p>
        <div class="row" style="gap: 10px; justify-content: flex-end;">
          <button class="btn-pill" @click="showDeleteConfirm = false">取消</button>
          <button class="btn" style="background: var(--mood-b); border-color: var(--mood-b); color: #fff;" @click="deleteAccount">
            确认注销
          </button>
        </div>
      </div>
    </div>

    <MiniPlayer />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import MiniPlayer from '@/components/common/MiniPlayer.vue'
import MoodBlob from '@/components/common/MoodBlob.vue'
import { useUserStore } from '@/stores/user'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/user'

const userStore = useUserStore()
const authStore = useAuthStore()

const loading = ref(true)
const savingProfile = ref(false)
const profileSaved = ref(false)
const savingPw = ref(false)
const pwSaved = ref(false)
const pwError = ref<string | null>(null)
const showDeleteConfirm = ref(false)
const avatarPreview = ref<string | null>(null)
const avatarInput = ref<HTMLInputElement | null>(null)

const form = ref({ username: '', email: '', bio: '' })
const pwForm = ref({ old: '', new1: '', new2: '' })

onMounted(async () => {
  try {
    const user = await userStore.fetchProfile()
    form.value = { username: user?.username ?? '', email: user?.email ?? '', bio: '' }
    if (user?.avatarUrl) avatarPreview.value = user.avatarUrl
  } catch {
  } finally {
    loading.value = false
  }
})

function triggerAvatarInput() {
  avatarInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  avatarPreview.value = URL.createObjectURL(file)
  try {
    await userApi.uploadAvatar(file)
  } catch {
  }
}

async function saveProfile() {
  savingProfile.value = true
  profileSaved.value = false
  try {
    await userStore.updateProfile({ username: form.value.username })
    profileSaved.value = true
    setTimeout(() => { profileSaved.value = false }, 2500)
  } catch {
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  pwError.value = null
  if (!pwForm.value.old || !pwForm.value.new1 || !pwForm.value.new2) {
    pwError.value = '请填写所有密码字段'
    return
  }
  if (pwForm.value.new1 !== pwForm.value.new2) {
    pwError.value = '两次新密码不一致'
    return
  }
  if (pwForm.value.new1.length < 8) {
    pwError.value = '新密码至少 8 位'
    return
  }
  savingPw.value = true
  try {
    await userApi.changePassword({ oldPassword: pwForm.value.old, newPassword: pwForm.value.new1 })
    pwSaved.value = true
    pwForm.value = { old: '', new1: '', new2: '' }
    setTimeout(() => { pwSaved.value = false }, 2500)
  } catch (err: any) {
    pwError.value = err?.response?.data?.message ?? '修改失败，请检查旧密码'
  } finally {
    savingPw.value = false
  }
}

async function deleteAccount() {
  showDeleteConfirm.value = false
  try {
    await userApi.deleteAccount()
    authStore.logout()
  } catch {
  }
}
</script>

<style scoped>
.page-pad {
  padding: 32px 56px 0;
}

@media (max-width: 768px) {
  .page-pad {
    padding: 24px 18px 0;
  }
}

.section-card {
  border: 1px solid var(--rule);
  border-radius: 16px;
  background: var(--paper);
  overflow: hidden;
}

.section-header {
  padding: 12px 20px;
  border-bottom: 1px solid var(--rule);
  background: var(--bg-2);
}

.danger-zone {
  border-color: color-mix(in srgb, var(--mood-b) 30%, transparent);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-family: var(--mono);
  font-size: 10px;
  letter-spacing: 0.15em;
  color: var(--ink-3);
  text-transform: uppercase;
}

.field-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--rule);
  border-radius: 8px;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--serif-cn);
  font-size: 15px;
  outline: none;
  transition: border-color 0.15s;
}

.field-input:focus {
  border-color: var(--ink-3);
}

.field-input--readonly {
  color: var(--ink-3);
  cursor: default;
}

.avatar-upload-btn {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--ink);
  color: var(--bg);
  border: 2px solid var(--bg);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.15s;
}

.avatar-upload-btn:hover {
  opacity: 0.8;
}
</style>
