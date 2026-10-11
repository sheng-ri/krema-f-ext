#!/usr/bin/env bash
# 真实可分发 bundle 的端到端验收。
#
# 对 krema-demo 真跑任务、断言磁盘产物与真实进程，不依赖 TestKit：跑的是真实工程，断言写不成「顺着实现摆位置」。
# pnpm 与 appimagetool 装在构建镜像里（见 .cnb/build/Dockerfile），本脚本不再联网自举工具链。
#
# 用法：从仓库根目录运行（依赖 ./gradlew）。
set -euo pipefail

cd "$(dirname "$0")/.."

DEMO=krema-demo
OUT="$DEMO/build/krema"
NAME="krema-demo"

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

# 一次调用把整张任务图（build → runtime → bundle → platform）都跑出来：
# 拆成多个 ./gradlew 调用会让每一步各自存一份不同的配置缓存入口，二次跑就复不到稳定入口、误判没命中 UP-TO-DATE。
echo "== kremaBundlePlatform（首次，构建整张图）=="
./gradlew :"$DEMO":kremaBundlePlatform --configuration-cache --no-daemon --project-cache-dir "$CACHE_DIR"

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
# headless 环境起不了 WebView 窗口，这里只验「启动器可执行」这一层（验证点到打包结构为止）。

# --- 断言 platform 各格式产物 ---
# 宿主平台（Linux）配了 appimage + deb。
[ -f "$OUT/platform/appimage/$NAME.AppImage" ] || fail "缺少 AppImage：$OUT/platform/appimage/$NAME.AppImage"
# jpackage 的 deb 文件名是 `<name>_<version>_<arch>.deb`，不写死版本/架构，只认 `*.deb` 存在。
[ -n "$(find "$OUT/platform/deb" -maxdepth 1 -name '*.deb' -print -quit)" ] || fail "缺少 deb 产物：$OUT/platform/deb/"

# --- kremaDevRun：进程真能起、classpath 接对、KREMA_DEV_URL 注入 ---
# headless 下 demo 打印 Krema 初始化那一行后会在加载 libwebview.so 时抛错，进程随后退出；
# 不等窗口，只验「初始化行出现」—— 主类找不到（classpath 接错）时根本打不出这行。
echo "== kremaDevRun（进程启动验收）=="
: > /tmp/krema-devrun.log
set +e
timeout 180 ./gradlew :"$DEMO":kremaDevRun --configuration-cache --no-daemon --console=plain --project-cache-dir "$CACHE_DIR" > /tmp/krema-devrun.log 2>&1
set -e
grep -q 'Creating window' /tmp/krema-devrun.log || fail "kremaDevRun 未打印 Krema 初始化行：进程没起来或 classpath 接错"

# --- 二次执行命中 UP-TO-DATE ---
# 指真正执行 jpackage 的 kremaBundle：聚合 task 无 action，对它断言恒真，掩盖了「第二次不重跑」。
REBUILD=$(./gradlew :"$DEMO":kremaBundle --configuration-cache --no-daemon --project-cache-dir "$CACHE_DIR" 2>&1)
echo "$REBUILD" | grep -q "kremaBundle UP-TO-DATE" || fail "二次执行未命中 UP-TO-DATE"

echo "E2E OK"
