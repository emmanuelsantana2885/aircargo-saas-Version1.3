import { describe, it, expect, beforeEach } from 'vitest'
import { captureForms, restoreForms, saveDraft, loadDraft, clearDraft, setReturnTo, popReturnTo } from '@/utils/formDraft'

function buildForm() {
  document.body.innerHTML = `
    <form id="f">
      <input id="a" type="text" />
      <input id="b" type="checkbox" />
      <select id="c"><option value="">--</option><option value="MIA">MIA</option></select>
      <textarea id="d"></textarea>
    </form>`
  const q = id => document.getElementById(id)
  q('a').value = 'texto original'
  q('b').checked = true
  q('c').value = 'MIA'
  q('d').value = 'observaciones largas'
}

describe('formDraft', () => {
  beforeEach(() => {
    sessionStorage.clear()
    document.body.innerHTML = ''
  })

  it('captureForms captura valores y estados de checkbox/select/textarea', () => {
    buildForm()
    const snap = captureForms()
    expect(snap.length).toBe(4)
    expect(snap[0].v).toBe('texto original')
    expect(snap[1].v).toBe('1') // checkbox marcado
    expect(snap[2].v).toBe('MIA')
    expect(snap[3].v).toBe('observaciones largas')
  })

  it('restoreForms recupera valores y dispara input (actualiza v-model)', () => {
    buildForm()
    const snap = captureForms()
    // simular que el usuario "limpió" el formulario
    document.getElementById('a').value = ''
    document.getElementById('b').checked = false
    document.getElementById('c').value = ''
    document.getElementById('d').value = ''

    let inputFiredOnA = false
    document.getElementById('a').addEventListener('input', () => { inputFiredOnA = true })

    const n = restoreForms(snap)
    expect(n).toBe(4)
    expect(inputFiredOnA).toBe(true)
    expect(document.getElementById('a').value).toBe('texto original')
    expect(document.getElementById('b').checked).toBe(true)
    expect(document.getElementById('c').value).toBe('MIA')
    expect(document.getElementById('d').value).toBe('observaciones largas')
  })

  it('restore ignora campos con firma distinta (estructura cambió)', () => {
    buildForm()
    const snap = captureForms()
    document.body.innerHTML = '<input id="otro" type="text"/>'
    expect(restoreForms(snap)).toBe(0)
  })

  it('draft y returnTo persisten en sessionStorage', () => {
    saveDraft({ route: '/receipts?mawbId=x', forms: [1, 2] })
    setReturnTo('/receipts')
    const d = loadDraft()
    expect(d.route).toBe('/receipts?mawbId=x')
    expect(d.forms).toEqual([1, 2])
    expect(popReturnTo()).toBe('/receipts')
    expect(popReturnTo()).toBeNull()
    clearDraft()
    expect(loadDraft()).toBeNull()
  })
})
