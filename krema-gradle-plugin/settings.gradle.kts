rootProject.name = "krema-plugin-build"

pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
