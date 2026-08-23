import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import LocaleDatePicker from '@/components/LocaleDatePicker.vue'

// Mensajes mínimos reales (regresión: t() sobre arrays NO devuelve la lista)
const messages = {
  es: {
    common: {
      months: ['Enero','Febrero','Marzo','Abril','Mayo','Junio','Julio','Agosto','Septiembre','Octubre','Noviembre','Diciembre'],
      monthsShort: ['Ene','Feb','Mar','Abr','May','Jun','Jul','Ago','Sep','Oct','Nov','Dic'],
      weekdaysShort: ['Lu','Ma','Mi','Ju','Vi','Sa','Do'],
      clear: 'Limpiar',
    },
    filterBar: { periods: { today: 'Hoy' }, clear: 'Limpiar' },
  },
}

function mountPicker(modelValue = '') {
  const i18n = createI18n({ legacy: false, locale: 'es', messages })
  return mount(LocaleDatePicker, {
    props: { modelValue },
    global: { plugins: [i18n] },
  })
}

describe('LocaleDatePicker', () => {
  it('abre el calendario con los 12 meses en el dropdown (regresión arrays i18n)', async () => {
    const w = mountPicker()
    await w.find('button').trigger('click')
    const options = w.findAll('select')[0].findAll('option')
    expect(options.length).toBe(12)
    expect(options[0].text()).toBe('Enero')
    expect(options[8].text()).toBe('Septiembre')
  })

  it('muestra el mes actual seleccionado y años navegables', async () => {
    const w = mountPicker()
    await w.find('button').trigger('click')
    const now = new Date()
    const monthSelect = w.findAll('select')[0]
    const yearSelect = w.findAll('select')[1]
    expect(Number(monthSelect.element.value)).toBe(now.getMonth())
    expect(Number(yearSelect.element.value)).toBe(now.getFullYear())
    expect(yearSelect.findAll('option').length).toBeGreaterThan(10)
  })

  it('emitir ISO yyyy-mm-dd al elegir un día', async () => {
    const w = mountPicker()
    await w.find('button').trigger('click')
    // primer día del grid que pertenece al mes visible
    const dayButtons = w.findAll('.grid.grid-cols-7 button')
    expect(dayButtons.length).toBe(42)
    await dayButtons[10].trigger('click')
    const emitted = w.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('botón Hoy emite la fecha de hoy', async () => {
    const w = mountPicker()
    await w.find('button').trigger('click')
    const todayBtn = w.findAll('button').find(b => b.text() === 'Hoy')
    await todayBtn.trigger('click')
    const iso = new Date().toLocaleDateString('sv-SE') // yyyy-mm-dd
    expect(w.emitted('update:modelValue')[0][0]).toBe(iso)
  })

  it('display formatea según locale con mes abreviado', () => {
    const w = mountPicker('2026-08-15')
    expect(w.find('button span').text()).toBe('15 Ago 2026')
  })
})
