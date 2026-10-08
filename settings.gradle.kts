rootProject.name = "krema-f-ext"

include("krema-cli-spi", "krema-gradle-plugin", "krema-demo")

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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.cnb.cool/BirthCat-Dev/jars/-/packages/")
        }
        mavenCentral()
    }
}
