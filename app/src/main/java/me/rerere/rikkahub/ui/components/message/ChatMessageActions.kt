/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalDisplaySettings
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.toLocalString
import org.koin.compose.koinInject
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    onTranslate: ((UIMessage, Locale) -> Unit)? = null,
    onClearTranslation: (UIMessage) -> Unit = {},
) {
    val context = LocalContext.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000)
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp)
        )

        Icon(
            imageVector = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    if (message.role == MessageRole.USER) showRegenerateConfirm = true else onRegenerate()
                }
                .padding(8.dp)
                .size(16.dp)
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val displaySettings = LocalDisplaySettings.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                val text = message.toText()
                                val textToSpeak = if (displaySettings.ttsOnlyReadQuoted) {
                                    text.extractQuotedContentAsText() ?: text
                                } else text
                                tts.speak(textToSpeak)
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
            )

            if (onTranslate != null) {
                Icon(
                    imageVector = HugeIcons.Translate,
                    contentDescription = stringResource(R.string.translate),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showTranslateDialog = true }
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
        }

        Icon(
            imageVector = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenActionSheet() }
                .padding(8.dp)
                .size(16.dp)
        )

        ChatMessageBranchSelector(node = node, onUpdate = onUpdate)
    }

    if (showTranslateDialog && onTranslate != null) {
        LanguageSelectionDialog(
            onLanguageSelected = { language ->
                showTranslateDialog = false
                onTranslate(message, language)
            },
            onClearTranslation = {
                showTranslateDialog = false
                onClearTranslation(message)
            },
            onDismissRequest = { showTranslateDialog = false },
        )
    }

    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onEditAndRegenerate: () -> Unit,
    onQuote: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val conversationRepository = koinInject<ConversationRepository>()
    val chatService = koinInject<ChatService>()
    val scope = rememberCoroutineScope()

    var forwardMode by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loadingTargets by remember { mutableStateOf(false) }
    var forwardingTo by remember { mutableStateOf<Uuid?>(null) }
    var forwardError by remember { mutableStateOf<String?>(null) }
    var regenerating by remember { mutableStateOf(false) }

    LaunchedEffect(forwardMode) {
        if (!forwardMode || targets.isNotEmpty()) return@LaunchedEffect
        loadingTargets = true
        forwardError = null
        runCatching {
            settings.assistants
                .flatMap { assistant -> conversationRepository.getConversationsOfAssistant(assistant.id).first() }
                .distinctBy { it.id }
                .sortedByDescending { it.updateAt }
        }.onSuccess {
            targets = it
        }.onFailure {
            forwardError = it.message ?: "读取聊天列表失败"
        }
        loadingTargets = false
    }

    fun locateSourceAndRegenerate() {
        if (regenerating) return
        regenerating = true
        scope.launch {
            val source = runCatching {
                settings.assistants.asSequence().mapNotNull { assistant ->
                    conversationRepository.getRecentConversations(assistant.id, limit = 50)
                        .firstOrNull { conversation ->
                            conversation.messageNodes.any { node -> node.messages.any { it.id == message.id } }
                        }
                }.firstOrNull()
            }.getOrNull()
            if (source != null) {
                chatService.regenerateAtMessage(source.id, message)
                onDismissRequest()
            } else {
                forwardError = "没有找到这条消息所属的聊天，请回到消息下方使用重新生成按钮。"
            }
            regenerating = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        if (forwardMode) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("转发到", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { forwardMode = false }) { Text("返回") }
                }
                Text(
                    "选择一个 Character / Chat。转发只写入目标聊天，不会自动触发对方回复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (loadingTargets) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else if (targets.isEmpty()) {
                    Text(forwardError ?: "暂无可转发的聊天", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(targets, key = { it.id }) { target ->
                            val assistantName = settings.assistants
                                .firstOrNull { it.id == target.assistantId }
                                ?.name
                                .orEmpty()
                                .ifBlank { "Character" }
                            Card(
                                enabled = forwardingTo == null,
                                onClick = {
                                    forwardingTo = target.id
                                    forwardError = null
                                    scope.launch {
                                        runCatching {
                                            val fullTarget = conversationRepository.getConversationById(target.id)
                                                ?: error("目标聊天不存在")
                                            val sourceLabel = if (message.role == MessageRole.USER) {
                                                settings.displaySetting.userNickname.ifBlank { "我" }
                                            } else {
                                                "AI 消息"
                                            }
                                            val text = message.toText().trim()
                                            require(text.isNotBlank()) { "这条消息没有可转发的文字内容" }
                                            val forwarded = UIMessage(
                                                role = MessageRole.USER,
                                                parts = listOf(
                                                    UIMessagePart.Text("【转发自 $sourceLabel】\n$text")
                                                ),
                                            )
                                            conversationRepository.updateConversation(
                                                fullTarget.copy(
                                                    messageNodes = fullTarget.messageNodes + forwarded.toMessageNode(),
                                                    updateAt = Instant.now(),
                                                )
                                            )
                                        }.onSuccess {
                                            onDismissRequest()
                                        }.onFailure {
                                            forwardError = it.message ?: "转发失败"
                                        }
                                        forwardingTo = null
                                    }
                                },
                            ) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(assistantName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        target.title.ifBlank { "未命名聊天" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                forwardError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ActionCard(HugeIcons.Copy01, "复制") {
                context.copyMessageToClipboard(message)
                onDismissRequest()
            }
            ActionCard(HugeIcons.Copy01, "引用") {
                onDismissRequest()
                onQuote()
            }
            ActionCard(HugeIcons.TextSelection, "选择") {
                onDismissRequest()
                onSelectAndCopy()
            }
            ActionCard(HugeIcons.Refresh03, if (regenerating) "正在重新生成…" else "重新生成") {
                locateSourceAndRegenerate()
            }
            ActionCard(HugeIcons.Edit01, "编辑") {
                onDismissRequest()
                onEdit()
            }
            if (message.role == MessageRole.USER) {
                ActionCard(HugeIcons.Refresh03, "编辑并重试") {
                    onDismissRequest()
                    onEditAndRegenerate()
                }
            }
            ActionCard(HugeIcons.Share04, "转发") { forwardMode = true }

            if (onToggleFavorite != null) {
                ActionCard(
                    HugeIcons.FavouriteCircle,
                    stringResource(
                        if (isFavorite) R.string.chat_message_remove_favorite
                        else R.string.chat_message_add_favorite
                    )
                ) {
                    onDismissRequest()
                    onToggleFavorite()
                }
            }

            // Keep the original advanced actions available below the core long-press menu.
            ActionCard(HugeIcons.Share04, stringResource(R.string.share)) {
                onDismissRequest()
                onShare()
            }
            ActionCard(HugeIcons.GitFork, stringResource(R.string.create_fork)) {
                onDismissRequest()
                onFork()
            }

            val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>().any { it.text.isNotBlank() }
            if (hasTextContent) {
                ActionCard(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview)) {
                    onDismissRequest()
                    onWebViewPreview()
                }
            }

            Card(
                onClick = {
                    onDismissRequest()
                    onDelete()
                },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                ) {
                    Icon(HugeIcons.Delete01, contentDescription = null, modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.delete), style = MaterialTheme.typography.titleMedium)
                }
            }

            forwardError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) Text(model.displayName)
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(4.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
