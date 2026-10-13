import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// 配置与源码同在 front/，而 npm 命令也由插件在这个目录里跑，故 root 取默认的当前目录、
// 产物直接落在 dist/。产物进 app jar 后固定落在 assets/ 下，与主类 prodAssets("/assets") 对应。
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
  },
});
