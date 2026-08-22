import { createI18n } from 'vue-i18n'
import en from './en.js'
import es from './es.js'

const saved = localStorage.getItem('aircargo_lang') || 'es'

const i18n = createI18n({
  legacy: false,
  locale: saved,
  fallbackLocale: 'en',
  messages: { en, es },
})

export default i18n
