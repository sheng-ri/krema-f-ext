#!/usr/bin/env bash
# 真实可分发 bundle 的端到端验收。
#
# 对 krema-demo 真跑任务、断言磁盘产物与真实进程，不依赖 TestKit：跑的是真实工程，断言写不成「顺着实现摆位置」。
# pnpm、appimagetool 与桌面环境（Xvfb/WebKit/xwininfo）装在构建镜像里（见 .cnb/build/Dockerfile），本脚本不再联网自举工具链。
#
# 用法：从仓库根目录运行（依赖 ./gradlew）。
#
# 全程带 daemon（不加 --no-daemon）：run task 把子进程输出泵回 Gradle 的 task 流，只有真经
# daemon 才验得到这条链路 —— inheritIO() 那版输出掉进 daemon 的 fd，console 上一行都没有，
# 而窗口照开、HTTP 照通，不盯日志就会 E2E OK 放过去。
set -euo pipefail

cd "$(dirname "$0")/.."

DEMO=krema-demo
OUT="$DEMO/build/krema"
NAME="krema-demo"
# commit 附件保留天数：截图只是给人看的构建证据，两周够了，不必永久留在仓库里。
ASSET_TTL_DAYS=14

# Gradle scratch（配置缓存、依赖缓存）全部指到 /tmp，不往项目文件夹写临时文件。
# `--project-cache-dir` 会把 configuration-cache 一起搬走，一个开关即够。
CACHE_DIR=/tmp/krema-e2e-cache
rm -rf "$CACHE_DIR"
# 清掉上一轮产物，保证每轮 e2e 从干净状态跑（产物在 build/ 下，被 gitignore，不是临时文件）。
rm -rf "$OUT"

fail() {
  echo "E2E FAIL: $1" >&2
  exit 1
}

# 查 app 窗口是否存在。一律带 timeout：X server 挂死时 xwininfo 无限阻塞，
# 没有 timeout 的话下面每个轮询循环都会把脚本静默挂住。
has_window() {
  timeout 5 xwininfo -root -tree 2>/dev/null | grep -q "Krema Demo"
}

# 一次调用把整张任务图（build → runtime → bundle → platform）都跑出来：
# 拆成多个 ./gradlew 调用会让每一步各自存一份不同的配置缓存入口，二次跑就复不到稳定入口、误判没命中 UP-TO-DATE。
echo "== kremaBundlePlatform（首次，构建整张图）=="
./gradlew -p "$DEMO" kremaBundlePlatform --configuration-cache --no-daemon --project-cache-dir "$CACHE_DIR"

# --- 断言 lib ---
LIB="$OUT/lib"
[ -f "$LIB/$NAME.jar" ] || fail "缺少 app jar：$LIB/$NAME.jar"
[ -f "$LIB/$NAME-assets.jar" ] || fail "缺少 assets jar：$LIB/$NAME-assets.jar"
unzip -l "$LIB/$NAME-assets.jar" | grep -q "assets/index.html" || fail "assets jar 没带 assets/index.html"

# app jar 的 Main-Class 与主类一致
MAIN_CLASS=$(unzip -p "$LIB/$NAME.jar" META-INF/MANIFEST.MF | grep -i '^Main-Class:' | tr -d '\r' | sed 's/Main-Class: //')
[ -n "$MAIN_CLASS" ] || fail "app jar 没有 Main-Class"
grep -q "main_class = \"$MAIN_CLASS\"" "$DEMO/krema.toml" || fail "app jar 的 Main-Class ($MAIN_CLASS) 与 krema.toml 不一致"

# --- 断言 runtime ---
[ -x "$OUT/runtime/bin/java" ] || fail "运行时镜像里没有可执行 java"

# --- 断言 bundle（app-image）结构 ---
# app-image 落在 kremaBundle 产物目录下的 <appName> 子目录（macOS 为 .app）。
BUNDLE_APP="$OUT/bundle/$NAME"
[ -d "$BUNDLE_APP" ] || fail "app-image 目录不存在：$BUNDLE_APP"
LAUNCHER="$BUNDLE_APP/bin/$NAME"
[ -x "$LAUNCHER" ] || fail "app-image 启动器不可执行：$LAUNCHER"
# 启动器真能不能把窗口开出来、能不能把前端发出来，由下面的 kremaBundleRun 段落验。

# --- 断言 platform 各格式产物 ---
# 宿主平台（Linux）配了 appimage + deb。
[ -f "$OUT/platform/appimage/$NAME.AppImage" ] || fail "缺少 AppImage：$OUT/platform/appimage/$NAME.AppImage"
# jpackage 的 deb 文件名是 `<name>_<version>_<arch>.deb`，不写死版本/架构，只认 `*.deb` 存在。
[ -n "$(find "$OUT/platform/deb" -maxdepth 1 -name '*.deb' -print -quit)" ] || fail "缺少 deb 产物：$OUT/platform/deb/"

