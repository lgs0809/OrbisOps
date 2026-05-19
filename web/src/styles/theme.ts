export const theme = {
  colors: {
    primary: '#2563eb',
    primaryHover: '#1d4ed8',
    primaryActive: '#1e40af',

    success: '#0f8a5f',
    warning: '#c96a09',
    error: '#d73a49',
    info: '#2563eb',

    text: {
      primary: '#171a1f',
      secondary: '#505762',
      tertiary: '#6b7280',
      disabled: '#aeb4bd',
    },

    bg: {
      primary: '#ffffff',
      secondary: '#f7f8fa',
      tertiary: '#f0f1f3',
      disabled: '#f1f2f4',
    },

    border: {
      primary: '#d6d9de',
      secondary: '#e6e8eb',
      tertiary: '#eef0f2',
    },

    gradient: {
      primary: 'linear-gradient(135deg, #2563eb 0%, #111827 100%)',
      secondary: 'linear-gradient(135deg, #0f766e 0%, #2563eb 100%)',
      tertiary: 'linear-gradient(135deg, #f59e0b 0%, #dc2626 100%)',
    },
  },

  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Inter, Roboto, "Helvetica Neue", Arial, sans-serif',
    fontSize: {
      xs: '11px',
      sm: '13px',
      base: '15px',
      lg: '17px',
      xl: '20px',
      '2xl': '24px',
      '3xl': '30px',
      '4xl': '36px',
    },
    fontWeight: {
      normal: 400,
      medium: 500,
      semibold: 600,
      bold: 700,
    },
    lineHeight: {
      tight: 1.25,
      normal: 1.5,
      relaxed: 1.7,
    },
  },

  spacing: {
    xs: '4px',
    sm: '8px',
    base: '16px',
    lg: '24px',
    xl: '32px',
    '2xl': '48px',
    '3xl': '64px',
  },

  borderRadius: {
    sm: '6px',
    base: '10px',
    lg: '12px',
    xl: '14px',
    '2xl': '16px',
    full: '999px',
  },

  shadows: {
    sm: '0 1px 2px rgb(15 23 42 / 4%)',
    base: '0 1px 3px rgb(15 23 42 / 6%), 0 1px 2px rgb(15 23 42 / 3%)',
    md: '0 8px 24px rgb(15 23 42 / 8%)',
    lg: '0 18px 48px rgb(15 23 42 / 12%)',
    xl: '0 24px 64px rgb(15 23 42 / 14%)',
    card: '0 2px 10px rgb(15 23 42 / 5%)',
    modal: '0 20px 64px rgb(15 23 42 / 16%)',
  },

  animation: {
    duration: {
      fast: '0.12s',
      normal: '0.2s',
      slow: '0.35s',
    },
    easing: {
      ease: 'ease',
      easeIn: 'ease-in',
      easeOut: 'ease-out',
      easeInOut: 'ease-in-out',
      cubic: 'cubic-bezier(0.4, 0, 0.2, 1)',
    },
  },

  breakpoints: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    '2xl': '1536px',
  },

  zIndex: {
    dropdown: 1000,
    sticky: 1020,
    fixed: 1030,
    modal: 1040,
    popover: 1050,
    tooltip: 1060,
  },
};

export type Theme = typeof theme;
