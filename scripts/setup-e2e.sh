#!/usr/bin/env bash
# 本地开发用的 e2e 工具链自举：装上 pnpm 与 appimagetool（及其依赖），
# 让 scripts/e2e.sh 能在开发机上直接跑，不必等 CI 镜像。
#
# CI 镜像（.cnb/build/Dockerfile）已声明式装齐这些，本脚本只服务本地。
# 用法：从仓库根目录运行（依赖 root / sudo 装系统包与全局 npm 包）。
set -euo pipefail

cd "$(dirname "$0")/.."

# --- pnpm：demo 前端构建要用 ---
if ! command -v pnpm >/dev/null 2>&1; then
  echo "== 安装 pnpm =="
  npm install -g pnpm@9
fi

# --- appimagetool：AppImage 封装要用，放进 PATH ---
# 工具本身是 AppImage，CI 镜像里也是同一份；本机缺了就下载到 /usr/local/bin。
if ! command -v appimagetool >/dev/null 2>&1; then
  echo "== 下载 appimagetool 到 /usr/local/bin =="
  curl -fsSL "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" \
    -o /usr/local/bin/appimagetool
  chmod +x /usr/local/bin/appimagetool
fi

# --- appimagetool 封装 AppImage 要的 squashfs 工具链，以及 jpackage 打 deb 要的 fakeroot（按需补齐） ---
MISSING_DEPS=""
for dep in squashfs-tools patchelf file desktop-file-utils fakeroot; do
  bin=$(case "$dep" in
    squashfs-tools) echo mksquashfs ;;
    file) echo file ;;
    desktop-file-utils) echo desktop-file-validate ;;
    patchelf) echo patchelf ;;
    fakeroot) echo fakeroot ;;
  esac)
  command -v "$bin" >/dev/null 2>&1 || MISSING_DEPS="$MISSING_DEPS $dep"
done
if [ -n "$MISSING_DEPS" ]; then
  echo "== 安装缺失依赖:$MISSING_DEPS =="
  apt-get update
  apt-get install -y $MISSING_DEPS
fi

echo "setup-e2e 完成：pnpm 与 appimagetool 已就绪（$(command -v appimagetool)）"
