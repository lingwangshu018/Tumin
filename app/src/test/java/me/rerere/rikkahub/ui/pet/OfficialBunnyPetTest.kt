package me.rerere.rikkahub.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialBunnyPetTest {
    @Test
    fun allPetMotionsMapToOfficialBunnyPoses() {
        assertEquals(OfficialBunnyPose.IDLE, PetMotion.IDLE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.SIT, PetMotion.GENTLE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.WALK, PetMotion.APPROACH.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.HAPPY, PetMotion.AFFECTIONATE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.HEART, PetMotion.STAY_CLOSE.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.BACK, PetMotion.CAUTIOUS.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.LOOK, PetMotion.LOOK.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.SLEEP, PetMotion.SLEEP.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.SHY, PetMotion.SHY.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.ANGRY, PetMotion.ANGRY.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.TOUCHED, PetMotion.TOUCHED.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.POKED, PetMotion.POKED.toOfficialBunnyPose())
        assertEquals(OfficialBunnyPose.RECONCILE, PetMotion.RECONCILE.toOfficialBunnyPose())
    }

    @Test
    fun officialBunnyActionsUseExpectedFrameCounts() {
        assertEquals(2, OfficialBunnyPose.IDLE.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.SIT.animationSpec().frames.size)
        assertEquals(3, OfficialBunnyPose.WALK.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.HAPPY.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.HEART.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.BACK.animationSpec().frames.size)
        assertEquals(3, OfficialBunnyPose.LOOK.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.SLEEP.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.SHY.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.ANGRY.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.TOUCHED.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.POKED.animationSpec().frames.size)
        assertEquals(2, OfficialBunnyPose.RECONCILE.animationSpec().frames.size)
    }

    @Test
    fun activeActionsAreFasterThanIdleAnimation() {
        assertTrue(OfficialBunnyPose.WALK.animationSpec().frameDurationMillis < OfficialBunnyPose.IDLE.animationSpec().frameDurationMillis)
        assertTrue(OfficialBunnyPose.POKED.animationSpec().frameDurationMillis < OfficialBunnyPose.IDLE.animationSpec().frameDurationMillis)
        assertTrue(OfficialBunnyPose.TOUCHED.animationSpec().frameDurationMillis < OfficialBunnyPose.IDLE.animationSpec().frameDurationMillis)
    }
}
