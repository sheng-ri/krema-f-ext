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

# 构建期自检：JDK 大版本对不上就炸，别把问题留到跑测试时。
RUN java -version 2>&1 | grep -q '"25'
