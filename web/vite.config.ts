import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The production build lands in the jar's classpath under /webapp (WebServer serves it from there).
// `npm run dev` serves the app on :5173 and forwards the game socket to a running
// WebServer on :7070 (`java -jar target/izikstar-chess-3.1.0.jar --no-browser`).
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../target/classes/webapp',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/ws': { target: 'ws://127.0.0.1:7070', ws: true },
    },
  },
});