# --- kremaDevRun：进程真能起、classpath 接对、KREMA_DEV_URL 注入 ---
# dev run 有 KREMA_DEV_URL 指向 vite，classpath 上没有 assets jar，故这里只验「初始化行出现」。
# 真开窗口与资源服务的验收在下面的 kremaBundleRun —— 那边才是 prod 路径，assets jar 在 classpath 上。
echo "== kremaDevRun（进程启动验收）=="
: > /tmp/krema-devrun.log
set +e
timeout 20 ./gradlew -p "$DEMO" kremaDevRun --configuration-cache --console=plain --project-cache-dir "$CACHE_DIR" > /tmp/krema-devrun.log 2>&1
set -e
grep -q 'Creating window' /tmp/krema-devrun.log || fail "kremaDevRun 未打印 Krema 初始化行：进程没起来或 classpath 接错"

# --- kremaBundleRun：桌面环境下真开窗口，且资源服务真的把前端发出来 ---
# 起 app-image 启动器（走 kremaBundleRun，它是唯一会因进程崩溃而失败的入口），后台跑，
# 下面轮询窗口与 HTTP。启动器是长驻进程，跑完这轮由 bundle_run_pid 收掉。
# Xvfb 固定 :99 且 -ac 免访问控制：CI 与本地同一个 display 号，不需要认证 cookie。
DISPLAY_NUM=99
XVFB_PID=""
RUN_LOG=/tmp/krema-bundlerun.log
: > "$RUN_LOG"

if ! xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1; then
  echo "== 起 Xvfb :$DISPLAY_NUM（免认证）=="
  Xvfb ":$DISPLAY_NUM" -screen 0 1280x1024x24 -ac >/tmp/krema-xvfb.log 2>&1 &
  XVFB_PID=$!
  # 轮询等 X server 就绪：起得慢就直接 xwininfo 会报连不上，误判成「应用没窗口」。
  for _ in $(seq 1 50); do
    xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 && break
    sleep 0.1
  done
  xdpyinfo -display ":$DISPLAY_NUM" >/dev/null 2>&1 || fail "Xvfb :$DISPLAY_NUM 起不来，见 /tmp/krema-xvfb.log"
fi
export DISPLAY=":$DISPLAY_NUM"

# 清掉上一轮残留的应用进程：孤儿进程会把窗口留在同一个 display 上，
# 下面的窗口断言会秒命中那个旧窗口，于是本轮 app 根本没起窗口也算过（验不出东西还不报错）。
#
# 两种 app 都要清：bundle 启动器（app-image 里的可执行文件）与 kremaDevRun 起的 classpath 进程
# （shot-dev.sh 留下的）。后者命令行里没有独立路径，只能按主类名认。
pkill -f "$DEMO/build/krema/bundle/$NAME/bin/$NAME" 2>/dev/null || true
pkill -f "$DEMO.*com.example.kremademo.Main" 2>/dev/null || true
for _ in $(seq 1 20); do
  has_window || break
  sleep 0.2
done
has_window && fail "display 上还有别的 Krema Demo 窗口（上一轮没清干净），本轮截图与窗口断言都不可信"

echo "== kremaBundleRun（窗口与资源服务验收）=="
./gradlew -p "$DEMO" kremaBundleRun --configuration-cache --console=plain --project-cache-dir "$CACHE_DIR" > "$RUN_LOG" 2>&1 &
bundle_run_pid=$!

# 收尾：无论走哪条分支都不留孤儿窗口与 Xvfb。kill 打断的是 gradle，
# KremaProcessCleanupService 在 build 收尾时会把应用进程一并回收。
cleanup_desktop() {
  [ -n "${bundle_run_pid:-}" ] && kill "$bundle_run_pid" 2>/dev/null || true
  [ -n "$XVFB_PID" ] && kill "$XVFB_PID" 2>/dev/null || true
}
trap cleanup_desktop EXIT

# 窗口断言：标题就是 Main.kt 里的 title()，出现即说明窗口真在 X 上被创建了。
# 只等窗口不够 —— 缺 jlink 模块时 app 会在窗口创建后几秒内崩掉，故窗口与下面的
# 资源服务都要在 kill 之前验完。
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

# 输出断言：daemon 模式下应用输出必须回到 console。
# inheritIO() 继承的是 daemon 进程的 fd，子进程输出到不了 console（日志里一行 [Krema] 都没有）——
# 而窗口能开、HTTP 能通，所以只验窗口/接口的话这类回归照样 E2E OK。必须单独盯住日志本身。
grep -q '\[Krema\]' "$RUN_LOG" || fail "kremaBundleRun 的 console 里没有应用输出（inheritIO 会这样）：$RUN_LOG"

