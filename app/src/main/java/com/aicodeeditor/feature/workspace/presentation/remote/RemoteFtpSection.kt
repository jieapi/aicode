package com.aicodeeditor.feature.workspace.presentation.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicodeeditor.core.theme.Brand
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@Composable
fun WiFiFtpServerSection(viewModel: RemoteServerViewModel) {
    val isRunning by viewModel.ftpServerManager.isRunning.collectAsStateWithLifecycle()
    val serverUrl by viewModel.ftpServerManager.serverUrl.collectAsStateWithLifecycle()
    val port by viewModel.ftpServerManager.port.collectAsStateWithLifecycle()
    val username by viewModel.ftpServerManager.username.collectAsStateWithLifecycle()
    val password by viewModel.ftpServerManager.password.collectAsStateWithLifecycle()
    val isAnonymous by viewModel.ftpServerManager.isAnonymous.collectAsStateWithLifecycle()
    val autoStart by viewModel.ftpServerManager.autoStart.collectAsStateWithLifecycle()
    val errorMessage by viewModel.ftpServerManager.errorMessage.collectAsStateWithLifecycle()

    var editPort by remember(port) { mutableStateOf(port.toString()) }
    var editUsername by remember(username) { mutableStateOf(username) }
    var editPassword by remember(password) { mutableStateOf(password) }
    var editAnonymous by remember(isAnonymous) { mutableStateOf(isAnonymous) }
    var editAutoStart by remember(autoStart) { mutableStateOf(autoStart) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    FeatherIcons.Info,
                    contentDescription = null,
                    tint = Brand.IconGray,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1. 手机与电脑需连接至同一 WiFi 局域网。\n2. 开启服务后，在电脑的文件资源管理器（或 FileZilla）地址栏访问下方 FTP 地址。\n3. 连接成功后，即可在电脑端直接查看并编辑手机工作区根目录（projects）下的全部代码项目。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            FeatherIcons.Share2,
                            contentDescription = null,
                            tint = if (isRunning) androidx.compose.ui.graphics.Color(0xFF4CAF50) else Brand.IconGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "FTP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRunning) "运行中: $serverUrl" else "服务未开启",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isRunning) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Switch(
                        checked = isRunning,
                        onCheckedChange = { viewModel.toggleFtpServer() }
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "错误: $errorMessage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        FeatherIcons.Settings,
                        contentDescription = null,
                        tint = Brand.IconGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "服务参数配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = editPort,
                    onValueChange = { editPort = it.filter { char -> char.isDigit() } },
                    label = { Text("监听端口") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    label = { Text("登录用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous
                )

                OutlinedTextField(
                    value = editPassword,
                    onValueChange = { editPassword = it },
                    label = { Text("登录密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editAnonymous
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "允许匿名访问", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "无需输入用户名和密码即可连接", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAnonymous, onCheckedChange = { editAnonymous = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "开机自启", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "应用启动时后台自动运行 FTP 服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = editAutoStart, onCheckedChange = { editAutoStart = it })
                }

                Button(
                    onClick = {
                        val p = editPort.toIntOrNull() ?: 2121
                        viewModel.saveFtpServerConfig(p, editUsername, editPassword, editAnonymous, editAutoStart)
                        android.widget.Toast.makeText(context, "配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("保存配置")
                }
            }
        }
    }
}
