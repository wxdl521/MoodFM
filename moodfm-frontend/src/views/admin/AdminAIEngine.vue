<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import '@/assets/styles/admin.css'
import { aiConfigAdminApi, sceneAdminApi, SceneTemplate } from '@/api/admin'
import type { AdminToastType } from '@/types'
import { logger } from '@/utils/logger'

interface WeightItem { key: string; label: string; desc: string; val: number; min: number; max: number }

const tab = ref<'weights' | 'scenes' | 'prompt'>('weights')
const saved = ref(false)

const metrics = [
  { label: '平均推荐准确率', val: '87.3%', trend: '↓ -0.4%', cls: 's1' },
  { label: '平均完整播放率', val: '68.2%', trend: '↑ +1.2%', cls: 's2' },
  { label: '日均 Heart 率',  val: '12.4%', trend: '↑ +0.8%', cls: 's3' },
  { label: '日均 Skip 率',   val: '24.1%', trend: '↓ -0.3%', cls: 's4' },
]

const weights = ref<WeightItem[]>([
  { key: 'positivity', label: '情绪正向偏置',   desc: '推荐歌曲的情绪正负向倾向，值越高越正向',    val: 65, min: 0, max: 100 },
  { key: 'energy',     label: '能量权重',        desc: '高能量歌曲的优先程度，影响动感 vs 舒缓比例', val: 48, min: 0, max: 100 },
  { key: 'diversity',  label: '流派多样性',      desc: '推荐时跨流派的发散程度',                    val: 72, min: 0, max: 100 },
  { key: 'novelty',    label: '新鲜度偏好',      desc: '陌生曲目 vs 熟悉曲目的比例',                val: 40, min: 0, max: 100 },
  { key: 'feedback',   label: '反馈学习速率',    desc: '用户 Skip/Heart 反馈影响后续推荐的速度',     val: 58, min: 0, max: 100 },
  { key: 'contextual', label: '上下文权重',      desc: '时间、天气、历史等上下文的影响力',           val: 82, min: 0, max: 100 },
])

const DEFAULTS: Record<string, number> = { positivity: 65, energy: 48, diversity: 72, novelty: 40, feedback: 58, contextual: 82 }

const scenes = ref<SceneTemplate[]>([])
const loadingScenes = ref(false)
const scenesLoaded = ref(false)

const editingScene = ref<SceneTemplate | null>(null)
const showSceneModal = ref(false)

const showNewModal = ref(false)
const newSceneForm = ref({ key: '', name: '', cn: '', active: true, songs: 0, accuracy: '—' })

const promptTemplate = ref(
  '你是 MoodFM 的 AI 推荐引擎。根据用户当前的心情描述和上下文，推荐最合适的歌曲列表。\n\n' +
  '用户输入：{{user_input}}\n当前时间：{{current_time}}\n用户历史偏好：{{user_profile}}\n当前场景：{{scene}}\n\n' +
  '请输出 JSON 格式的推荐列表，包含歌曲名、艺人、推荐理由。推荐数量：{{count}} 首。\n' +
  '推荐时考虑用户黑名单：{{blacklist}}。'
)

function toast(msg: string, type: AdminToastType = 'ok') { window.__adminToast?.(msg, type) }

async function loadScenes() {
  loadingScenes.value = true
  try {
    scenes.value = await sceneAdminApi.list()
  } catch (err) {
    logger.warn('admin:ai-engine-load-scenes', err)
    toast('场景加载失败', 'warn')
  } finally {
    loadingScenes.value = false
  }
}

onMounted(async () => {
  try {
    const [weightResult, promptResult] = await Promise.all([
      aiConfigAdminApi.getWeights(),
      aiConfigAdminApi.getPrompt(),
    ])
    weights.value.forEach(w => { w.val = Number(weightResult[w.key]) || DEFAULTS[w.key] })
    promptTemplate.value = promptResult.prompt
  } catch (err) {
    // silent: 配置加载失败时回退默认值
    logger.warn('admin:ai-engine-load-config', err)
  }
})

watch(tab, (val) => {
  if (val === 'scenes' && !scenesLoaded.value) {
    loadScenes().then(() => { scenesLoaded.value = true })
  }
})

async function saveWeights() {
  const record: Record<string, string> = {}
  weights.value.forEach(w => { record[w.key] = String(w.val) })
  try {
    await aiConfigAdminApi.saveWeights(record)
    toast('权重配置已保存')
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } catch (err) {
    logger.warn('admin:ai-engine-save-weights', err)
    toast('保存失败，请重试', 'warn')
  }
}

