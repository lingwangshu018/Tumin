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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R

enum class OfficialBunnyPose {
    IDLE,
    SIT,
    WALK,
    HAPPY,
    HEART,
    BACK,
}

fun PetMotion.toOfficialBunnyPose(): OfficialBunnyPose = when (this) {
    PetMotion.IDLE -> OfficialBunnyPose.IDLE
    PetMotion.GENTLE -> OfficialBunnyPose.SIT
    PetMotion.APPROACH -> OfficialBunnyPose.WALK
    PetMotion.AFFECTIONATE -> OfficialBunnyPose.HAPPY
    PetMotion.STAY_CLOSE -> OfficialBunnyPose.HEART
    PetMotion.CAUTIOUS -> OfficialBunnyPose.BACK
}

@DrawableRes
private fun OfficialBunnyPose.drawableRes(): Int = when (this) {
    OfficialBunnyPose.IDLE -> R.drawable.pet_bunny_idle_1
    OfficialBunnyPose.SIT -> R.drawable.pet_bunny_sit_1
    OfficialBunnyPose.WALK -> R.drawable.pet_bunny_walk_right_1
    OfficialBunnyPose.HAPPY -> R.drawable.pet_bunny_happy_1
    OfficialBunnyPose.HEART -> R.drawable.pet_bunny_heart_1
    OfficialBunnyPose.BACK -> R.drawable.pet_bunny_back_1
}

@Composable
fun OfficialBunnyPet(
    presentation: PetPresentation,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val pose = presentation.motion.toOfficialBunnyPose()
    val transition = rememberInfiniteTransition(label = "official-bunny")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = when (pose) {
            OfficialBunnyPose.WALK -> -5f
            OfficialBunnyPose.HAPPY -> -7f
            OfficialBunnyPose.HEART -> -3f
            OfficialBunnyPose.BACK -> -1f
            else -> -2f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (pose) {
                    OfficialBunnyPose.HAPPY -> 520
                    OfficialBunnyPose.WALK -> 680
                    else -> 1350
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bunny-bob",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = if (pose == OfficialBunnyPose.BACK) 1f else 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bunny-pulse",
    )

    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(pose.drawableRes()),
            contentDescription = "兔眠兔",
            modifier = Modifier
                .size(112.dp)
                .offset(y = bob.dp)
                .scale(pulse),
        )
    }
}
