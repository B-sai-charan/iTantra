package org.itantra.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AudioCaptureStateTest {

    @Test
    fun testInitialStateValues() {
        val states = AudioCaptureState.values()
        assertEquals(6, states.size)
        assertEquals(AudioCaptureState.UNINITIALIZED, AudioCaptureState.valueOf("UNINITIALIZED"))
        assertEquals(AudioCaptureState.INITIALIZED, AudioCaptureState.valueOf("INITIALIZED"))
        assertEquals(AudioCaptureState.RECORDING, AudioCaptureState.valueOf("RECORDING"))
        assertEquals(AudioCaptureState.STOPPED, AudioCaptureState.valueOf("STOPPED"))
        assertEquals(AudioCaptureState.RELEASED, AudioCaptureState.valueOf("RELEASED"))
        assertEquals(AudioCaptureState.ERROR, AudioCaptureState.valueOf("ERROR"))
    }
}
