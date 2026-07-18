import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")

    kotlin("plugin.compose")
    kotlin("plugin.serialization") version "2.2.21"
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

// 从本地 keystore.properties 读取 release 签名密钥（已 gitignore，不入库）。
// 若文件不存在（如 CI 环境）则跳过，release 产出 unsigned 包。
val keystorePropertiesFile = file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// versionCode 从 git 提交数自动生成：随每次提交单调递增，无需手动维护，
// 杜绝"升 versionName 忘升 versionCode"导致升级判定失效。
// 工作目录用 rootProject.projectDir（仓库根），无 git 环境（如下载 zip 构建）时 fallback 到 1。
// CI 额外校验 versionCode 单调（见 .github/workflows/android-release.yml），防 rebase/squash 改写历史导致回退。
fun gitCommitCount(): Int = try {
    val process = Runtime.getRuntime().exec(
        arrayOf("git", "rev-list", "--count", "HEAD"),
        null,
        rootProject.projectDir
    )
    process.waitFor()
    process.inputStream.bufferedReader().readText().trim().toIntOrNull() ?: 1
} catch (e: Exception) {
    1
}

android {
    namespace = "com.aicode"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.aicode"
        minSdk = 26
        // 锁定 targetSdk 28：Android 10+（API 29+）的 W^X/SELinux 策略禁止执行 App 可写
        // 数据目录里的文件，PRoot 二进制将无法运行（同 Termux 的取舍）。代价：不能上 Google Play。
        targetSdk = 28
        versionCode = gitCommitCount()
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 按容器镜像拆包：universal 同时含 arm + x86 两套 Alpine rootfs/proot（兼容所有设备但体积大），
    // armsolo 仅含 arm（对应 arm64-v8a），x86solo 仅含 x86（对应 x86_64）
    // ——单架构包体积约为通用包的一半。assets/container/... 由各 flavor 的 sourceSet 提供，
    // ContainerInstaller.ASSET_DIR 在 universal 下按设备 ABI 选其一，在 solo 包里只剩一套故一定命中。
    //
    // 注意：defaultConfig 不再固定 abiFilters，改由各 flavor 维度决定；universal 默认含全部
    // 依赖的 ABI（arm64-v8a + x86_64），单架构包各自收敛到单一 ABI，避免错架构设备加载错误的镜像。
    flavorDimensions += "container"
    productFlavors {
        create("universal") {
            dimension = "container"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        create("armsolo") {
            dimension = "container"
            ndk { abiFilters += "arm64-v8a" }
        }
        create("x86solo") {
            dimension = "container"
            ndk { abiFilters += "x86_64" }
        }
    }

    // 容器镜像按 flavor 共享 sourceSet：
    //   _armAssets 仅物理一份 arm 镜像，由 universal + armsolo 共享
    //   _x86Assets 仅物理一份 x86 镜像，由 universal + x86solo 共享
    // 这样单架构包天然只含一套镜像（AGP 资源并集合并：未被引用的目录不参与），
    // universal 含两套；镜像二进制在仓库里也只各一份，无重复。
    // 放弃 ignoreAssetsPattern=dir:x86：实测对 container/x86 整树无效（rc1 已证实）。
    sourceSets {
        getByName("universal") { assets.srcDir("src/_armAssets") }
        getByName("universal") { assets.srcDir("src/_x86Assets") }
        getByName("armsolo") { assets.srcDir("src/_armAssets") }
        getByName("x86solo") { assets.srcDir("src/_x86Assets") }
    }

    buildTypes {
        // debug 加包名后缀 .debug → applicationId 变 com.aicode.debug，与 release（com.aicode）
        // 可同机共存、互不覆盖。IDE 跑 debug 不再因签名不同卸载已装的正式版。
        // 注意：因 applicationId 不同，debug 变体私有目录为 /data/data/com.aicode.debug/，
        // release 已解压的容器 rootfs 与工作区项目在 debug 下不可见（需重新解压/clone），属预期隔离行为。
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // targetSdk 故意锁定 28（PRoot 需在 app 可写目录执行二进制，Android 10+ W^X 禁止），
    // 代价是不进 Google Play——故关闭该平台的过期 targetSdk 检查。
    // 同时关闭 release 构建的 lint 检查：本仓库只出 GitHub Release 不上 Play，
    // lintVital 在 R8/打包阶段额外吃 CPU 与内存（2 核 7GB runner 易 OOM），且其发现不阻塞发布。
    lint {
        disable += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// 彻底禁用 lintVital<Flavor>Release 任务（三 flavor 各一个），
// 使其不进入 assembleRelease 的任务图——比 lint.checkReleaseBuilds=false 更省构建开销与内存。
// 仅在 release 任务图执行前禁用，避免影响开发期 debug lint。
gradle.projectsEvaluated {
    tasks.matching { it.name.startsWith("lintVital") }.configureEach { enabled = false }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2025.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.animation:animation")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Hilt 依赖注入
    implementation("com.google.dagger:hilt-android:2.56.1")
    ksp("com.google.dagger:hilt-compiler:2.56.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room 数据库
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // HTML 解析与清洗 (用于 WebFetchTool)
    implementation("org.jsoup:jsoup:1.18.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Kotlin 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // YAML 解析 (用于 Skill Frontmatter)
    implementation("org.yaml:snakeyaml:2.2")

    // 远程同步 (SFTP/FTP) 与内置 FTP 服务端
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("commons-net:commons-net:3.10.0")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // 容器：解压 Alpine rootfs tar.gz（正确处理 symlink/hardlink/权限位）
    implementation("org.apache.commons:commons-compress:1.26.2")
    // xz 解压支持：commons-compress 的 XZCompressorInputStream 依赖此库（解压用户导入的 .tar.xz 镜像）
    implementation("org.tukaani:xz:1.10")

    // Termux 开源终端组件：terminal-emulator 负责 VT100/ANSI 解析与 PTY（自带 native .so），
    // terminal-view 是渲染用的 Android View。经 JitPack 分发（com.github.<user>.<repo> 坐标形式），
    // 避免自行实现终端模拟器。
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")

    // Material Icons
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Lucide Icons
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")

    // Markdown Renderer
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.41.0")
    // Markdown Renderer — Code Syntax Highlighting
    implementation("com.mikepenz:multiplatform-markdown-renderer-code:0.41.0")

    // Core Android
    implementation("androidx.core:core:1.16.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
