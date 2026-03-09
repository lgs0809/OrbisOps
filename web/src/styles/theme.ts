// 主题配置 - 参考美团、京东等管理后台设计规范
export const theme = {
  // 主色调
  colors: {
    primary: '#2563eb',
    primaryHover: '#3b82f6',
    primaryActive: '#1d4ed8',

    // 功能色
    success: '#0f9f6e',
    warning: '#d97706',
    error: '#dc2626',
    info: '#2563eb',

    // 中性色
    text: {
      primary: '#172033',
      secondary: '#4b5563',
      tertiary: '#7b8794',
      disabled: '#aeb8c4',
    },

    // 背景色
    bg: {
      primary: '#ffffff',
      secondary: '#f6f8fb',
      tertiary: '#eef2f7',
      disabled: '#eef2f7',
    },

    // 边框色
    border: {
      primary: '#d5dce7',
      secondary: '#e7ecf3',
      tertiary: '#edf1f6',
    },

    // 品牌渐变色
    gradient: {
      primary: 'linear-gradient(135deg, #2563eb 0%, #0f766e 100%)',
      secondary: 'linear-gradient(135deg, #0f766e 0%, #65a30d 100%)',
      tertiary: 'linear-gradient(135deg, #f59e0b 0%, #dc2626 100%)',
    }
  },

  // 字体
  typography: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    fontSize: {
      xs: '12px',
      sm: '14px',
      base: '16px',
      lg: '18px',
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
      relaxed: 1.75,
    }
  },

  // 间距
  spacing: {
    xs: '4px',
    sm: '8px',
    base: '16px',
    lg: '24px',
    xl: '32px',
    '2xl': '48px',
    '3xl': '64px',
  },

  // 圆角
  borderRadius: {
    sm: '4px',
    base: '8px',
    lg: '8px',
    xl: '10px',
    '2xl': '14px',
    full: '50%',
  },

  // 阴影
  shadows: {
    sm: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
    base: '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06)',
    md: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
    lg: '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)',
    xl: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
    card: '0 2px 8px rgba(0, 0, 0, 0.06)',
    modal: '0 8px 24px rgba(0, 0, 0, 0.12)',
  },

  // 动画
  animation: {
    duration: {
      fast: '0.15s',
      normal: '0.3s',
      slow: '0.5s',
    },
    easing: {
      ease: 'ease',
      easeIn: 'ease-in',
      easeOut: 'ease-out',
      easeInOut: 'ease-in-out',
      cubic: 'cubic-bezier(0.4, 0, 0.2, 1)',
    }
  },

  // 断点
  breakpoints: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    '2xl': '1536px',
  },

  // Z-index层级
  zIndex: {
    dropdown: 1000,
    sticky: 1020,
    fixed: 1030,
    modal: 1040,
    popover: 1050,
    tooltip: 1060,
  }
};

export type Theme = typeof theme;
