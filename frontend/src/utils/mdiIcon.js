import { h } from 'vue'

export function mdiIcon(path) {
  return {
    name: 'MdiIcon',
    props: {
      size: { type: [Number, String], default: 24 },
      strokeWidth: { type: [Number, String], default: 2 }
    },
    render() {
      return h('svg', {
        viewBox: '0 0 24 24',
        width: this.size,
        height: this.size,
        fill: 'currentColor',
        'aria-hidden': 'true',
        xmlns: 'http://www.w3.org/2000/svg'
      }, [h('path', { d: path })])
    }
  }
}