package me.rerere.rikkahub.ui.pet

import org.junit.Assert.assertEquals
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
}
