<template>
  <div class="lang-toggle" :title="locale === 'es' ? 'Switch to English' : 'Cambiar a Español'">
    <button @click="setLang('es')" class="lang-btn" :class="{ active: locale === 'es', inactive: locale !== 'es' }">
      ES
    </button>
    <button @click="setLang('en')" class="lang-btn" :class="{ active: locale === 'en', inactive: locale !== 'en' }">
      EN
    </button>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
const { locale } = useI18n()

function setLang(lang) {
  locale.value = lang
  localStorage.setItem('aircargo_lang', lang)
}
</script>

<style scoped>
.lang-toggle {
  display: flex;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid rgba(255,255,255,0.15);
  background: rgba(255,255,255,0.05);
}
.lang-btn {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.08em;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  line-height: 1.6;
}
.lang-btn.active {
  background: rgba(255,255,255,0.95);
  color: #1e293b;
  text-shadow: none;
}
.lang-btn.inactive {
  background: transparent;
  color: rgba(255,255,255,0.35);
  text-shadow: 0 0 6px rgba(255,255,255,0.15);
}
.lang-btn.inactive:hover {
  color: rgba(255,255,255,0.6);
}
</style>
