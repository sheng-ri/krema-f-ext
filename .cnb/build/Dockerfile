# 云构建门禁镜像：只装 JDK 25。
# Gradle 由 wrapper 自带，镜像里不必再装一份。
FROM cnbcool/default-build-env:latest

RUN set -eux; \
    ARCH="$(dpkg --print-architecture)"; \
    case "$ARCH" in \
      amd64) JDK_ARCH="x64" ;; \
      arm64) JDK_ARCH="aarch64" ;; \
      *) echo "unsupported arch: $ARCH" >&2; exit 1 ;; \
    esac; \
    curl -fsSL \
        "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25%2B36/OpenJDK25U-jdk_${JDK_ARCH}_linux_hotspot_25_36.tar.gz" \
        -o /tmp/jdk.tar.gz; \
    mkdir -p /opt/jdk-25; \
    tar -xzf /tmp/jdk.tar.gz -C /opt/jdk-25 --strip-components=1; \
    rm -f /tmp/jdk.tar.gz

ENV JAVA_HOME=/opt/jdk-25
ENV PATH="$JAVA_HOME/bin:$PATH"

# 前端构建要 pnpm；AppImage 封装要 appimagetool 及其 squashfs/patchelf 依赖。
# 原先这些在 e2e 里联网自举（npm i -g / apt-get），挪进镜像声明式装齐，e2e 不再动项目文件夹、不再每轮 apt-get。
#
# 桌面环境那一组是为e2e 真开窗口服务的：libwebview.so 链的是 libwebkit2gtk-4.1 + gtk3，
# 缺了它们进程在窗口创建后立刻以加载失败退出；Xvfb 提供无头 X server，x11-utils 给 xwininfo
# （e2e 用它断言窗口标题）。`-ac` 关掉访问控制，容器里不需要认证 cookie。
# imagemagick 出 import：e2e 用它把 X display 截成图，作为 CI 里的窗口证据。
RUN apt-get update && apt-get install -y --no-install-recommends \
        nodejs npm squashfs-tools patchelf file desktop-file-utils fakeroot unzip \
        xvfb x11-utils libwebkit2gtk-4.1-0 libgtk-3-0 imagemagick \
    && rm -rf /var/lib/apt/lists/*
RUN npm install -g pnpm@12
RUN curl -fsSL "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" \
        -o /usr/local/bin/appimagetool \
    && chmod +x /usr/local/bin/appimagetool

# 桌面环境的默认落点：Xvfb 起在:99，e2e 与本地手工验证共用同一个 display 号。
ENV DISPLAY=:99

# 构建期自检：JDK 大版本对不上就炸，别把问题留到跑测试时。
RUN java -version 2>&1 | grep -q '"25'
