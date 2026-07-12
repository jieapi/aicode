# Keep rules for release builds (R8/ProGuard).
# 适用本项目：Hilt + Room + kotlinx.serialization + Retrofit/Gson + Compose + Termux 终端。

# ---- 通用：保留注解、签名、内部类、泛型签名 ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable        # 保留崩溃栈行号

# ---- Retrofit + OkHttp ----
# Retrofit 通过反射读取 API 接口方法的注解，必须保留接口与签名。
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Gson（Retrofit converter + AILogger 用 GsonBuilder 反射序列化）----
# Retrofit/Gson 通过反射读写 data class 字段，类名与字段名不可混淆/裁剪。
#
# 注意：不能用 `-keep class ...OpenAIModels*`！OpenAIModels.kt 只是 Kotlin 文件名，
# 里面真正的 DTO 类叫 ChatCompletionRequest/OpenAIChatMessage/AnthropicMessageRequest 等，
# 没有一个以 "OpenAIModels" 开头，ProGuard 的 `*` 只匹配类名后缀，不会保留整个文件里的类。
# 字段名一旦被混淆成 a/b/c，Gson 反射序列化发出的 JSON key 就全是乱码 → 服务端 400。
# 故直接 keep 整个 remote 包下所有 DTO 类及其字段。
-keep class com.aicode.feature.agent.data.remote.** { *; }
# 任何带 @SerializedName 的字段
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Gson 的 TypeAdapter / InstanceCreator
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation class com.google.gson.reflect.TypeToken { *; }
# Gson 用 sun.misc.Unsafe（旧版）创建未初始化实例；保留构造器以免部分机型崩溃
-keepclassmembers class com.aicode.** {
    <init>();
}

# ---- kotlinx.serialization（编译期生成 $$serializer，运行时通过反射查找 Companion.serializer()）----
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 本项目所有 @Serializable 类的生成器
-keep,includedescriptorclasses class com.aicode.**$$serializer { *; }
-keepclassmembers class com.aicode.** {
    *** Companion;
}
-keepclasseswithmembers class com.aicode.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Hilt (Dagger) ----
-keep class dagger.hilt.** { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-dontwarn dagger.hilt.**

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.aicode.**.entity.** { *; }   # Room @Entity 不可混淆列名映射

# ---- Compose ----
-dontwarn androidx.compose.**

# ---- Termux terminal-emulator/view（native + JNI）----
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }

# ---- DataStore / security-crypto（内部反射）----
-dontwarn androidx.security.**

# ---- SnakeYAML / EdDSA 等第三方库在 Android 环境下的 JRE 缺失类告警忽略 ----
-dontwarn java.beans.**
-dontwarn sun.security.x509.**
