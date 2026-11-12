/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4ff',
          100: '#dbe7ff',
          200: '#c3d5ff',
          300: '#9fbcff',
          400: '#7aa0ff',
          500: '#4f7cff',
          600: '#3b63e0',
          700: '#2f4fb8'
        },
        /** 登录页深色视觉（与 D:\Web\portfolio-login 设计稿同源） */
        auth: {
          bg: '#0a0e17',
          card: '#101523',
          line: 'rgba(255, 255, 255, 0.06)',
          field: 'rgba(255, 255, 255, 0.04)',
          'field-on': 'rgba(255, 255, 255, 0.07)',
          text: '#e8eaef',
          sub: '#8892a8',
          muted: '#5a6380',
          accent: '#4f8cff',
          'accent-soft': 'rgba(79, 140, 255, 0.18)',
          'accent-line': 'rgba(79, 140, 255, 0.35)',
          danger: '#f87171'
        }
      },
      backgroundImage: {
        // 三层径向光晕叠垂直渐变，替代大半径 blur 光球：零性能成本
        'auth-stage':
          'radial-gradient(ellipse 80% 60% at 50% 30%, rgba(79, 140, 255, 0.1) 0%, transparent 60%), radial-gradient(ellipse 60% 50% at 20% 70%, rgba(124, 92, 252, 0.08) 0%, transparent 55%), radial-gradient(ellipse 50% 40% at 80% 60%, rgba(79, 140, 255, 0.05) 0%, transparent 50%), linear-gradient(180deg, #0a0e17 0%, #0d1225 40%, #101827 70%, #0a0e17 100%)',
        'auth-submit': 'linear-gradient(135deg, #4f8cff 0%, #3d6fd9 50%, #4f8cff 100%)',
        'auth-title': 'linear-gradient(135deg, #6d8fff, #a78bfa, #6d8fff)',
        'auth-card-top':
          'linear-gradient(90deg, transparent, rgba(79, 140, 255, 0.5) 20%, rgba(124, 92, 252, 0.5) 80%, transparent)',
        'auth-divider': 'linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.08), transparent)'
      },
      maxWidth: {
        /** 内容区统一宽度：C 端页面一律用它，别再各写 max-w-3xl / max-w-4xl */
        content: '1120px',
        reading: '760px',
        form: '560px'
      },
      boxShadow: {
        card: '0 1px 2px rgba(15, 23, 42, 0.04), 0 8px 24px -12px rgba(15, 23, 42, 0.12)',
        nav: '0 1px 0 rgba(15, 23, 42, 0.04)',
        pop: '0 12px 32px -8px rgba(15, 23, 42, 0.22)'
      },
      animation: {
        'page-in': 'page-in 0.18s ease-out both',
        'pop-in': 'pop-in 0.14s ease-out both'
      },
      keyframes: {
        'card-up': {
          from: { transform: 'translateY(30px)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' }
        },
        'card-in': {
          from: { transform: 'translateY(24px) scale(0.97)', opacity: '0' },
          to: { transform: 'translateY(0) scale(1)', opacity: '1' }
        },
        'page-in': {
          from: { transform: 'translateY(6px)', opacity: '0' },
          to: { transform: 'translateY(0)', opacity: '1' }
        },
        'pop-in': {
          from: { transform: 'translateY(-4px) scale(0.98)', opacity: '0' },
          to: { transform: 'translateY(0) scale(1)', opacity: '1' }
        }
      }
    }
  },
  plugins: []
}
