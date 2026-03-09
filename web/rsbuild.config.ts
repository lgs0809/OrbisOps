import { pluginReact } from '@rsbuild/plugin-react';
import { pluginLess } from '@rsbuild/plugin-less';
import { defineConfig } from '@rsbuild/core';

export default defineConfig({
  plugins: [pluginReact(), pluginLess()],
  source: {
    entry: {
      index: './src/app.tsx',
    },
    define: {
      __API_BASE_DOMAIN__: JSON.stringify(process.env.API_BASE_DOMAIN || ''),
      __PUBLIC_API_BASE_DOMAIN__: JSON.stringify(process.env.PUBLIC_API_BASE_DOMAIN || ''),
      'import.meta.env.API_BASE_DOMAIN': JSON.stringify(process.env.API_BASE_DOMAIN || ''),
      'import.meta.env.PUBLIC_API_BASE_DOMAIN': JSON.stringify(process.env.PUBLIC_API_BASE_DOMAIN || ''),
    },
  },
  html: {
    title: 'OrbisOps',
  },
  performance: {
    chunkSplit: {
      strategy: 'split-by-experience',
      forceSplitting: {
        react: /node_modules[\\/](react|react-dom|react-router-dom)[\\/]/,
        semi: /node_modules[\\/]@douyinfe[\\/]/,
        xyflow: /node_modules[\\/]@xyflow[\\/]/,
      },
    },
  },
});