async function resetWeights() {
  weights.value.forEach(w => { w.val = DEFAULTS[w.key] ?? 50 })
  const defaultRecord: Record<string, string> = {}
  Object.entries(DEFAULTS).forEach(([k, v]) => { defaultRecord[k] = String(v) })
  try {
    await aiConfigAdminApi.saveWeights(defaultRecord)
    toast('已重置为默认值', 'info')
  } catch (err) {
    logger.warn('admin:ai-engine-reset-weights', err)
    toast('重置失败，请重试', 'warn')
  }
}

async function toggleScene(scene: SceneTemplate) {
  const next = !scene.active
  try {
    await sceneAdminApi.update(scene.id, { active: next })
    scene.active = next
    toast(`${next ? '已启用' : '已禁用'} · ${scene.name}`)
  } catch (err) {
    logger.warn('admin:ai-engine-toggle-scene', err)
    toast('操作失败，请重试', 'warn')
  }
}

function openScene(scene: SceneTemplate) {
  editingScene.value = { ...scene }
  showSceneModal.value = true
}

async function saveScene() {
  if (!editingScene.value) return
  try {
    await sceneAdminApi.update(editingScene.value.id, {
      name: editingScene.value.name,
      key: editingScene.value.key,
      cn: editingScene.value.cn,
    })
    const idx = scenes.value.findIndex(s => s.id === editingScene.value!.id)
    if (idx > -1) scenes.value[idx] = { ...editingScene.value }
    showSceneModal.value = false
    toast('场景已更新')
  } catch (err) {
    logger.warn('admin:ai-engine-save-scene', err)
    toast('保存失败，请重试', 'warn')
  }
}

function openNewScene() {
  newSceneForm.value = { key: '', name: '', cn: '', active: true, songs: 0, accuracy: '—' }
  showNewModal.value = true
}

async function submitNewScene() {
  if (!newSceneForm.value.key || !newSceneForm.value.name) {
    toast('请填写 Key 和名称', 'warn')
    return
  }
  try {
    const created = await sceneAdminApi.create(newSceneForm.value)
    scenes.value.push(created)
    showNewModal.value = false
    toast('场景已创建')
  } catch (err) {
    logger.warn('admin:ai-engine-create-scene', err)
    toast('创建失败，请重试', 'warn')
  }
}

async function savePrompt() {
  try {
    await aiConfigAdminApi.savePrompt(promptTemplate.value)
    toast('Prompt 模板已保存')
  } catch (err) {
    logger.warn('admin:ai-engine-save-prompt', err)
    toast('保存失败，请重试', 'warn')
  }
}

function accuracyColor(acc: string) {
  const n = parseInt(acc)
  return n >= 88 ? 'color:var(--ok)' : n >= 80 ? 'color:var(--ink-2)' : 'color:var(--warn)'
}

const promptVars = ['user_input', 'current_time', 'user_profile', 'scene', 'count', 'blacklist']
</script>

