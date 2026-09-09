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
  top: max(16px, env(safe-area-inset-top));
  right: max(16px, env(safe-area-inset-right));
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 400px;
  width: 100%;
  pointer-events: none;
}
.toast-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 13px 16px;
  border-radius: 12px;
  font-size: 13px;
  font-family: var(--font-family);
  font-weight: 500;
  box-shadow: 0 12px 32px -8px rgba(15,23,42,0.25), inset 0 1px 0 rgba(255,255,255,0.6);
  cursor: pointer;
  pointer-events: auto;
  border-left: 4px solid;
}
.toast-success { background: linear-gradient(135deg, #ecfdf5, #d1fae5); color: #065f46; border-color: #10b981; }
.toast-error   { background: linear-gradient(135deg, #fef2f2, #fee2e2); color: #991b1b; border-color: #ef4444; }
.toast-warning { background: linear-gradient(135deg, #fffbeb, #fef3c7); color: #92400e; border-color: #f59e0b; }
.toast-info    { background: linear-gradient(135deg, #eff6ff, #dbeafe); color: #1e40af; border-color: #3b82f6; }
.toast-icon { font-size: 16px; font-weight: bold; flex-shrink: 0; line-height: 1.2; filter: drop-shadow(0 1px 1px rgba(0,0,0,0.15)); }
.toast-msg  { flex: 1; line-height: 1.45; }
.toast-rich { line-height: 1.5; }
.toast-rich :deep(.toast-title) {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 4px;
}
.toast-rich :deep(.toast-detail) {
  font-size: 12px;
  font-weight: 400;
  opacity: 0.75;
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
  cursor: pointer; opacity: 0.4; padding: 0 2px;
  line-height: 1; flex-shrink: 0;
}
.toast-close:hover { opacity: 0.9; }

@media (max-width: 640px) {
  .toast-container {
    top: max(12px, env(safe-area-inset-top));
    left: 12px;
    right: 12px;
    max-width: none;
  }
}

.toast-enter-active { transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1); }
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from { opacity: 0; transform: translateX(48px) scale(0.96); }
.toast-leave-to   { opacity: 0; transform: translateX(48px) scale(0.96); }
</style>