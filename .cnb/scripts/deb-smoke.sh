#!/usr/bin/env bash
# Linux 真产物验收：造一个最小 consumer 工程，让 kremaBundle 真打一个 .deb。
#
# 只在手动入口跑 —— .deb 会把整个 runtime（几百 MB）塞进去，进 PR 门禁太贵（见 #27）。
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

version="$("$root/gradlew" -p "$root" -q properties --property version | sed -n 's/^version: //p')"
echo "插件版本: $version"

echo "== 发布插件到本地 maven，供 consumer 工程消费 =="
"$root/gradlew" -p "$root" publishToMavenLocal -x test --configuration-cache

echo "== 造最小 consumer 工程 =="
mkdir -p "$workdir/src-java/com/example"
cat > "$workdir/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
rootProject.name = "deb-smoke"
EOF

cat > "$workdir/build.gradle.kts" <<EOF
import top.birthcat.kremaf.ext.gradle.task.KremaBundleTask

plugins {
    java
    id("top.birthcat.kremaf.ext") version "${version}"
}

krema {
    mainClass = "com.example.Main"
}

// Krema 约定的 Java 源码目录是 krema.toml 的 src-java，接到标准 source set 上。
sourceSets.main {
    java.setSrcDirs(listOf("src-java"))
}

tasks.named<KremaBundleTask>("kremaBundle") {
    target.set("linux-x86_64")
}
EOF

cat > "$workdir/krema.toml" <<'EOF'
[package]
name = "deb-smoke"
identifier = "com.example.debsmoke"
version = "1.0.0"

[build]
main_class = "com.example.Main"
EOF

cat > "$workdir/src-java/com/example/Main.java" <<'EOF'
package com.example;

public final class Main {
    public static void main(String[] args) {
        System.out.println("deb-smoke");
    }
}
EOF

echo "== 真打 .deb =="
"$root/gradlew" -p "$workdir" kremaBundle --configuration-cache --stacktrace

echo "== 校验产物 =="
deb="$(find "$workdir/build/krema" -name '*.deb' -print -quit)"
if [ -z "$deb" ]; then
    echo "没找到 .deb，打包路径没接通" >&2
    find "$workdir/build/krema" -maxdepth 3 -print >&2
    exit 1
fi

echo "产物: $deb ($(du -h "$deb" | cut -f1))"
dpkg-deb --info "$deb"
dpkg-deb --contents "$deb" > "$workdir/contents.txt"
sed -n '1,20p' "$workdir/contents.txt"
