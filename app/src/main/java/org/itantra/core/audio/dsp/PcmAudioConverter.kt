package org.itantra.core.audio.dsp

/**
 * Utility for converting audio formats safely.
 */
object PcmAudioConverter {

    /**
     * Converts floating-point PCM audio samples in range [-1.0f, 1.0f]
     * to 16-bit signed PCM samples in range [-32768, 32767].
     *
     * Safely clamps any out-of-bounds samples to prevent integer overflow clipping distortion.
     */
    fun floatToPcm16(samples: FloatArray): ShortArray {
        val pcm16 = ShortArray(samples.size)
        for (i in samples.indices) {
            val clamped = samples[i].coerceIn(-1.0f, 1.0f)
            pcm16[i] = (clamped * 32767.0f).toInt().toShort()
        }
        return pcm16
    }
}
