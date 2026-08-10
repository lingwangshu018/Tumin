/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.di

import kotlinx.serialization.json.Json
import me.rerere.highlight.Highlighter
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.ai.AILoggingManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.data.service.MemoryBankService
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.utils.EmojiData
import me.rerere.rikkahub.utils.EmojiUtils
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.SoundEffectPlayer
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        Highlighter(get())
    }

    single {
        AppEventBus()
    }

    // Workflows: AgentRun ledger (no-op stub), repository, engine, trigger registry.
    single { me.rerere.rikkahub.data.agentrun.AgentRunRepository() }
    single {
        me.rerere.rikkahub.data.repository.SshHostRepository(
            get<me.rerere.rikkahub.data.db.AppDatabase>().sshHostDao()
        )
    }
    single {
        me.rerere.rikkahub.workflow.repository.WorkflowRepository(
            workflowDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowDao(),
            workflowRunDao = get<me.rerere.rikkahub.data.db.AppDatabase>().workflowRunDao(),
        )
    }
    single { me.rerere.rikkahub.workflow.condition.ContextProvider(get(), get(), get()) }
    single { me.rerere.rikkahub.workflow.execution.WorkflowActionRunner() }
    single {
        me.rerere.rikkahub.workflow.execution.WorkflowEngine(
            repository = get(),
            settingsStore = get(),
            contextProvider = get(),
            actionRunner = get(),
            auditRepo = get(),
        ).also { engine ->
            get<me.rerere.rikkahub.workflow.repository.WorkflowRepository>().bindEngine(engine)
        }
    }
    single {
        me.rerere.rikkahub.workflow.trigger.TriggerRegistry(
            context = get(),
            appScope = get(),
            workflowRepository = get(),
        )
    }

    single {
        LocalTools(get(), get(), get(), get(), get())
    }

    // 微信 Bot (iLink 协议) HTTP 客户端
    single { me.rerere.rikkahub.data.weixin.WeixinBotClient(get()) }

    // QQ Bot (API v2) HTTP 客户端 + WebSocket 共用同一个 OkHttpClient
    single { me.rerere.rikkahub.data.qq.QqBotClient(get()) }

    single {
        me.rerere.rikkahub.data.ai.tools.ToolSurfaceBuilder(
            context = get(),
            localTools = get(),
            toolSurfaceBuilder = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            pluginToolProvider = get(),
            workspaceRepository = get(),
            json = get(),
            memoryRepository = get(),
            coupleRepository = get(),
        )
    }

    single {
        UpdateChecker(get())
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        AILoggingManager()
    }

    single {
        MemoryBankService(
            memoryBankDAO = get(),
            okHttpClient = get(),
            context = get()
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationHandler = get(),
            templateTransformer = get(),
            providerManager = get(),
            localTools = get(),
            mcpManager = get(),
            filesManager = get(),
            skillManager = get(),
            pluginToolProvider = get(),
            pluginLoader = get(),
            workspaceRepository = get(),
            memoryBankService = get(),
            folderRepository = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }

}
