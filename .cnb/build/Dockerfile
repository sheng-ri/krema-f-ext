# 云构建门禁镜像：JDK 25 + Gradle 9.8。
# 只放构建期必需项；原生 GUI 依赖等真压测 webview 时再加。
FROM cnbcool/default-build-env:latest

# 安装 JDK 25（Eclipse Temurin）
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

# 安装 Gradle 9.8.0
ARG GRADLE_VERSION=9.8.0
RUN set -eux; \
    curl -fsSL \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -o /tmp/gradle.zip; \
    unzip -q /tmp/gradle.zip -d /opt; \
    ln -sfn "/opt/gradle-${GRADLE_VERSION}" /opt/gradle; \
    rm -f /tmp/gradle.zip

ENV JAVA_HOME=/opt/jdk-25
ENV GRADLE_HOME=/opt/gradle
ENV PATH="$JAVA_HOME/bin:$GRADLE_HOME/bin:/usr/local/bin:$PATH"

# 构建期自检：JDK 大版本对不上就炸，别把问题留到跑测试时。
RUN java -version 2>&1 | grep -q '"25' && gradle --version
