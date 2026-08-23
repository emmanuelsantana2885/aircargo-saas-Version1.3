import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ErrorBoundary from '@/components/ErrorBoundary.vue'

import { createRouter, createMemoryHistory } from 'vue-router'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }],
})

const i18n = createI18n({
  legacy: false,
  locale: 'es',
  messages: {
    es: {
      common: { back: 'Volver' },
      errorBoundary: {
        title: 'Algo salió mal',
        message: 'Ocurrió un error inesperado en esta vista.',
        retry: 'Reintentar',
        home: 'Inicio',
      },
    },
  },
})

function globalStubs() {
  return {
    plugins: [i18n, router],
    stubs: { 'router-link': { template: '<a><slot /></a>' }, 'router-view': true },
  }
}

describe('ErrorBoundary', () => {
  it('renderiza el contenido cuando el hijo está sano', () => {
    const wrapper = mount(ErrorBoundary, {
      global: globalStubs(),
      slots: { default: '<p class="ok">contenido</p>' },
    })
    expect(wrapper.find('.ok').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('Algo salió mal')
  })

  it('muestra fallback con botones cuando el hijo lanza en render', async () => {
    const BadChild = {
      render() {
        throw new TypeError('filteredX is undefined') // el tipo de bug real que tuvimos
      },
    }
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount(ErrorBoundary, {
      global: globalStubs(),
      slots: { default: BadChild },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Algo salió mal')
    expect(wrapper.text()).toContain('Reintentar')
    spy.mockRestore()
  })

  it('recover() restaura el contenido tras un error', async () => {
    let shouldThrow = true
    const Conditional = {
      render() {
        if (shouldThrow) throw new Error('boom')
        return null
      },
    }
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const wrapper = mount(ErrorBoundary, {
      global: globalStubs(),
      slots: { default: Conditional },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Reintentar')
    // "Reintentar" limpia hasError; si el hijo ya no falla, vuelve a renderizar
    shouldThrow = false
    await wrapper.find('button.ds-btn-primary').trigger('click')
    await wrapper.vm.$nextTick()
    spy.mockRestore()
  })
})
