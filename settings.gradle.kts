rootProject.name = "krema-f-ext"

include("krema-gradle-plugin")
include("krema-demo")

pluginManagement {
    includeBuild("krema-gradle-plugin") { name = "krema-plugin-build" }

    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
        }
        mavenCentral()
    }
}
