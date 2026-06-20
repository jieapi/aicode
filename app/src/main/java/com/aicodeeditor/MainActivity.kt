package com.aicodeeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aicodeeditor.core.theme.AIEditorTheme
import com.aicodeeditor.feature.agent.presentation.AIAgentViewModel
import com.aicodeeditor.feature.agent.presentation.component.AIChatPanel
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

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            val agentViewModel: AIAgentViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val workspaceViewModel: WorkspaceViewModel = hiltViewModel()
            AIChatPanel(
                viewModel = agentViewModel,
                settingsViewModel = settingsViewModel,
                workspaceViewModel = workspaceViewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToTerminal = { navController.navigate("terminal") },
                
            )
        }
        composable("settings") {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
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
