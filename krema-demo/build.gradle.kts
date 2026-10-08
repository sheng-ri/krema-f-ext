// 验收第二层的载体：一个真能编译、真能跑的最小 Krema 应用。
//
// 它故意不挂 krema 插件 —— 第二层验的是「中间层 run() 能起真进程并判到握手行」，
// 不是插件接线（那是第一层 TestKit 的活）。挂插件会让本仓自举出一个复合构建环。
plugins {
    java
}

// 主类住在 Krema 约定的 src-java，不是 Gradle 默认的 src/main/java。
sourceSets {
    main {
        java.setSrcDirs(listOf("src-java"))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}
