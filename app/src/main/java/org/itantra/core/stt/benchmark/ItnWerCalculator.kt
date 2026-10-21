package org.itantra.core.stt.benchmark

/**
 * Secondary diagnostic WER/CER calculator with Inverse Text Normalization (ITN) awareness.
 *
 * LABEL: ITN-AWARE / SECONDARY DIAGNOSTIC
 *
 * NOTE: This is an analytical tool and does NOT modify or replace the official strict [WerCalculator] baseline.
 */
object ItnWerCalculator {

    /**
     * Evaluates reference against hypothesis text applying secondary ITN normalization before alignment.
     *
     * @param reference Ground truth reference transcript.
     * @param hypothesis ASR transcribed hypothesis.
     * @return [WerResult] containing secondary diagnostic metrics.
     */
    fun calculate(
        reference: String,
        hypothesis: String
    ): WerResult {
        val itnRef = ItnTextNormalizer.normalize(reference)
        val itnHyp = ItnTextNormalizer.normalize(hypothesis)

        return WerCalculator.calculate(
            reference = itnRef,
            hypothesis = itnHyp,
            normalize = false
        )
    }
}
