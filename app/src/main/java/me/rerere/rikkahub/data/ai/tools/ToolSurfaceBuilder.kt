/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.CoupleRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.plugin.provider.PluginToolProvider

private enum class CompanionIntent {
    COUPLE_SPACE,
    DIARY,
    ANNIVERSARY,
    MEMO,
    CALENDAR,
    READING,
    MUSIC,
}

private fun detectCompanionIntent(text: String): CompanionIntent? {
    if (text.isBlank()) return null
    val t = text.lowercase()
    return when {
        listOf("兔眠空间", "兔眠动态", "qq空间", "qq 空间", "情侣空间", "空间动态", "发动态", "删动态", "删除动态", "评论空间", "看空间", "朋友圈").any(t::contains) -> CompanionIntent.COUPLE_SPACE
        listOf("日记", "回信", "journal").any(t::contains) -> CompanionIntent.DIARY
        listOf("纪念日", "纪念册", "anniversary").any(t::contains) -> CompanionIntent.ANNIVERSARY
        listOf("备忘录", "备忘", "memo", "记一下", "记住这件事").any(t::contains) -> CompanionIntent.MEMO
        listOf("日历", "日程", "安排", "calendar").any(t::contains) -> CompanionIntent.CALENDAR
        listOf("共读", "小说", "批注", "书签", "阅读", "reading").any(t::contains) -> CompanionIntent.READING
        listOf("一起听", "点歌", "音乐", "播放", "暂停", "下一首", "music").any(t::contains) -> CompanionIntent.MUSIC
        else -> null
    }
}

/**
 * Builds the full tool surface for an assistant: search + local + system + workspace + skill
 * + MCP + plugin tools. This is the single source of truth shared by [me.rerere.rikkahub.service.ChatService]
 * (interactive) and [me.rerere.rikkahub.workflow.execution.WorkflowEngine] (headless fire), so a
 * workflow action can reference any tool the assistant actually has registered - not just the
 * local-tool subset. Without this, workflow_create would reject system/MCP/plugin tool names as
 * "unknown_tool" and the engine couldn't execute them at fire time.
 *
 * Headless callers (workflow fire) pass an empty [recentMessages] list and a null/assistant-default
 * [workspaceCwd]; the read-only context tools that consult recent messages simply see no history.
 */
class ToolSurfaceBuilder(
    private val context: Context,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val pluginToolProvider: PluginToolProvider,
    private val workspaceRepository: WorkspaceRepository,
    private val json: Json,
    private val memoryRepository: MemoryRepository,
    private val coupleRepository: CoupleRepository,
) {
    suspend fun build(
        assistant: me.rerere.rikkahub.data.model.Assistant,
        settings: Settings,
        invocationContext: ToolInvocationContext,
        recentMessages: List<UIMessage> = emptyList(),
        workspaceCwd: String? = null,
    ): List<Tool> {
        val latestUserText = recentMessages.asReversed()
            .firstOrNull { it.role == MessageRole.USER }
            ?.parts
            ?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("\n") { it.text }
            .orEmpty()
        val companionIntent = detectCompanionIntent(latestUserText)

        return buildList {
            if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) {
                    MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    assistant.id.toString()
                }
                addAll(buildMemoryTools(
                    json = json,
                    onCreation = { content -> memoryRepository.addMemory(memoryAssistantId, content) },
                    onUpdate = { id, content -> memoryRepository.updateContent(id, content) },
                    onDelete = { id -> memoryRepository.deleteMemory(id) },
                ))
            }
            if (settings.enableWebSearch) {
                addAll(createSearchTools(settings))
            }
            addAll(localTools.getTools(assistant.localTools, invocationContext))
            val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions()
            if (systemToolsOptions.isNotEmpty()) {
                addAll(SystemTools(context, settings).getTools(systemToolsOptions, recentMessages, filesManager))
            }

            // When the latest user request clearly targets a companion surface, expose the exact
            // matching tool(s) first and keep unrelated plugin/MCP/workspace tools out of the tool
            // surface for this turn. This prevents requests such as “更新兔眠空间” from being
            // misrouted to unrelated image/HTML plugins.
            when (companionIntent) {
                CompanionIntent.COUPLE_SPACE -> {
                    add(readCoupleSpaceTool(coupleRepository, invocationContext))
                    add(postCoupleSpaceTool(coupleRepository, invocationContext))
                    add(commentCoupleSpaceTool(coupleRepository, invocationContext))
                    add(deleteCoupleSpacePostTool(coupleRepository, invocationContext))
                    add(deleteCoupleSpacePostTool(coupleRepository, invocationContext))
                }
                CompanionIntent.DIARY -> add(sharedDiaryTool(coupleRepository, invocationContext))
                CompanionIntent.ANNIVERSARY -> add(anniversaryBookTool(coupleRepository, invocationContext))
                CompanionIntent.MEMO -> add(lifeMemoTool(context, invocationContext))
                CompanionIntent.CALENDAR -> add(lifeCalendarTool(context, invocationContext))
                CompanionIntent.READING -> add(sharedReadingTool(context, invocationContext))
                CompanionIntent.MUSIC -> add(sharedMusicTool(context, invocationContext))
                null -> {
                    add(readCoupleSpaceTool(coupleRepository, invocationContext))
                    add(postCoupleSpaceTool(coupleRepository, invocationContext))
                    add(commentCoupleSpaceTool(coupleRepository, invocationContext))
                    add(deleteCoupleSpacePostTool(coupleRepository, invocationContext))
                    add(deleteCoupleSpacePostTool(coupleRepository, invocationContext))
                    add(sharedDiaryTool(coupleRepository, invocationContext))
                    add(anniversaryBookTool(coupleRepository, invocationContext))
                    add(lifeMemoTool(context, invocationContext))
                    add(lifeCalendarTool(context, invocationContext))
                    add(sharedReadingTool(context, invocationContext))
                    add(sharedMusicTool(context, invocationContext))
                }
            }

            // Companion-specific turns intentionally avoid unrelated external tool surfaces. For
            // ordinary chat, preserve the full workspace/skill/MCP/plugin tool surface.
            if (companionIntent == null) {
                addAll(createWorkspaceTools(assistant.workspaceId?.toString(), workspaceRepository, workspaceCwd))
                if (assistant.enabledSkills.isNotEmpty()) {
                    addAll(createSkillTools(assistant.enabledSkills, skillManager.listSkills(), skillManager))
                }
                mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                    add(
                        Tool(
                            name = ToolNaming.buildMcpToolName(serverId, tool.name),
                            description = tool.description ?: "",
                            parameters = { tool.inputSchema },
                            needsApproval = tool.needsApproval,
                            execute = {
                                mcpManager.callTool(serverId, tool.name, it.jsonObject)
                            },
                        )
                    )
                }
                addAll(pluginToolProvider.getTools())
            }
        }
    }
}