# 资源服务断言：AssetServer 端口随机，从日志抓；再 curl 其 /index.html，
# 验前端真的发得出来（只有 assets jar 在 classpath 上时才会 200）。
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

# --- 截图：窗口开着的时候拍一张，作为 CI 记录 ---
# 窗口出现 ≠ 页面画完：webview 建窗口在前、渲染在后，只等窗口会拍到一张空窗。
# 故先等 app 进事件循环，再给它一段渲染时间，最后才按 root 抓图
#（按 window id 抓不行：无头 X 下没有 WM，按 id 抓报 no window with specified ID）。
grep -q "Running event loop" "$RUN_LOG" || fail "app 没进事件循环，见 $RUN_LOG"
sleep "${RENDER_WAIT:-2}"
SHOT=/tmp/krema-e2e-screenshot.png
timeout 20 import -display ":$DISPLAY_NUM" -window root "$SHOT" 2>/dev/null ||
    fail "截图失败（import 不可用或 display 无内容）：/tmp/krema-xvfb.log"
[ -s "$SHOT" ] || fail "截图为空文件：$SHOT"
# 纯色图说明页面没画出来（窗口起来了但内容没渲染），这种图当证据没有意义。
[ "$(identify -format '%k' "$SHOT" 2>/dev/null || echo 0)" -gt 1 ] ||
    fail "截图只有单一颜色，页面多半没渲染出来：$SHOT"
echo "== 截图已存：$SHOT =="

# --- 截图挂到本次 commit 上（CI 里才做，2 周后自动过期）---
# 三步：取上传地址 → PUT 到对象存储 → 回调确认。地址与 token 都从接口返回里取，不自己拼。
# 上传失败只提示不中断：截图是给人看的证据，不是门禁项，e2e 的断言在上面已经全过了。
upload_commit_asset() {
  [ -n "${CNB_REPO_SLUG:-}" ] || {
    echo "（非 CI 环境，跳过附件上传）"
    return 0
  }
  local sha="${CNB_BRANCH_SHA:-}" resp up verify token path
  [ -n "$sha" ] || {
    echo "（无 CNB_BRANCH_SHA，跳过附件上传）"
    return 0
  }
  resp=$(cnb git post-commit-asset-upload-url \
    --repo "$CNB_REPO_SLUG" --sha1 "$sha" \
    --asset-name "$(basename "$SHOT")" --size "$(stat -c %s "$SHOT")" --ttl "$ASSET_TTL_DAYS" 2>&1) || {
    echo "（取上传地址失败，跳过附件上传：$resp）"
    return 0
  }
  up=$(echo "$resp" | grep -oE 'https://asset\.cnb\.cool[^"]+' | head -1)
  verify=$(echo "$resp" | grep -oE 'https://api\.cnb\.cool[^"]+' | head -1)
  [ -n "$up" ] && [ -n "$verify" ] || {
    echo "（上传地址解析失败，跳过附件上传）"
    return 0
  }
  curl -fsS -X PUT --upload-file "$SHOT" "$up" || {
    echo "（PUT 失败，跳过附件上传）"
    return 0
  }
  # 确认接口要从 verify_url 上取 token 与 path，两段路径参数在 URL 里是百分号编码的，别自己拼。
  token=$(echo "$verify" | grep -oE 'asset-upload-confirmation/[^/]+' | cut -d/ -f3)
  path=$(echo "$verify" | grep -oE '%2F[^?]+\.png' | head -1 | sed 's/%2F/\//g')
  cnb git post-commit-asset-upload-confirmation \
    --repo "$CNB_REPO_SLUG" --sha1 "$sha" \
    --upload-token "$token" --asset-path "$path" --ttl "$ASSET_TTL_DAYS" >/dev/null 2>&1 ||
    {
      echo "（确认失败，附件可能没挂上）"
      return 0
    }
  echo "== 截图已挂到 commit $sha（$ASSET_TTL_DAYS 天后过期）=="
}
upload_commit_asset

cleanup_desktop
bundle_run_pid=""
trap - EXIT

# --- 断言二次执行命中 UP-TO-DATE ---
# 指真正执行 jpackage 的 kremaBundle：聚合 task 无 action，对它断言恒真，掩盖了「第二次不重跑」。
REBUILD=$(./gradlew -p "$DEMO" kremaBundle --configuration-cache --console=plain --project-cache-dir "$CACHE_DIR" 2>&1)
echo "$REBUILD" | grep -q "kremaBundle UP-TO-DATE" || fail "二次执行未命中 UP-TO-DATE"

echo "E2E OK"
