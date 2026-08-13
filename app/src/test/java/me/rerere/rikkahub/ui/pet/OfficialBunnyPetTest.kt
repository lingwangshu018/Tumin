package me.rerere.rikkahub.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialBunnyPetTest {
    @Test
    fun relationshipMotionsMapToStableOfficialBunnyPoses() {
        assertEquals(OfficialBunnyPose.IDLE, PetMotion.IDLE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.SIT, PetMotion.GENTLE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.WALK, PetMotion.APPROACH.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.HAPPY, PetMotion.AFFECTIONATE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.HEART, PetMotion.STAY_CLOSE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.BACK, PetMotion.CAUTIOUS.toOfficialBunnyPose())
    }

    @Test
    fun relationshipPosesUseOfficialMultiFrameAnimations() {
        assertEquals(2, OfficialBunnyPose.IDLE.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.SIT.animationSpec().frames.size)
        assertEquals(3, OfficialBunnyPose.WALK.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.HAPPY.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.HEART.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.BACK.animationSpec().frames.size)
    }

    @Test
    fun walkAnimationIsFasterThanIdleAnimation() {
        assertTrue(
            OfficialBunnyPose.WALK.animationSpec().frameDurationMillis <
                OfficialBunnyPose.IDLE.animationSpec().frameDurationMillis,
        )
    }
}
