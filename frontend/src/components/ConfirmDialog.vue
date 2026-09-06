<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center p-4 ds-confirm-backdrop" @click.self="onCancel">
      <div class="ds-modal-panel max-w-md py-5 animate-pop" @keydown.esc="onCancel">
        <div v-if="title" class="mb-4 pb-3 border-b border-slate-200">
          <h3 class="ds-modal-title">{{ title }}</h3>
        </div>
        <div class="mb-5">
          <p class="text-sm font-mono text-slate-900 whitespace-pre-wrap leading-relaxed">{{ message }}</p>
        </div>
        <div class="flex justify-end gap-2">
          <button v-if="cancelText" @click="onCancel" class="ds-btn-secondary text-sm">
            {{ cancelText }}
          </button>
          <button @click="onConfirm"
            class="ds-btn-primary text-sm"
            :class="danger ? 'ds-confirm-danger' : ''">
            {{ confirmText || 'Aceptar' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useConfirm } from '../composables/useConfirm'

const { visible, title, message, confirmText, cancelText, danger, onConfirm, onCancel } = useConfirm()

function onKeydown(e) {
  if (!visible.value) return
  if (e.key === 'Escape') onCancel()
  if (e.key === 'Enter') onConfirm()
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>
