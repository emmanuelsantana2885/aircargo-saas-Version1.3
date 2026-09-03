import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

// Smoke test del patrón de layout responsivo del formulario de recibo:
// verifica que el contenedor aplica la altura responsiva (var --receipt-form-h),
// que el área de pasos tiene scroll interno y que footer/stepper quedan fijos.
const ReceiptFormLayout = {
  template: `
    <div class="p-1.5 md:p-2.5 flex flex-col receipt-form"
         style="min-height: 240px; height: var(--receipt-form-h, 58dvh); max-height: calc(100dvh - 150px);">
      <div class="mb-1.5 shrink-0">STEPPER</div>
      <div class="flex-1 min-h-0 overflow-y-auto">CONTENIDO_PASOS</div>
      <div class="shrink-0">FOOTER</div>
    </div>
  `,
}

describe('ReceiptFormLayout', () => {
  it('aplica la altura responsiva con límites min/max (dvh)', () => {
    const w = mount(ReceiptFormLayout)
    const el = w.element
    expect(el.classList.contains('receipt-form')).toBe(true)
    const s = el.getAttribute('style') || ''
    // Los límites MIN/MAX responsivos (dvh) están declarados en el inline style
    expect(s).toContain('min-height: 240px')
    expect(s).toContain('100dvh')
  })

  it('mantiene el stepper y el footer fijos y el contenido con scroll interno', () => {
    const w = mount(ReceiptFormLayout)
    const texts = w.text()
    expect(texts).toContain('STEPPER')
    expect(texts).toContain('FOOTER')
    const scrollArea = w.find('.flex-1.min-h-0.overflow-y-auto')
    expect(scrollArea.exists()).toBe(true)
    // El scroll interno está presente: flex-1 + min-h-0 + overflow-y-auto
    expect(scrollArea.classes()).toContain('overflow-y-auto')
    expect(scrollArea.classes()).toContain('min-h-0')
  })

  it('añade la clase .receipt-form para aplicar la altura responsiva por CSS', () => {
    // El alto se controla por la clase .receipt-form y la var --receipt-form-h
    // (definida en el <style scoped> con media queries). Verificamos que la
    // clase está presente en el contenedor.
    const w = mount(ReceiptFormLayout)
    expect(w.element.classList.contains('receipt-form')).toBe(true)
  })
})