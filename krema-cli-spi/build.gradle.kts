plugins {
    `java-library`
}

dependencies {
    api(libs.krema.core)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
