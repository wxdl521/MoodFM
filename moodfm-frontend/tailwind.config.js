/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        'serif-en': ['Instrument Serif', 'Times New Roman', 'serif'],
        'serif-cn': ['Noto Serif SC', 'Songti SC', 'STSong', 'serif'],
        'mono': ['JetBrains Mono', 'ui-monospace', 'monospace'],
      },
      colors: {
        'mood-a': 'var(--mood-a)',
        'mood-b': 'var(--mood-b)',
        'mood-c': 'var(--mood-c)',
        'mood-d': 'var(--mood-d)',
        'bg': 'var(--bg)',
        'bg-2': 'var(--bg-2)',
        'ink': 'var(--ink)',
        'ink-2': 'var(--ink-2)',
        'ink-3': 'var(--ink-3)',
        'paper': 'var(--paper)',
        'rule': 'var(--rule)',
      },
      borderRadius: {
        's': 'var(--radius-s)',
        'm': 'var(--radius-m)',
        'l': 'var(--radius-l)',
      },
    },
  },
  plugins: [],
}
