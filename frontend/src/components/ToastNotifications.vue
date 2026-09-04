<template>
  <Teleport to="body">
    <div class="toast-container">
      <TransitionGroup name="toast">
        <div v-for="t in toastStore.toasts" :key="t.id"
          class="toast-item"
          :class="'toast-' + t.type"
          @click="toastStore.remove(t.id)">
          <span class="toast-icon" v-html="iconFor(t.type)"></span>
          <span v-if="t.html" class="toast-msg toast-rich" v-html="t.message"></span>
          <span v-else class="toast-msg">{{ t.message }}</span>
          <button class="toast-close" @click.stop="toastStore.remove(t.id)">&times;</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToastStore } from '../stores/toast'

const toastStore = useToastStore()

function iconFor(type) {
  const icons = {
    success: '&#10003;',
    error: '&#10007;',
    warning: '&#9888;',
    info: '&#8505;',
  }
  return icons[type] || icons.info
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 400px;
  width: 100%;
  pointer-events: none;
}
.toast-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-family: var(--font-family);
  font-weight: 500;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  cursor: pointer;
  pointer-events: auto;
  border-left: 4px solid;
}
.toast-success { background: #f5f5f5; color: #111111; border-color: #111111; }
.toast-error   { background: #f5f5f5; color: #111111; border-color: #111111; }
.toast-warning { background: #f5f5f5; color: #111111; border-color: #111111; }
.toast-info    { background: #f5f5f5; color: #111111; border-color: #111111; }
.toast-icon { font-size: 16px; font-weight: bold; flex-shrink: 0; }
.toast-msg  { flex: 1; }
.toast-rich { line-height: 1.5; }
.toast-rich :deep(.toast-title) {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 4px;
}
.toast-rich :deep(.toast-detail) {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.7;
}
.toast-rich :deep(.toast-arrow) {
  display: inline-block;
  margin: 0 4px;
  opacity: 0.5;
}
.toast-rich :deep(.toast-before) {
  text-decoration: line-through;
  opacity: 0.5;
}
.toast-rich :deep(.toast-after) {
  font-weight: 700;
}
.toast-close {
  background: none; border: none; font-size: 18px;
  cursor: pointer; opacity: 0.5; padding: 0 2px;
  line-height: 1; flex-shrink: 0;
}
.toast-close:hover { opacity: 1; }

.toast-enter-active { transition: all 0.3s ease; }
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from { opacity: 0; transform: translateX(40px); }
.toast-leave-to   { opacity: 0; transform: translateX(40px); }
</style>
