<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm" @click.self="onCancel">
      <div class="bg-white rounded-xl shadow-2xl mx-4 w-full max-w-md overflow-hidden" @keydown.esc="onCancel">
        <div v-if="title" class="px-5 py-3 border-b border-slate-200">
          <h3 class="text-sm font-mono font-black uppercase tracking-wider text-slate-950">{{ title }}</h3>
        </div>
        <div class="px-5 py-4">
          <p class="text-sm font-mono text-slate-900 whitespace-pre-wrap">{{ message }}</p>
        </div>
        <div class="flex justify-end gap-2 px-5 py-3 border-t border-slate-200 bg-slate-50">
          <button v-if="cancelText" @click="onCancel"
            class="text-sm px-4 py-2 rounded-lg border border-slate-300 font-mono font-bold text-slate-950 hover:bg-white transition">
            {{ cancelText }}
          </button>
          <button @click="onConfirm"
            class="text-sm px-4 py-2 rounded-lg font-mono font-bold text-white transition"
            :class="danger ? 'bg-slate-600 hover:bg-slate-500' : 'bg-slate-950 hover:bg-slate-800'">
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
