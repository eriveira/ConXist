/// <reference types="vitest/config" />
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const brandFolder = fileURLToPath(new URL('../../brand', import.meta.url));
const apiTarget = process.env.CONXIST_API_URL ?? 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@brand': brandFolder } },
  server: {
    host: '127.0.0.1',
    fs: { allow: ['.', brandFolder] },
    proxy: { '/api': apiTarget },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: false,
    globals: true,
  },
});
