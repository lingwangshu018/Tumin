/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.service.VoiceCallService
import me.rerere.rikkahub.ui.components.ui.toComposeColor
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.rememberSharedPreferenceBoolean
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState
import me.rerere.rikkahub.ui.pet.InAppPetHost

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val colorMode by rememberColorMode()
    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val amoledDarkMode by rememberAmoledDarkMode()

    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val theme = findThemeById(settings.themeId, settings.customThemes)
                ?: findPresetTheme(settings.themeId)
            theme.getColorScheme(dark = darkTheme)
        }
    }
    val colorSchemeConverted = remember(darkTheme, amoledDarkMode, colorScheme) {
        if (darkTheme && amoledDarkMode) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors

    val finalColorScheme = remember(
        colorSchemeConverted,
        settings.displaySetting.primaryColor,
        settings.displaySetting.globalTextColor,
        settings.themeId,
    ) {
        var scheme = colorSchemeConverted
        settings.displaySetting.primaryColor?.let { pc ->
            val primaryColor = pc.toComposeColor()
            val luminance = 0.299f * primaryColor.red + 0.587f * primaryColor.green + 0.114f * primaryColor.blue
            val onPrimary = if (luminance > 0.5f) Color.Black else Color.White
            scheme = scheme.copy(
                primary = primaryColor,
                onPrimary = onPrimary,
            )
        }
        settings.displaySetting.globalTextColor?.let { gtc ->
            val textColor = gtc.toComposeColor()
            scheme = scheme.copy(
                onBackground = textColor,
                onSurface = textColor,
                onSurfaceVariant = textColor,
            )
        }
        if (settings.themeId == "pearltide") {
            val glassAlpha = 0.6f
            scheme = scheme.copy(
                background = scheme.background.copy(alpha = 0f),
                surface = scheme.surface.copy(alpha = glassAlpha),
                surfaceBright = scheme.surfaceBright.copy(alpha = glassAlpha),
                surfaceDim = scheme.surfaceDim.copy(alpha = glassAlpha),
                surfaceContainerLowest = scheme.surfaceContainerLowest.copy(alpha = glassAlpha),
                surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = glassAlpha),
                surfaceContainer = scheme.surfaceContainer.copy(alpha = glassAlpha),
                surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = glassAlpha),
                surfaceContainerHighest = scheme.surfaceContainerHighest.copy(alpha = glassAlpha),
                surfaceVariant = scheme.surfaceVariant.copy(alpha = glassAlpha),
            )
        }
        scheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors,
        LocalOverscrollFactory provides null
    ) {
        MaterialExpressiveTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            motionScheme = MotionScheme.expressive()
        ) {
            val petEnabled by rememberSharedPreferenceBoolean("in_app_pet_enabled", false)
            val activeCall by VoiceCallService.activeConversationId.collectAsState()
            val currentAssistant = settings.getCurrentAssistant()

            Box(modifier = Modifier.fillMaxSize()) {
                content()
                if (petEnabled && currentAssistant != null && activeCall == null) {
                    InAppPetHost(
                        assistant = currentAssistant,
                        visible = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current