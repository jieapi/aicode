package com.aicodeeditor

import android.app.Application
import com.aicodeeditor.core.util.FileLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AIEditorApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FileLogger.init(this)
        installCrashHandler()
    }

    /** 捕获未处理异常并落盘，随后交回系统默认处理器（保留原有崩溃弹窗/上报行为）。 */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.e("CRASH", "线程 ${thread.name} 未捕获异常", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }
}
