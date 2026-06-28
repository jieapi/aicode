package com.aicodeeditor.core.ui

import android.os.Build
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 跟随软键盘逐帧移动的底部内边距（用于把底栏 / 操作栏顶在键盘之上）。
 * 取 ime 与 navigationBars 的较大值：键盘弹出时浮于键盘上，收起时停在导航栏上，无重复内边距。
 *
 * 为什么要这么费劲：本应用锁定 `targetSdk = 28`（PRoot 需从可写目录执行，见 build.gradle.kts）。
 * 在 targetSdk < 30 下，`windowSoftInputMode=adjustResize` 走的是旧版兼容缩放路径，它会**吞掉**
 * 平台的 IME inset 逐帧动画——`WindowInsets.ime` 只在键盘动画结束后一步到位。表现就是「键盘先弹起、
 * 底栏再迟一拍跟上」。
 *
 * 解决：在 API 30+ 上，Activity.onCreate 把窗口切到 `ADJUST_NOTHING`（让旧版缩放路径让位），改由
 * 我们用 inset 把底栏顶起来；同时本函数挂一个 [WindowInsetsAnimationCompat] 回调，在 onProgress
 * 里读取键盘**当前帧**位置驱动内边距，从而与键盘锁帧同步移动。
 *
 * **重要**：softInputMode 由 Activity 统一设置一次，本函数 onDispose 不再还原——
 * 多个页面（Terminal / Chat）同时使用本函数时，NavHost 过渡动画期间两个 composable 共存，
 * 旧页面 dispose 会把新页面刚设好的回调清掉、把 softInputMode 切回 adjustResize，
 * 触发窗口重新布局导致白屏。
 *
 * API < 30 没有 IME inset 动画 API，保持 manifest 的 adjustResize 旧行为（窗口缩放，功能正常，
 * 只是没有顺滑动画）。
 */
@Composable
fun rememberImeBottomInset(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current

    // 静止 / 最终态：键盘动画结束、旋转、首帧由 Compose inset 给出准确值。
    val restingPx = maxOf(
        WindowInsets.ime.getBottom(density),
        WindowInsets.navigationBars.getBottom(density)
    )

    // 动画进行中由 onProgress 提供逐帧值。
    var progressPx by remember { mutableIntStateOf(restingPx) }
    var animating by remember { mutableStateOf(false) }

    if (Build.VERSION.SDK_INT >= 30) {
        DisposableEffect(view) {
            val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animating = true
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    progressPx = maxOf(ime, nav)
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) animating = false
                }
            }
            ViewCompat.setWindowInsetsAnimationCallback(view, callback)

            onDispose { }
        }
    }

    // 非受控的弹键盘路径（如终端用 InputMethodManager.showSoftInput 触发软键盘）拿不到逐帧
    // onProgress 回调，restingPx 会等 WindowInsets.ime 落定后一步跳到最终值，表现为底栏「瞬移」。
    // 在 API 30+ 上给这个静止值补一段补间，让底栏平滑滑动；受控路径（Compose TextField）有逐帧
    // 回调时 animating=true，直接走 progressPx，不叠加二次动画。
    // API < 30 不额外适配：沿用 manifest 的 adjustResize 系统默认行为，旧设备保持原样。
    val animatedRestingPx by animateIntAsState(
        targetValue = restingPx,
        animationSpec = tween(durationMillis = 220),
        label = "ime-resting"
    )
    val px = when {
        animating -> progressPx
        Build.VERSION.SDK_INT >= 30 -> animatedRestingPx
        else -> restingPx
    }
    return with(density) { px.toDp() }
}