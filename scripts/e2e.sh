#!/usr/bin/env bash
# 真实可分发 bundle 的端到端验收：对 krema-demo 真跑任务、断言磁盘产物与真实进程。
# 桌面依赖（Xvfb/WebKit/xwininfo）装在构建镜像里（见 .cnb/build/Dockerfile）。
# 用法：从仓库根目录运行（依赖 krema-demo/gradlew）。
set -euo pipefail

cd "$(dirname "$0")/.."

DEMO=krema-demo
OUT="$DEMO/build/krema"
NAME="krema-demo"
# 截图证据保留天数，两周足够。
ASSET_TTL_DAYS=14

# 桌面环境：固定 display 号；截图落到工作区内，供 .cnb.yml 的 cnbcool/attachments 插件上传。
DISPLAY_NUM=99
XVFB_PID=""
RUN_LOG=/tmp/krema-bundlerun.log
SHOT="$OUT/krema-e2e-screenshot.png"

# Gradle 缓存与产物都指到 /tmp 或 build/，不污染项目源码。
CACHE_DIR=/tmp/krema-e2e-cache
rm -rf "$CACHE_DIR"
rm -rf "$OUT"

fail() {
  echo "E2E FAIL: $1" >&2
  exit 1
}

# 窗口是否存在；带 timeout 防止 X server 挂死时 xwininfo 无限阻塞。
has_window() {
  timeout 5 xwininfo -root -tree 2>/dev/null | grep -q "Krema Demo"
}

# app 进程（JVM）需要 DISPLAY，否则 webview 创建会返回 null handle 而崩溃；
# 所以 Xvfb 必须在 kremaDevRun / kremaBundleRun 之前起好。
if ! xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1; then
  echo "== 起 Xvfb :$DISPLAY_NUM（免认证）=="
  pkill -f "Xvfb :$DISPLAY_NUM" 2>/dev/null || true
  rm -f "/tmp/.X$DISPLAY_NUM-lock" "/tmp/.X11-unix/X$DISPLAY_NUM"
  Xvfb ":$DISPLAY_NUM" -screen 0 1280x1024x24 -ac >/tmp/krema-xvfb.log 2>&1 &
  XVFB_PID=$!
  for _ in $(seq 1 50); do
    xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 && break
    sleep 0.1
  done
  xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 || fail "Xvfb :$DISPLAY_NUM 起不来，见 /tmp/krema-xvfb.log"
fi
export DISPLAY=":$DISPLAY_NUM"

# 收尾杀掉本轮起的后台进程（app 与 Xvfb）。
cleanup_desktop() {
  [ -n "${bundle_run_pid:-}" ] && kill "$bundle_run_pid" 2>/dev/null || true
  [ -n "$XVFB_PID" ] && kill "$XVFB_PID" 2>/dev/null || true
}
trap cleanup_desktop EXIT

# 清掉上一轮残留窗口，避免旧窗口让本轮断言失真。
pkill -f "$DEMO/build/krema/bundle/$NAME/bin/$NAME" 2>/dev/null || true
pkill -f "$DEMO.*com.example.kremademo.Main" 2>/dev/null || true
for _ in $(seq 1 20); do
  has_window || break
  sleep 0.2
done
has_window && fail "display 上还有别的 Krema Demo 窗口（上一轮没清干净）"

# 一次跑完整个任务图：build → runtime → bundle → platform。
echo "== kremaBundlePlatform（首次，构建整张图）=="
"$DEMO/gradlew" -p "$DEMO" kremaBundlePlatform --configuration-cache --no-daemon --project-cache-dir "$CACHE_DIR"

# --- 断言产物 ---
# kremaBundle 收拢的 jar 目录（BUNDLE_DEPS 的落点），jpackage 再由它铺进 app-image/lib/app。
LIB="$OUT/deps"
[ -f "$LIB/$NAME.jar" ] || fail "缺少 app jar：$LIB/$NAME.jar"
[ -f "$LIB/$NAME-assets.jar" ] || fail "缺少 assets jar：$LIB/$NAME-assets.jar"
unzip -l "$LIB/$NAME-assets.jar" | grep -q "assets/index.html" || fail "assets jar 没带 assets/index.html"

MAIN_CLASS=$(unzip -p "$LIB/$NAME.jar" META-INF/MANIFEST.MF | grep -i '^Main-Class:' | tr -d '\r' | sed 's/Main-Class: //')
[ -n "$MAIN_CLASS" ] || fail "app jar 没有 Main-Class"
grep -q "main_class = \"$MAIN_CLASS\"" "$DEMO/krema.toml" || fail "app jar 的 Main-Class ($MAIN_CLASS) 与 krema.toml 不一致"

[ -x "$OUT/runtime/bin/java" ] || fail "运行时镜像里没有可执行 java"

BUNDLE_APP="$OUT/bundle/$NAME"
[ -d "$BUNDLE_APP" ] || fail "app-image 目录不存在：$BUNDLE_APP"
LAUNCHER="$BUNDLE_APP/bin/$NAME"
[ -x "$LAUNCHER" ] || fail "app-image 启动器不可执行：$LAUNCHER"