<template>
  <div>
    <div class="topbar">
      <div class="topbar-title">
        <span class="topbar-title-main">AI Engine</span>
        <span class="topbar-title-cn">AI 推荐引擎</span>
      </div>
      <div class="topbar-actions">
        <span class="badge ok" v-if="saved"><span class="badge-dot"></span>已保存</span>
      </div>
    </div>

    <div class="content-area">
      <!-- Metrics -->
      <div class="stat-grid mb-20">
        <div v-for="m in metrics" :key="m.label" :class="['stat-card', m.cls]">
          <div class="stat-label">{{ m.label }}</div>
          <div class="stat-value" style="font-size:22px;">{{ m.val }}</div>
          <div class="stat-meta">{{ m.trend }}</div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tabs">
        <div :class="['tab-item', tab === 'weights' ? 'active' : '']" @click="tab = 'weights'">推荐权重</div>
        <div :class="['tab-item', tab === 'scenes'  ? 'active' : '']" @click="tab = 'scenes'">场景模板</div>
        <div :class="['tab-item', tab === 'prompt'  ? 'active' : '']" @click="tab = 'prompt'">Prompt 配置</div>
      </div>

      <!-- Weights -->
      <div v-if="tab === 'weights'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">情绪推荐权重</span>
            <div class="row gap-8">
              <button class="btn btn-ghost btn-sm" @click="resetWeights">重置默认</button>
              <button class="btn btn-primary btn-sm" @click="saveWeights">保存配置</button>
            </div>
          </div>
          <div class="card-body">
            <div class="alert info" style="margin-bottom:18px;">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
              权重调整将在下一个推荐周期（约 5 分钟后）生效。
            </div>
            <div v-for="w in weights" :key="w.key" style="padding:16px 0;border-bottom:1px solid var(--rule);">
              <div class="between mb-8">
                <div>
                  <div class="mono fs-12 t-ink">{{ w.label }}</div>
                  <div class="mono fs-10 t-ink3" style="margin-top:3px;">{{ w.desc }}</div>
                </div>
                <div class="mono fs-13 t-ink2" style="min-width:36px;text-align:right;">{{ w.val }}</div>
              </div>
              <div class="range-row">
                <span class="mono fs-10 t-ink3">{{ w.min }}</span>
                <input class="range-input" type="range" :min="w.min" :max="w.max" v-model.number="w.val" />
                <span class="mono fs-10 t-ink3">{{ w.max }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Scenes -->
      <div v-if="tab === 'scenes'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">场景模板管理</span>
            <button class="btn btn-primary btn-sm" @click="openNewScene">+ 新建场景</button>
          </div>
          <div v-if="loadingScenes" class="mono fs-12 t-ink3" style="padding:24px;text-align:center;">加载中…</div>
          <table v-else class="tbl">
            <thead><tr><th>场景</th><th>描述</th><th>曲库量</th><th>准确率</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="s in scenes" :key="s.id">
                <td>
                  <div class="mono fs-12 t-ink">{{ s.name }}</div>
                  <div class="mono fs-10 t-ink3">{{ s.key }}</div>
                </td>
                <td class="mono fs-11 t-ink3">{{ s.cn }}</td>
                <td class="mono fs-12 t-ink2">{{ s.songs }}</td>
                <td class="mono fs-12" :style="accuracyColor(s.accuracy)">{{ s.accuracy }}</td>
                <td>
                  <label class="toggle">
                    <input type="checkbox" :checked="s.active" @change="toggleScene(s)" />
                    <span class="toggle-track"></span>
                  </label>
                </td>
                <td>
                  <button class="btn btn-ghost btn-sm" @click="openScene(s)">编辑</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Prompt -->
      <div v-if="tab === 'prompt'">
        <div class="card">
          <div class="card-head">
            <span class="card-title">系统 Prompt 模板</span>
            <button class="btn btn-primary btn-sm" @click="savePrompt">保存</button>
          </div>
          <div class="card-body">
            <div class="alert warn" style="margin-bottom:16px;">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
              修改 Prompt 模板将直接影响所有用户的推荐质量，请谨慎操作。
            </div>
            <div class="form-group">
              <label class="form-label">Prompt 模板 · 支持变量占位符</label>
              <textarea class="form-input" v-model="promptTemplate" style="min-height:240px;font-size:12px;line-height:1.7;"></textarea>
            </div>
            <div class="mono fs-10 t-ink3" style="line-height:1.8;">
              可用变量：
              <code
                v-for="v in promptVars"
                :key="v"
                style="background:var(--bg-2);padding:1px 6px;border-radius:4px;margin-right:6px;"
                v-text="'{' + '{' + v + '}' + '}'"
              ></code>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Scene edit modal -->
    <div class="modal-overlay" v-if="showSceneModal" @click.self="showSceneModal = false">
      <div class="modal" v-if="editingScene">
        <div class="modal-head">
          <span class="modal-title">编辑场景 · {{ editingScene.name }}</span>
          <button class="modal-close" @click="showSceneModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">场景名称</label>
              <input class="form-input" v-model="editingScene.name" />
            </div>
            <div class="form-group">
              <label class="form-label">场景 Key</label>
              <input class="form-input" v-model="editingScene.key" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">中文描述</label>
            <input class="form-input" v-model="editingScene.cn" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="showSceneModal = false">取消</button>
          <button class="btn btn-primary btn-sm" @click="saveScene">保存</button>
        </div>
      </div>
    </div>

    <!-- New scene modal -->
    <div class="modal-overlay" v-if="showNewModal" @click.self="showNewModal = false">
      <div class="modal">
        <div class="modal-head">
          <span class="modal-title">新建场景</span>
          <button class="modal-close" @click="showNewModal = false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">场景名称</label>
              <input class="form-input" v-model="newSceneForm.name" placeholder="如：睡前放松" />
            </div>
            <div class="form-group">
              <label class="form-label">场景 Key</label>
              <input class="form-input" v-model="newSceneForm.key" placeholder="如：sleep" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">中文描述</label>
            <input class="form-input" v-model="newSceneForm.cn" placeholder="如：深夜慢节奏" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost btn-sm" @click="showNewModal = false">取消</button>
          <button class="btn btn-primary btn-sm" @click="submitNewScene">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>
