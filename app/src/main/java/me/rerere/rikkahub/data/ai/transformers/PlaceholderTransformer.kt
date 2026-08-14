/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.Locale
import java.util.TimeZone

data class PlaceholderCtx(
    val context: Context,
    val settingsStore: SettingsStore,
    val model: Model,
    val assistant: Assistant,
)

interface PlaceholderProvider {
    val placeholders: Map<String, PlaceholderInfo>
}

data class PlaceholderInfo(
    val displayName: @Composable () -> Unit,
    val resolver: (PlaceholderCtx) -> String
)

class PlaceholderBuilder {
    private val placeholders = mutableMapOf<String, PlaceholderInfo>()

    fun placeholder(
        key: String,
        displayName: @Composable () -> Unit,
        resolver: (PlaceholderCtx) -> String
    ) {
        placeholders[key] = PlaceholderInfo(displayName, resolver)
    }

    fun build(): Map<String, PlaceholderInfo> = placeholders.toMap()
}

fun buildPlaceholders(block: PlaceholderBuilder.() -> Unit): Map<String, PlaceholderInfo> {
    return PlaceholderBuilder().apply(block).build()
}

object DefaultPlaceholderProvider : PlaceholderProvider {
    override val placeholders: Map<String, PlaceholderInfo> = buildPlaceholders {
        placeholder("cur_date", { Text(stringResource(R.string.placeholder_current_date)) }) {
            LocalDate.now().toDateString()
        }

        placeholder("cur_time", { Text(stringResource(R.string.placeholder_current_time)) }) {
            LocalTime.now().toTimeString()
        }

        placeholder("cur_datetime", { Text(stringResource(R.string.placeholder_current_datetime)) }) {
            LocalDateTime.now().toDateTimeString()
        }

        placeholder("model_id", { Text(stringResource(R.string.placeholder_model_id)) }) {
            it.model.modelId
        }

        placeholder("model_name", { Text(stringResource(R.string.placeholder_model_name)) }) {
            it.model.displayName
        }

        placeholder("locale", { Text(stringResource(R.string.placeholder_locale)) }) {
            Locale.getDefault().displayName
        }

        placeholder("timezone", { Text(stringResource(R.string.placeholder_timezone)) }) {
            TimeZone.getDefault().displayName
        }

        placeholder("system_version", { Text(stringResource(R.string.placeholder_system_version)) }) {
            "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
        }

        placeholder("device_info", { Text(stringResource(R.string.placeholder_device_info)) }) {
            "${Build.BRAND} ${Build.MODEL}"
        }

        placeholder("battery_level", { Text(stringResource(R.string.placeholder_battery_level)) }) {
            it.context.batteryLevel().toString()
        }

        placeholder("nickname", { Text(stringResource(R.string.placeholder_nickname)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }

        placeholder("char", { Text(stringResource(R.string.placeholder_char)) }) {
            it.assistant.name.ifBlank { "assistant" }
        }

        placeholder("user", { Text(stringResource(R.string.placeholder_user)) }) {
            it.settingsStore.settingsFlow.value.displaySetting.userNickname.ifBlank { "user" }
        }
    }

    private fun Temporal.toDateString() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toTimeString() = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Temporal.toDateTimeString() = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(this)

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

object PlaceholderTransformer : InputMessageTransformer, KoinComponent {
    private val defaultProvider = DefaultPlaceholderProvider
    private val volatileSystemPlaceholders = setOf(
        "cur_date",
        "cur_time",
        "cur_datetime",
        "battery_level",
    )

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val settingsStore = get<SettingsStore>()
        val placeholderCtx = PlaceholderCtx(
            context = ctx.context,
            settingsStore = settingsStore,
            model = ctx.model,
            assistant = ctx.assistant,
        )
        val dynamicValues = linkedMapOf<String, String>()

        val transformedMessages = messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Text) {
                        val text = if (message.role == MessageRole.SYSTEM) {
                            replaceSystemPlaceholders(
                                text = part.text,
                                ctx = placeholderCtx,
                                dynamicValues = dynamicValues,
                            )
                        } else {
                            replacePlaceholders(text = part.text, ctx = placeholderCtx)
                        }
                        part.copy(text = text)
                    } else {
                        part
                    }
                }
            )
        }

        if (dynamicValues.isEmpty()) return transformedMessages

        val dynamicContext = UIMessage.user(
            buildString {
                appendLine("<dynamic_context>")
                appendLine("Resolve <dynamic_ref key=\"...\"/> references in system instructions using these current values:")
                dynamicValues.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
                append("</dynamic_context>")
            }
        )
        val latestUserIndex = transformedMessages.indexOfLast { it.role == MessageRole.USER }

        return if (latestUserIndex >= 0) {
            transformedMessages.toMutableList().apply {
                add(latestUserIndex, dynamicContext)
            }
        } else {
            transformedMessages + dynamicContext
        }
    }

    private fun replaceSystemPlaceholders(
        text: String,
        ctx: PlaceholderCtx,
        dynamicValues: MutableMap<String, String>,
    ): String {
        var result = text

        defaultProvider.placeholders.forEach { (key, placeholderInfo) ->
            if (!containsPlaceholder(result, key)) return@forEach

            if (key in volatileSystemPlaceholders) {
                dynamicValues[key] = placeholderInfo.resolver(ctx)
                result = replacePlaceholder(
                    text = result,
                    key = key,
                    value = "<dynamic_ref key=\"$key\"/>",
                )
            } else {
                result = replacePlaceholder(
                    text = result,
                    key = key,
                    value = placeholderInfo.resolver(ctx),
                )
            }
        }

        return result
    }

    private fun replacePlaceholders(
        text: String,
        ctx: PlaceholderCtx,
    ): String {
        var result = text

        defaultProvider.placeholders.forEach { (key, placeholderInfo) ->
            if (containsPlaceholder(result, key)) {
                result = replacePlaceholder(
                    text = result,
                    key = key,
                    value = placeholderInfo.resolver(ctx),
                )
            }
        }

        return result
    }

    private fun containsPlaceholder(text: String, key: String): Boolean {
        return text.contains("{{$key}}", ignoreCase = true) ||
            text.contains("{$key}", ignoreCase = true)
    }

    private fun replacePlaceholder(text: String, key: String, value: String): String {
        return text
            .replace(oldValue = "{{$key}}", newValue = value, ignoreCase = true)
            .replace(oldValue = "{$key}", newValue = value, ignoreCase = true)
    }
}
