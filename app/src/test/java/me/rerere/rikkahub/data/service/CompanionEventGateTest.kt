package me.rerere.rikkahub.data.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionEventGateTest {
    @Test
    fun routineGreetingDoesNotSpendExtraRequest() {
        assertFalse(CompanionEventGate.shouldEvaluate("早上好", "早上好，今天也要开心。"))
    }

    @Test
    fun relationshipEventIsEvaluated() {
        assertTrue(CompanionEventGate.shouldEvaluate("对不起，昨天让你生气了", "我还是有点委屈，但愿意和好。"))
    }

    @Test
    fun substantialExchangeIsEvaluatedWithoutKeyword() {
        assertTrue(CompanionEventGate.shouldEvaluate("甲".repeat(120), "乙".repeat(120)))
    }
}
