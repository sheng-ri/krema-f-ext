#!/usr/bin/env bash
# 快速截图：起 vite dev server + kremaDevRun，拍一张窗口图就走。
#
# 与 e2e.sh 的区别：e2e 验的是打包产物（bundle → app-image 启动器），慢且要 jpackage；
# 这里走 kremaDevRun（classpath 上直接起 JVM），配合 vite dev server 出图，几秒一张。
#
# 只启动、不收尾：脚本退出后 vite 与 app 继续留着，方便反复调页面；
# 下次执行会先杀掉上一轮再起新的。手动停用 pkill 命令见下方 CLEANUP_HINT。
#
# 用法：从仓库根目录运行（依赖 krema-demo/gradlew、pnpm、import）。
set -euo pipefail

cd "$(dirname "$0")/.."

DEMO=krema-demo
FRONT=$DEMO/front
NAME=krema-demo
DISPLAY_NUM=99
SHOT=/tmp/krema-devshot.png
RUN_LOG=/tmp/krema-devshot.log

fail() {
  echo "SHOT FAIL: $1" >&2
  exit 1
}

# 查窗口是否存在。一律带 timeout：X server 挂死时 xwininfo 会无限阻塞，
# 没有 timeout 的话下面每个轮询循环都会卡死，整个脚本静默挂住。
has_window() {
  timeout 5 xwininfo -root -tree 2>/dev/null | grep -q "Krema Demo"
}

# --- 杀掉上一轮 ---
# 按命令行特征匹配，不记 pid 文件：上一轮可能是被 Ctrl-C 打断的，pid 文件会过期，反过来误杀新进程。
#
# 三个特征各自对应实际进程：gradlew 客户端带 task 名；app 由 kremaDevRun 拉起，是 Gradle 那个
# JVM 里的 java 主类（classpath 起法，命令行里没有独立进程可按路径匹配）；vite 的命令行是
# `node <front>/node_modules/.../vite.js` —— 路径在 vite **之前**，故模式要写成 front 在前。
cleanup_previous() {
  pkill -f "gradlew.*kremaDevRun" 2>/dev/null || true
  pkill -f "com\.example\.kremademo\.Main" 2>/dev/null || true
  pkill -f "vite/bin/vite\.js" 2>/dev/null || true
  # app 的窗口留在同一个 display 上会污染本轮截图，等它真消失再继续。
  for _ in $(seq 1 20); do
    has_window || break
    sleep 0.2
  done
}

# --- Xvfb：没起才起，起了就复用 ---
# 固定 :99 且 -ac 免认证，和 e2e.sh 共用一个 display 号。
# lock 文件要一起清：进程被 kill -9 后 /tmp/.X99-lock 会留下，而 Xvfb 只看 lock 就报
# 「Server is already active」并不启动 —— 此时 socket 在但没人应答，所有 X 调用静默挂死。
if ! timeout 5 xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1; then
  echo "== 起 Xvfb :$DISPLAY_NUM =="
  pkill -f "Xvfb :$DISPLAY_NUM" 2>/dev/null || true
  rm -f "/tmp/.X$DISPLAY_NUM-lock" "/tmp/.X11-unix/X$DISPLAY_NUM"
  Xvfb ":$DISPLAY_NUM" -screen 0 1280x1024x24 -ac >/tmp/krema-shot-xvfb.log 2>&1 &
  for _ in $(seq 1 50); do
    timeout 5 xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 && break
    sleep 0.1
  done
  timeout 5 xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 || fail "Xvfb :$DISPLAY_NUM 起不来，见 /tmp/krema-shot-xvfb.log"
fi
export DISPLAY=":$DISPLAY_NUM"

cleanup_previous

# --- vite dev server ---
# 后台起、detached：脚本退出后它要继续活着（vite 一停，app 里的 webview 就白屏）。
# KREMA_DEV_URL 由 kremaDevRun 注入给 app 进程，两边靠 vite.config.ts 的 5173 对上。
if [ ! -d "$FRONT/node_modules" ]; then
  echo "== 首次：装前端依赖 =="
  (cd "$FRONT" && pnpm install)
fi
echo "== 起 vite dev server（5173）=="
(cd "$FRONT" && setsid pnpm run dev >/tmp/krema-shot-vite.log 2>&1 &)
for _ in $(seq 1 100); do
  curl -s -o /dev/null --max-time 2 http://127.0.0.1:5173/ && break
  sleep 0.3
done
curl -s -o /dev/null --max-time 2 http://127.0.0.1:5173/ || fail "vite 没起来，见 /tmp/krema-shot-vite.log"

# --- kremaDevRun：app 进程 ---
# 同样 detached：脚本退出后 app 要留着。gradle 输出既显示到终端也落文件，下面按就绪标志判成功。
echo "== 起 kremaDevRun =="
setsid sh -c "$DEMO/gradlew -p $DEMO kremaDevRun --console=plain < /dev/null 2>&1 | tee $RUN_LOG" &

# 窗口出现 ≠ 能拍到东西：webview 建窗口在前，渲染页面在后，
# 只等窗口会拍到一张还没加载的空窗。故两个条件都要满足：窗口在，且 app 已进事件循环。
for _ in $(seq 1 200); do
  has_window && grep -q "Running event loop" "$RUN_LOG" 2>/dev/null && break
  grep -q "BUILD FAILED" "$RUN_LOG" 2>/dev/null && fail "kremaDevRun 失败，见 $RUN_LOG"
  sleep 0.5
done
has_window || fail "没等到窗口「Krema Demo」，见 $RUN_LOG"
grep -q "Running event loop" "$RUN_LOG" 2>/dev/null || fail "app 没进事件循环（webview 可能没起来），见 $RUN_LOG"

# --- 截图 ---
# 抓 root 而非按 window id：Xvfb 下 app 窗口没有 WM 装饰，按 id 抓会报 no window with specified ID。
timeout 20 import -display ":$DISPLAY_NUM" -window root "$SHOT" 2>/dev/null || fail "截图失败，见 /tmp/krema-shot-xvfb.log"
[ -s "$SHOT" ] || fail "截图为空：$SHOT"

cat <<EOF
SHOT OK: $SHOT
app 与 vite 仍在跑（Ctrl-C 结束本脚本不影响它们）：
  vite      http://127.0.0.1:5173   日志 /tmp/krema-shot-vite.log
  app 日志  $RUN_LOG
要停掉：
  pkill -f gradlew.*kremaDevRun; pkill -f "$DEMO.*com.example.kremademo.Main"; pkill -f "$FRONT.*vite"
EOF