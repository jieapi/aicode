import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    
    kotlin("plugin.serialization") version "1.9.22"
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

android {
    namespace = "com.aicodeeditor"
    compileSdk = 34

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
        applicationId = "com.aicodeeditor"
        minSdk = 26
        // 锁定 targetSdk 28：Android 10+（API 29+）的 W^X/SELinux 策略禁止执行 App 可写
        // 数据目录里的文件，PRoot 二进制将无法运行（同 Termux 的取舍）。代价：不能上 Google Play。
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        // 仅打包 arm64-v8a 的 rootfs/proot，避免无意义地为其它架构膨胀 APK。
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // targetSdk 故意锁定 28（PRoot 需在 app 可写目录执行二进制，Android 10+ W^X 禁止），
    // 代价是不进 Google Play——故关闭该平台的过期 targetSdk 检查。
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.animation:animation")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Hilt 依赖注入
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room 数据库
    implementation("androidx.room:room-runtime:2.6.0")
    implementation("androidx.room:room-ktx:2.6.0")
    ksp("androidx.room:room-compiler:2.6.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.0")

    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // HTML 解析与清洗 (用于 WebFetchTool)
    implementation("org.jsoup:jsoup:1.17.2")

    // 安全存储
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Kotlin 序列化
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // YAML 解析 (用于 Skill Frontmatter)
    implementation("org.yaml:snakeyaml:2.2")

    // 远程同步 (SFTP/FTP)
    implementation("com.hierynomus:sshj:0.38.0")
    implementation("commons-net:commons-net:3.10.0")

    // 容器：解压 Alpine rootfs tar.gz（正确处理 symlink/hardlink/权限位）
    implementation("org.apache.commons:commons-compress:1.26.2")

    // Termux 开源终端组件：terminal-emulator 负责 VT100/ANSI 解析与 PTY（自带 native .so），
    // terminal-view 是渲染用的 Android View。经 JitPack 分发（com.github.<user>.<repo> 坐标形式），
    // 避免自行实现终端模拟器。
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")

    // WebView (用于代码编辑器)
    implementation("androidx.webkit:webkit:1.11.0")

    // Material Icons
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Markdown Renderer
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.24.0")

    // Core Android
    implementation("androidx.core:core:1.13.0")

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
