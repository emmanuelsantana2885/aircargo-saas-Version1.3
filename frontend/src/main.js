import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import i18n from './i18n'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(i18n)

// Última red de seguridad: errores no capturados se registran sin tumbar la app
app.config.errorHandler = (err, instance, info) => {
  console.error('[Error global]', info, err)
}

app.mount('#app')
