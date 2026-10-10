import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 前端源码在 web/，而 krema.toml 的 out_dir（插件声明的 dist 输出）指向项目根的 dist/，
// 故这里把 root 指到 web/、把产物落回项目根，插件才读得到。
export default defineConfig({
  root: "web",
  plugins: [react()],
  build: {
    outDir: "../dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
  },
});