pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Termux 终端组件（terminal-emulator / terminal-view）只在 JitPack 发布，腾讯镜像没有
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
    }
}
rootProject.name = "app"

include(":app")

