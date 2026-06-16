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
      __ORBISOPS_API_BASE_URL__: JSON.stringify(process.env.ORBISOPS_API_BASE_URL || ''),
      'import.meta.env.ORBISOPS_API_BASE_URL': JSON.stringify(process.env.ORBISOPS_API_BASE_URL || ''),
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
