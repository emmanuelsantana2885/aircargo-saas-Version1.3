/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      fontFamily: {
        mono: ['var(--font-family)', 'JetBrains Mono', 'Courier New', 'monospace'],
        sans: ['var(--font-family)', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        xs: '12px',
      },
    },
  },
  plugins: [],
}
