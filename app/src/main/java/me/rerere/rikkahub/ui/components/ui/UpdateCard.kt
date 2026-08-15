/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.dokar.sonner.ToastType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Download01
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useThrottle
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.UpdateDownload
import me.rerere.rikkahub.utils.UpdateInfo
import org.koin.compose.koinInject

/**
 * 关于页里的固定“更新”中心。
 *
 * - 手动检查：始终由用户点击触发；最新版会明确反馈。
 * - 自动检查：开关独立持久化；启动时每天最多检查一次。
 */
@Composable
fun UpdateCard(updateChecker: UpdateChecker = koinInject()) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var latestInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var autoCheckEnabled by remember {
        mutableStateOf(updateChecker.isAutoCheckEnabled(context))
    }

    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("更新") },
    ) {
        item(
            onClick = {
                if (checking) return@item
                scope.launch {
                    updateChecker.checkUpdate().collectLatest { state ->
                        when (state) {
                            UiState.Loading -> checking = true
                            is UiState.Success -> {
                                checking = false
                                val info = state.data
                                if (updateChecker.isNewerVersion(info)) {
                                    latestInfo = info
                                    showDetail = true
                                } else {
                                    toaster.show("当前已是最新版", type = ToastType.Success)
                                }
                            }
                            is UiState.Error -> {
                                checking = false
                                toaster.show(
                                    state.error.message ?: "检查更新失败，请稍后重试",
                                    type = ToastType.Error,
                                )
                            }
                        }
                    }
                }
            },
            leadingContent = {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(HugeIcons.Download01, contentDescription = null)
                }
            },
            supportingContent = { Text("检查 GitHub 上的最新版本") },
            headlineContent = {
                Text(if (checking) "正在检查更新…" else "检查更新")
            },
        )

        item(
            leadingContent = {
                Icon(HugeIcons.Download01, contentDescription = null)
            },
            supportingContent = { Text("启动时检查（每天最多一次）") },
            trailingContent = {
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = { enabled ->
                        autoCheckEnabled = enabled
                        updateChecker.setAutoCheckEnabled(context, enabled)
                    },
                )
            },
            headlineContent = { Text("自动检查更新") },
        )
    }

    val info = latestInfo
    if (showDetail && info != null) {
        val downloadHandler = useThrottle<UpdateDownload>(500) { item ->
            updateChecker.downloadUpdate(context, item)
            showDetail = false
            toaster.show("已开始下载 ${item.name}", type = ToastType.Info)
        }
        ModalBottomSheet(
            onDismissRequest = { showDetail = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "发现新版本 ${info.version}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "选择 APK 下载。下载完成后，可从系统下载通知或“下载”目录打开安装包。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MarkdownBlock(
                    content = info.changelog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (info.downloads.isEmpty()) {
                    Text(
                        text = "这个 Release 暂时没有可下载的 APK。",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    info.downloads.fastForEach { downloadItem ->
                        OutlinedCard(onClick = { downloadHandler(downloadItem) }) {
                            ListItem(
                                headlineContent = { Text(downloadItem.name) },
                                supportingContent = { Text(downloadItem.size) },
                                leadingContent = {
                                    Icon(HugeIcons.Download01, contentDescription = null)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