[ -f "$OUT/platform/appimage/$NAME.AppImage" ] || fail "缺少 AppImage：$OUT/platform/appimage/$NAME.AppImage"
[ -n "$(find "$OUT/platform/deb" -maxdepth 1 -name '*.deb' -print -quit)" ] || fail "缺少 deb 产物：$OUT/platform/deb/"

# --- kremaDevRun：dev 路径进程能起、classpath 接对 ---
# dev run 走 KREMA_DEV_URL + vite，classpath 上无 assets jar，只验初始化行。
echo "== kremaDevRun（进程启动验收）=="
set +e
timeout 20 "$DEMO/gradlew" -p "$DEMO" kremaDevRun --configuration-cache --console=plain --project-cache-dir "$CACHE_DIR" 2>&1 | tee /tmp/krema-devrun.log
set -e
grep -q 'Creating window' /tmp/krema-devrun.log || fail "kremaDevRun 未打印 Krema 初始化行：进程没起来或 classpath 接错"

# timeout 只杀了 gradle 客户端，daemon 里 kremaDevRun 仍在跑，app 进程与「Krema Demo」窗口都留着。
# 不清掉会连带坏三处：残留窗口让下面的窗口断言秒过、app 占着 project cache 锁把 kremaBundleRun 堵在等待、
# 截图拍到的是 dev 界面而非 bundle 产物。
pkill -f "$DEMO.*com.example.kremademo.Main" 2>/dev/null || true
for _ in $(seq 1 50); do
  has_window || break
  sleep 0.2
done
has_window && fail "kremaDevRun 的 app 没退干净，残留窗口会让后面的断言失真"

# --- kremaBundleRun：prod 路径真开窗口、真发前端资源 ---
# 用 --no-daemon：否则 daemon 模式下 app 的 stdout 不会被泵回 console，[Krema] 输出断言永远抓不到。
: > "$RUN_LOG"
echo "== kremaBundleRun（窗口与资源服务验收）=="
"$DEMO/gradlew" -p "$DEMO" kremaBundleRun --configuration-cache --console=plain --no-daemon --project-cache-dir "$CACHE_DIR" > "$RUN_LOG" 2>&1 &
bundle_run_pid=$!

WINDOW_SEEN=0
for _ in $(seq 1 300); do
  if has_window; then
    WINDOW_SEEN=1
    break
  fi
  kill -0 "$bundle_run_pid" 2>/dev/null || break
  sleep 0.5
done
[ "$WINDOW_SEEN" = 1 ] || fail "没等到窗口「Krema Demo」：$RUN_LOG"

grep -q '\[Krema\]' "$RUN_LOG" || fail "kremaBundleRun 的 console 里没有应用输出：$RUN_LOG"

PORT=""
for _ in $(seq 1 120); do
  PORT=$(grep -oE 'http://127\.0\.0\.1:[0-9]+' "$RUN_LOG" | head -1 | grep -oE '[0-9]+$' || true)
  [ -n "$PORT" ] && break
  kill -0 "$bundle_run_pid" 2>/dev/null || break
  sleep 0.5
done
[ -n "$PORT" ] || fail "没抓到 AssetServer 端口（日志里没有 http://127.0.0.1:<port>）：$RUN_LOG"

INDEX_CODE=$(curl -s -o /tmp/krema-index.html -w '%{http_code}' --max-time 5 "http://127.0.0.1:$PORT/index.html" || true)
[ "$INDEX_CODE" = 200 ] || fail "AssetServer /index.html 返回 $INDEX_CODE（期望 200），端口 $PORT"

# --- 截图：等页面渲染后再抓 root 窗口（无头 X 无 WM，只能按 root 抓）---
grep -q "Running event loop" "$RUN_LOG" || fail "app 没进事件循环，见 $RUN_LOG"
sleep "${RENDER_WAIT:-2}"
timeout 20 import -display ":$DISPLAY_NUM" -window root "$SHOT" 2>/dev/null ||
    fail "截图失败（import 不可用或 display 无内容）：/tmp/krema-xvfb.log"
[ -s "$SHOT" ] || fail "截图为空文件：$SHOT"
[ "$(identify -format '%k' "$SHOT" 2>/dev/null || echo 0)" -gt 1 ] ||
    fail "截图只有单一颜色，页面多半没渲染出来：$SHOT"
echo "== 截图已存：$SHOT =="

cleanup_desktop

# 二次执行 kremaBundle 应命中 UP-TO-DATE（聚合 task 无 action，单独断言会被掩盖）。
REBUILD=$("$DEMO/gradlew" -p "$DEMO" kremaBundle --configuration-cache --console=plain --project-cache-dir "$CACHE_DIR" 2>&1)
echo "$REBUILD" | grep -q "kremaBundle UP-TO-DATE" || fail "二次执行未命中 UP-TO-DATE"

echo "E2E OK"
