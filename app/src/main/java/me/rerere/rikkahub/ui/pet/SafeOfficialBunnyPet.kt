package me.rerere.rikkahub.ui.pet

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.R

/**
 * Crash-safe renderer for the official bunny. Some release/device combinations can make
 * BitmapFactory.decodeResource() return null for the optional WebP state atlas. Atlas-backed
 * poses therefore decode lazily and fall back to the normal idle drawable instead of crashing.
 */
@Composable
fun SafeOfficialBunnyPet(
    presentation: PetPresentation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val pose = presentation.motion.toOfficialBunnyPose()
    val animation = remember(pose) { pose.animationSpec() }
    var frameIndex by remember(pose) { mutableIntStateOf(0) }

    LaunchedEffect(pose, animation.frameDurationMillis, animation.frames.size) {
        frameIndex = 0
        while (animation.frames.size > 1) {
            delay(animation.frameDurationMillis)
            frameIndex = (frameIndex + 1) % animation.frames.size
        }
    }

    val transition = rememberInfiniteTransition(label = "safe-official-bunny")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = when (pose) {
            OfficialBunnyPose.WALK -> -2.5f
            OfficialBunnyPose.HAPPY -> -3.5f
            OfficialBunnyPose.HEART -> -2f
            OfficialBunnyPose.TOUCHED -> -4f
            OfficialBunnyPose.POKED -> -2.5f
            OfficialBunnyPose.RECONCILE -> -2f
            OfficialBunnyPose.SLEEP -> -0.4f
            OfficialBunnyPose.BACK -> -0.7f
            else -> -1.2f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (pose) {
                    OfficialBunnyPose.HAPPY -> 620
                    OfficialBunnyPose.WALK -> 570
                    OfficialBunnyPose.TOUCHED -> 420
                    OfficialBunnyPose.POKED -> 320
                    OfficialBunnyPose.ANGRY -> 500
                    else -> 1450
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "safe-bunny-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.992f,
        targetValue = when (pose) {
            OfficialBunnyPose.BACK, OfficialBunnyPose.SLEEP -> 1f
            OfficialBunnyPose.TOUCHED -> 1.018f
            else -> 1.008f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "safe-bunny-pulse",
    )

    val frame = animation.frames[frameIndex]
    val context = LocalContext.current
    val painter = when (frame) {
        is BunnyFrame.Drawable -> painterResource(frame.id)
        is BunnyFrame.Atlas -> {
            val atlasBitmap = remember(context.resources) {
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.pet_bunny_state_atlas,
                    BitmapFactory.Options().apply { inScaled = false },
                )
                    ?.takeIf { bitmap -> bitmap.width >= 480 && bitmap.height >= 288 }
                    ?.asImageBitmap()
            }

            if (atlasBitmap == null) {
                painterResource(R.drawable.pet_bunny_idle_1)
            } else {
                remember(atlasBitmap, frame.index) {
                    val column = frame.index % 5
                    val row = frame.index / 5
                    BitmapPainter(
                        image = atlasBitmap,
                        srcOffset = IntOffset(column * 96, row * 96),
                        srcSize = IntSize(96, 96),
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = "兔眠兔",
            modifier = Modifier
                .size(112.dp)
                .offset(y = bob.dp)
                .scale(pulse),
        )
    }
}
