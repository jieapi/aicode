package com.aicodeeditor

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aicodeeditor.core.theme.AIEditorTheme
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.component.AIChatPanel
import com.aicodeeditor.feature.agent.presentation.component.ChatDrawerContent
import com.aicodeeditor.feature.git.presentation.GitViewModel
import com.aicodeeditor.feature.git.presentation.component.GitScreen
import com.aicodeeditor.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicodeeditor.feature.settings.presentation.SettingsViewModel
import com.aicodeeditor.feature.settings.presentation.component.SettingsScreen
import com.aicodeeditor.feature.terminal.domain.TerminalKeepaliveService
import com.aicodeeditor.feature.terminal.presentation.TerminalViewModel
import com.aicodeeditor.feature.terminal.presentation.component.TerminalScreen
import com.aicodeeditor.feature.workspace.presentation.WorkspaceViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** 用于冷启动时在前台恢复常驻保活通知（App.onCreate 的启动可能被后台 FGS 限制挡掉）。 */
    @Inject
    lateinit var keepaliveSettings: KeepaliveSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // 绘制到系统状态栏/导航栏之下，让应用背景与系统栏融为一体（消除割裂的色块）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // API 30+：全局切到 ADJUST_NOTHING，由 rememberImeBottomInset() 接管键盘内边距。
        // 必须在 Activity 级别统一设置，不能在每个 composable 里各自 save/restore——
        // NavHost 过渡动画期间新旧页面共存，旧页面 dispose 恢复 softInputMode 会触发窗口重布局导致白屏。
        if (Build.VERSION.SDK_INT >= 30) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        setContent {
            AIEditorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }

        // 此时处于前台，启动前台服务一定被允许：若用户曾开启常驻保活，补上通知。
        lifecycleScope.launch {
            if (keepaliveSettings.isEnabled()) {
                TerminalKeepaliveService.enablePersistent(this@MainActivity)
            }
        }
    }
}

/**
 * 根导航容器。
 *
 * [ModalNavigationDrawer] 放在 [NavHost] **外面**，使 Drawer 的生命周期独立于页面切换。
 *
 * NavHost 禁用了全部过渡动画（enterTransition / exitTransition = None）——
 * Terminal 页面的 [AndroidView] 不参与 Compose 的 graphicsLayer alpha 动画，
 * 如果保留默认 fadeIn/fadeOut，过渡期间新旧 composable 共存，TerminalView 以满不透明度
 * 覆盖在新页面之上；过渡结束后原生 View 被移除触发 View 层级重布局，恰与 Drawer 打开动画
 * 叠加导致渲染管线中断——表现为「退出终端后立即点侧边栏白屏」。
 *
 * ViewModel 提升到这一层创建，以便 Drawer 内容和 AIChatPanel 共享同一实例。
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 用于判断当前路由：仅在聊天页允许 Drawer 手势。
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // Activity 级别的 ViewModel——Drawer 和 AIChatPanel 共享同一个实例。
    val agentViewModel: AIAgentViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val workspaceViewModel: WorkspaceViewModel = hiltViewModel()

    // 侧边栏打开时，系统返回键先收起侧边栏。
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // 侧边栏需要的数据。
    val currentWorkspace by workspaceViewModel.current.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(currentWorkspace) {
        agentViewModel.setWorkspace(currentWorkspace?.path ?: "")
    }

    val sessions by agentViewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by agentViewModel.currentSessionId.collectAsStateWithLifecycle()
    val agentStates by agentViewModel.agentStates.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 仅在聊天页启用手势滑出；其他页面禁止（但已打开时始终可关闭）。
        gesturesEnabled = currentRoute == "chat" || drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RectangleShape,
                drawerContainerColor = androidx.compose.ui.graphics.Color.White,
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(300.dp)
            ) {
                ChatDrawerContent(
                    sessions = sessions,
                    currentSessionId = currentSessionId,
                    agentStates = agentStates,
                    onSelect = {
                        agentViewModel.selectSession(it.id)
                        scope.launch { drawerState.close() }
                    },
                    onCreate = {
                        agentViewModel.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onDelete = { agentViewModel.deleteSession(it.id) },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings")
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = "chat",
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable("chat") {
                AIChatPanel(
                    viewModel = agentViewModel,
                    settingsViewModel = settingsViewModel,
                    workspaceViewModel = workspaceViewModel,
                    drawerState = drawerState,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToTerminal = { navController.navigate("terminal") },
                    onNavigateToGit = { navController.navigate("git") }
                )
            }
            composable("settings") {
                val settingsVm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsVm,
                    onNavigateBack = { 
                        navController.popBackStack() 
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable("terminal") {
                val terminalViewModel: TerminalViewModel = hiltViewModel()
                TerminalScreen(
                    viewModel = terminalViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("git") {
                val gitViewModel: GitViewModel = hiltViewModel()
                GitScreen(
                    viewModel = gitViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
