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

private enum class CompanionIntent { COUPLE_SPACE, DIARY, ANNIVERSARY, MEMO, CALENDAR, READING, MUSIC }

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
            ?.parts?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("\n") { it.text }.orEmpty()
        val companionIntent = detectCompanionIntent(latestUserText)

        // HARD ROUTE: once a companion intent is detected, expose ONLY that surface's tools.
        // Do not expose memory/web/local/system/workspace/skill/MCP/plugin tools in the same turn.
        // This deliberately removes tool-choice competition (e.g. HTMLCSStoImage stealing a
        // Rabbit Space request) and makes the model choose among only the actions that can
        // actually complete the requested in-app operation.
        if (companionIntent != null) {
            return when (companionIntent) {
                CompanionIntent.COUPLE_SPACE -> listOf(
                    readCoupleSpaceTool(coupleRepository, invocationContext),
                    postCoupleSpaceTool(coupleRepository, invocationContext),
                    commentCoupleSpaceTool(coupleRepository, invocationContext),
                    deleteCoupleSpacePostTool(coupleRepository, invocationContext),
                )
                CompanionIntent.DIARY -> listOf(sharedDiaryTool(coupleRepository, invocationContext))
                CompanionIntent.ANNIVERSARY -> listOf(anniversaryBookTool(coupleRepository, invocationContext))
                CompanionIntent.MEMO -> listOf(lifeMemoTool(context, invocationContext))
                CompanionIntent.CALENDAR -> listOf(lifeCalendarTool(context, invocationContext))
                CompanionIntent.READING -> listOf(sharedReadingTool(context, invocationContext))
                CompanionIntent.MUSIC -> listOf(sharedMusicTool(context, invocationContext))
            }
        }

        return buildList {
            if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) MemoryRepository.GLOBAL_MEMORY_ID else assistant.id.toString()
                addAll(buildMemoryTools(
                    json = json,
                    onCreation = { content -> memoryRepository.addMemory(memoryAssistantId, content) },
                    onUpdate = { id, content -> memoryRepository.updateContent(id, content) },
                    onDelete = { id -> memoryRepository.deleteMemory(id) },
                ))
            }
            if (settings.enableWebSearch) addAll(createSearchTools(settings))
            addAll(localTools.getTools(assistant.localTools, invocationContext))
            val systemToolsOptions = settings.systemToolsSetting.getEnabledOptions()
            if (systemToolsOptions.isNotEmpty()) addAll(SystemTools(context, settings).getTools(systemToolsOptions, recentMessages, filesManager))

            // Ordinary chat keeps companion tools available, but they no longer compete with
            // unrelated tools on an explicitly detected companion-operation turn.
            add(readCoupleSpaceTool(coupleRepository, invocationContext))
            add(postCoupleSpaceTool(coupleRepository, invocationContext))
            add(commentCoupleSpaceTool(coupleRepository, invocationContext))
            add(deleteCoupleSpacePostTool(coupleRepository, invocationContext))
            add(sharedDiaryTool(coupleRepository, invocationContext))
            add(anniversaryBookTool(coupleRepository, invocationContext))
            add(lifeMemoTool(context, invocationContext))
            add(lifeCalendarTool(context, invocationContext))
            add(sharedReadingTool(context, invocationContext))
            add(sharedMusicTool(context, invocationContext))

            addAll(createWorkspaceTools(assistant.workspaceId?.toString(), workspaceRepository, workspaceCwd))
            if (assistant.enabledSkills.isNotEmpty()) addAll(createSkillTools(assistant.enabledSkills, skillManager.listSkills(), skillManager))
            mcpManager.getAllAvailableTools().forEach { (serverId, tool) ->
                add(Tool(
                    name = ToolNaming.buildMcpToolName(serverId, tool.name),
                    description = tool.description ?: "",
                    parameters = { tool.inputSchema },
                    needsApproval = tool.needsApproval,
                    execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                ))
            }
            addAll(pluginToolProvider.getTools())
        }
    }
}
