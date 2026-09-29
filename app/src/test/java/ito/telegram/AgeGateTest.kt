package ito.telegram

import ito.telegram.adult.AgeGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeGateTest {

    private val fullChallenge = AgeGate.Challenge(blink = true, turn = true, smile = true)
    private val weakChallenge = AgeGate.Challenge(blink = true)

    @Test
    fun `clear adult passes strict mode`() {
        val samples = listOf(28f, 30f, 29f, 31f, 27f, 30f, 29f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Allowed)
        assertEquals(29, (v as AgeGate.Verdict.Allowed).age)
    }

    @Test
    fun `minor is denied`() {
        val samples = listOf(13f, 14f, 12f, 15f, 13f, 14f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Denied)
    }

    @Test
    fun `borderline is denied in strict mode`() {
        val samples = listOf(18f, 18f, 19f, 18f, 18f, 19f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue("در حالت سخت‌گیر مرز ۱۸ نباید قبول شود", v is AgeGate.Verdict.Denied)
    }

    @Test
    fun `missing liveness is inconclusive`() {
        val samples = listOf(30f, 31f, 29f, 30f, 32f, 30f)
        val v = AgeGate.decide(samples, weakChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Inconclusive)
    }

    @Test
    fun `no model means no access`() {
        val samples = listOf(40f, 41f, 39f, 40f, 42f, 40f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 1, modelReady = false)
        assertTrue(v is AgeGate.Verdict.Inconclusive)
    }

    @Test
    fun `multiple faces rejected`() {
        val samples = listOf(40f, 41f, 39f, 40f, 42f, 40f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 2, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Inconclusive)
    }

    @Test
    fun `not enough samples rejected`() {
        val v = AgeGate.decide(listOf(40f, 41f), fullChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Inconclusive)
    }

    @Test
    fun `scattered samples rejected in strict mode`() {
        val samples = listOf(16f, 45f, 20f, 60f, 19f, 35f)
        val v = AgeGate.decide(samples, fullChallenge, strict = true, faces = 1, modelReady = true)
        assertTrue(v is AgeGate.Verdict.Inconclusive)
    }

    @Test
    fun `median and spread are sane`() {
        assertEquals(3f, AgeGate.median(listOf(1f, 3f, 5f)), 0.001f)
        assertEquals(2.5f, AgeGate.median(listOf(1f, 2f, 3f, 4f)), 0.001f)
        assertTrue(AgeGate.spread(listOf(10f, 10f, 10f, 10f)) < 1f)
    }
}
