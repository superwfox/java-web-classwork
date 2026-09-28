<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MessageGlobe from './components/MessageGlobe.vue'
import { ApiError, countryName, listMessages, writeMessage } from './message/api'
import type { Location, Message } from './message/api'

const messages = ref<Message[]>([])
const selected = ref<Location | null>(null)
const active = ref<Message | null>(null)
const draft = ref('')
const editor = ref<HTMLTextAreaElement>()
const loading = ref(true)
const busy = ref(false)
const loadError = ref('')
const saveError = ref('')
const conflict = ref(false)
const deletePrompt = ref(false)
const savedText = ref('')
const status = ref('')
let requestId = ''
let timer: ReturnType<typeof setTimeout> | undefined
let saving: Promise<boolean> | null = null
let disposed = false
const dirty = computed(() => selected.value !== null && draft.value !== savedText.value)
const valid = computed(() => draft.value.trim().length > 0 && draft.value.length <= 1000)
const coordinates = computed(() => selected.value
  ? `${selected.value.latitude.toFixed(4)}°, ${selected.value.longitude.toFixed(4)}°` : '')

async function load() {
  loading.value = true; loadError.value = ''
  try { messages.value = await listMessages() }
  catch { loadError.value = '留言暂时无法载入，请确认 Java 服务已启动后重试。' }
  finally { loading.value = false }
}
function open(location: Location, message: Message | null = null) {
  if (selected.value) return
  active.value = message
  draft.value = message?.text || ''
  savedText.value = draft.value
  requestId = crypto.randomUUID()
  saveError.value = ''; conflict.value = false; deletePrompt.value = false; status.value = ''
  selected.value = { latitude: location.latitude, longitude: location.longitude }
  nextTick(() => editor.value?.focus({ preventScroll: true }))
}
watch(draft, () => {
  clearTimeout(timer)
  status.value = ''
  if (active.value && !conflict.value && dirty.value && valid.value && !deletePrompt.value) {
    timer = setTimeout(() => { void save() }, 900)
  }
})
async function save(): Promise<boolean> {
  clearTimeout(timer)
  if (saving) return saving
  if (!selected.value || conflict.value) return false
  if (!valid.value) { saveError.value = '请输入 1–1000 字的留言'; return false }
  if (active.value && !dirty.value) return true
  const text = draft.value
  const location = { ...selected.value }
  const message = active.value
  busy.value = true; saveError.value = ''; status.value = '正在保存…'
  saving = (async () => {
    try {
      const result = await writeMessage(message
        ? { action: 'update', id: message.id, version: message.version, text }
        : { action: 'create', requestId, text, ...location })
      if (disposed) return false
      const index = messages.value.findIndex(item => item.id === result.id)
      if (index === -1) messages.value.push(result)
      else messages.value[index] = result
      active.value = result
      savedText.value = result.text
      if (draft.value === text && result.text === text.trim()) draft.value = result.text
      status.value = '已保存'
      return true
    } catch (error) {
      conflict.value = error instanceof ApiError && error.status === 409
      saveError.value = error instanceof ApiError ? error.message : '保存未确认，请重试；输入已保留。'
      status.value = ''
      return false
    } finally { busy.value = false; saving = null }
  })()
  const success = await saving
  if (success && dirty.value && !disposed) timer = setTimeout(() => { void save() }, 900)
  return success
}
function reset() {
  clearTimeout(timer)
  selected.value = null; active.value = null; draft.value = ''; savedText.value = ''; deletePrompt.value = false
}
async function close() {
  if (busy.value) return
  if (dirty.value && !(await save())) return
  // The user can continue typing while a save is in flight; never close over a newer draft.
  if (dirty.value) return
  reset()
}
function discard() { if (!busy.value) reset() }
function askDelete() { clearTimeout(timer); deletePrompt.value = true }
function cancelDelete() {
  deletePrompt.value = false
  if (dirty.value && valid.value && !conflict.value) timer = setTimeout(() => { void save() }, 900)
}
async function remove() {
  if (!active.value || busy.value) return
  busy.value = true; saveError.value = ''
  try {
    await writeMessage<void>({ action: 'delete', id: active.value.id, version: active.value.version })
    messages.value = messages.value.filter(item => item.id !== active.value?.id)
    reset()
  } catch (error) {
    conflict.value = error instanceof ApiError && error.status === 409
    saveError.value = error instanceof ApiError ? error.message : '删除未确认，请重新载入留言确认结果。'
  } finally { busy.value = false }
}
async function reloadActive() {
  const id = active.value?.id
  await load()
  if (loadError.value) { saveError.value = loadError.value; return }
  const latest = messages.value.find(item => item.id === id)
  if (!latest) { reset(); return }
  active.value = latest; draft.value = latest.text; savedText.value = latest.text
  conflict.value = false; saveError.value = ''; deletePrompt.value = false; status.value = '已载入最新内容'
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || busy.value) { event.preventDefault(); event.returnValue = '' }
}
onMounted(() => { void load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => { disposed = true; clearTimeout(timer); window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <main class="landing">
    <header class="masthead">
      <a class="wordmark" href="./" aria-label="Java Web home" @click.prevent="close"><span class="brand-dot"></span>JAVA / WEB</a>
      <span class="edition">A WORLD OF POSSIBILITIES</span>
    </header>
    <section class="hero" aria-labelledby="headline">
      <div class="globe-stage">
        <div class="orbit orbit-one" aria-hidden="true"></div><div class="orbit orbit-two" aria-hidden="true"></div>
        <span class="coordinate coordinate-left">{{ loading ? 'CONNECTING…' : `${messages.length} MESSAGES` }}<br>ONE SHARED WORLD</span>
        <span class="coordinate coordinate-right">CLICK TO LEAVE A NOTE<br>HOVER TO SLOW DOWN</span>
        <MessageGlobe :messages="messages" :selected="selected" :locked="selected !== null"
          @pick="location => open(location)" @open="message => open(message, message)" />
        <section v-if="selected" class="message-editor" role="dialog" aria-labelledby="editor-title" :aria-busy="busy">
          <div class="editor-heading"><span class="eyebrow">A NOTE ON EARTH</span><button class="icon-button" type="button" aria-label="保存并关闭" title="保存并关闭" :disabled="busy" @click="close">×</button></div>
          <h2 id="editor-title">{{ active ? '这里，有一段对话。' : '在这里，留下你的声音。' }}</h2>
          <p class="editor-coordinates">{{ coordinates }}</p>
          <template v-if="!deletePrompt">
            <label class="visually-hidden" for="message-text">留言内容</label>
            <textarea id="message-text" ref="editor" v-model="draft" maxlength="1000" rows="5" placeholder="想和这个世界说些什么？" :readonly="conflict" />
            <div class="editor-meta"><span>{{ active ? countryName(active.country) : '发布时记录 IP 所属国家／地区' }}</span><span>{{ draft.length }} / 1000</span></div>
            <p class="editor-note">公开留言，任何人均可编辑或删除。{{ active ? '停止输入后自动保存。' : '点击勾选按钮发布。' }}</p>
          </template>
          <p v-else class="delete-question">确定删除这条留言？删除后无法恢复。</p>
          <p v-if="saveError" class="error-message" role="alert">{{ saveError }}</p>
          <div class="editor-actions">
            <template v-if="conflict"><button class="text-button" type="button" @click="discard">关闭并放弃草稿</button><button class="action-button" type="button" :disabled="loading" @click="reloadActive">载入最新内容</button></template>
            <template v-else-if="deletePrompt"><button class="text-button" type="button" :disabled="busy" @click="cancelDelete">保留留言</button><button class="action-button danger" type="button" :disabled="busy" @click="remove">确认删除</button></template>
            <template v-else>
              <button v-if="active" class="text-button" type="button" :disabled="busy" @click="askDelete">删除留言</button>
              <button v-else class="text-button" type="button" :disabled="busy" @click="discard">取消</button>
              <span class="save-status" aria-live="polite">{{ status }}</span>
              <button class="action-button save-button" type="button" :disabled="busy || !valid" :aria-label="active ? '保存留言' : '发布留言'" :title="active ? '保存留言' : '发布留言'" @click="save">
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="3" stroke="currentColor" stroke-width="1.5"/><path d="m7 12 3.2 3.2L17 8.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
              </button>
            </template>
          </div>
        </section>
      </div>
      <div class="caption"><p class="eyebrow"><span class="status-dot"></span> SMALL CODE. BIG CONNECTIONS.</p><h1 id="headline">Link the world<br>with <span>java web</span><span class="cursor" aria-hidden="true">_</span></h1></div>
      <div v-if="loadError" class="load-error" role="status">{{ loadError }} <button class="text-button" type="button" :disabled="loading" @click="load">重新载入</button></div>
    </section>
    <footer class="footer"><span>BUILT TO CONNECT.</span><a href="https://db-ip.com" target="_blank" rel="noopener noreferrer">IP Geolocation by DB-IP</a><span>JAVA WEB / 001</span></footer>
  </main>
</template>
