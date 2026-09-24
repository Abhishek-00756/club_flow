/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Bricolage Grotesque"', '"Figtree"', 'system-ui', 'sans-serif'],
        sans: ['"Figtree"', 'system-ui', '-apple-system', '"Segoe UI"', 'sans-serif'],
      },
      colors: {
        // Chalkboard-and-marker palette: a faint green-grey wall, dark board ink, teal marker, yellow highlighter.
        chalk: {
          50: '#F7F8F5',
          100: '#EFF1EC',
          200: '#E1E5DD',
          300: '#CBD1C6',
        },
        ink: {
          DEFAULT: '#15202B',
          soft: '#3B4A57',
          mute: '#65737F',
          faint: '#98A3AC',
        },
        marker: {
          50: '#E8F4F1',
          100: '#CFE8E2',
          500: '#128C7A',
          600: '#0F7566',
          700: '#0B5C50',
        },
        hl: {
          100: '#FBF1BF',
          300: '#F4D954',
          500: '#E6BE1A',
        },
      },
      boxShadow: {
        card: '0 1px 0 rgba(21,32,43,0.04)',
        pop: '0 10px 30px -8px rgba(21,32,43,0.25)',
      },
    },
  },
  plugins: [],
};
