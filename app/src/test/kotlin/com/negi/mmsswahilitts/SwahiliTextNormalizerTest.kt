package com.negi.mmsswahilitts

import org.junit.Assert.assertEquals
import org.junit.Test

class SwahiliTextNormalizerTest {
    private val normalizer = SwahiliTextNormalizer(("habari HABARI ").toSet())

    @Test
    fun removesPunctuationAndUnsupportedCharacters() {
        assertEquals("Habari habari", normalizer.normalize("Habari, habari! 123"))
    }

    @Test
    fun chunksAtWordBoundary() {
        assertEquals(
            listOf("habari habari", "habari"),
            normalizer.chunks("habari habari habari", maxCharacters = 14),
        )
    }
}
