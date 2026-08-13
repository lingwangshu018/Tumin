package me.rerere.rikkahub.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.R

enum class OfficialBunnyPose {
    IDLE,
    SIT,
    WALK,
    HAPPY,
    HEART,
    BACK,
}

data class BunnyAnimationSpec(
    @param:DrawableRes val frames: List<Int>,
    val frameDurationMillis: Long,
)

fun PetMotion.toOfficialBunnyPose(): OfficialBunnyPose = when (this) {
    PetMotion.IDLE -> OfficialBunnyPose.IDLE
    PetMotion.GENTLE -> OfficialBunnyPose.SIT
    PetMotion.APPROACH -> OfficialBunnyPose.WALK
    PetMotion.AFFECTIONATE -> OfficialBunnyPose.HAPPY
    PetMotion.STAY_CLOSE -> OfficialBunnyPose.HEART
    PetMotion.CAUTIOUS -> OfficialBunnyPose.BACK
}

fun OfficialBunnyPose.animationSpec(): BunnyAnimationSpec = when (this) {
    OfficialBunnyPose.IDLE -> BunnyAnimationSpec(
        frames = listOf(R.drawable.pet_bunny_idle_1, R.drawable.pet_bunny_idle_2),
        frameDurationMillis = 850L,
    )
    OfficialBunnyPose.SIT -> BunnyAnimationSpec(
        frames = listOf(R.drawable.pet_bunny_sit_1, R.drawable.pet_bunny_sit_2),
        frameDurationMillis = 780L,
    )
    OfficialBunnyPose.WALK -> BunnyAnimationSpec(
        frames = listOf(
            R.drawable.pet_bunny_walk_right_1,
            R.drawable.pet_bunny_walk_right_2,
            R.drawable.pet_bunny_walk_right_3,
        ),
        frameDurationMillis = 190L,
    )
    OfficialBunnyPose.HAPPY -> BunnyAnimationSpec(
        frames = listOf(R.drawable.pet_bunny_happy_1, R.drawable.pet_bunny_happy_2),
        frameDurationMillis = 330L,
    )
    OfficialBunnyPose.HEART -> BunnyAnimationSpec(
        frames = listOf(R.drawable.pet_bunny_heart_1, R.drawable.pet_bunny_heart_2),
        frameDurationMillis = 520L,
    )
    OfficialBunnyPose.BACK -> BunnyAnimationSpec(
        frames = listOf(R.drawable.pet_bunny_back_1, R.drawable.pet_bunny_back_2),
        frameDurationMillis = 900L,
    )
}

@Composable
fun OfficialBunnyPet(
    presentation: PetPresentation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
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

    val transition = rememberInfiniteTransition(label = "official-bunny")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = when (pose) {
            OfficialBunnyPose.WALK -> -2.5f
            OfficialBunnyPose.HAPPY -> -3.5f
            OfficialBunnyPose.HEART -> -2f
            OfficialBunnyPose.BACK -> -0.7f
            else -> -1.2f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (pose) {
                    OfficialBunnyPose.HAPPY -> 620
                    OfficialBunnyPose.WALK -> 570
                    else -> 1450
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bunny-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.992f,
        targetValue = if (pose == OfficialBunnyPose.BACK) 1f else 1.008f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1550),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bunny-pulse",
    )

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(animation.frames[frameIndex]),
            contentDescription = "兔眠兔",
            modifier = Modifier
                .size(112.dp)
                .offset(y = bob.dp)
                .scale(pulse),
        )
    }
}
