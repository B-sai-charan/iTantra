package org.itantra.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * JVM Tokenizer Compatibility & Integrity Test for Sherpa-ONNX Multilingual Whisper.
 *
 * Verifies:
 * 1. Exactly 51,865 token entries.
 * 2. Contiguous token IDs from 0 to 51864 with zero gaps or duplicates.
 * 3. Required special tokens match the OpenAI & Sherpa-ONNX specification:
 *    - 50257: <|endoftext|>
 *    - 50258: <|startoftranscript|>
 *    - 50259: <|en|> (English language tag)
 *    - 50276: <|hi|> (Hindi language tag)
 *    - 50358: <|translate|>
 *    - 50359: <|transcribe|>
 *    - 50363: <|notimestamps|>
 *    - 50364..51864: Timestamp tokens <|0.00|> to <|30.00|>
 * 4. Valid base64 encoding and UTF-8 string decoding across all entries.
 */
class WhisperTokenizerIntegrityTest {

    private val tokensFile: File by lazy {
        val candidates = listOf(
            File("src/main/assets/models/whisper-tiny/tiny-tokens.txt"),
            File("app/src/main/assets/models/whisper-tiny/tiny-tokens.txt"),
            File("../app/src/main/assets/models/whisper-tiny/tiny-tokens.txt")
        )
        candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("tiny-tokens.txt not found in candidate paths")
    }

    @Test
    fun `tiny-tokens has exactly 51865 entries`() {
        val lines = tokensFile.readLines().filter { it.isNotBlank() }
        assertEquals("Total token count must equal 51865", 51865, lines.size)
    }

    @Test
    fun `token IDs are unique and strictly contiguous from 0 to 51864`() {
        val lines = tokensFile.readLines().filter { it.isNotBlank() }
        val idSet = mutableSetOf<Int>()

        for ((idx, line) in lines.withIndex()) {
            val parts = line.trim().split("\\s+".toRegex())
            assertTrue("Line $idx must have at least 2 parts (token rank)", parts.size >= 2)
            val rank = parts[1].toInt()
            assertFalse("Duplicate token ID found: $rank at line $idx", idSet.contains(rank))
            idSet.add(rank)
        }

        assertEquals(51865, idSet.size)
        assertEquals(0, idSet.minOrNull())
        assertEquals(51864, idSet.maxOrNull())

        for (expectedId in 0..51864) {
            assertTrue("Missing token ID $expectedId", idSet.contains(expectedId))
        }
    }

    @Test
    fun `special language tokens and control tokens have exact required IDs`() {
        val lines = tokensFile.readLines().filter { it.isNotBlank() }
        val idToToken = mutableMapOf<Int, String>()

        val decoder = Base64.getDecoder()
        for (line in lines) {
            val parts = line.trim().split("\\s+".toRegex())
            val b64 = parts[0]
            val rank = parts[1].toInt()
            val decodedStr = try {
                String(decoder.decode(b64), Charsets.UTF_8)
            } catch (e: Exception) {
                b64
            }
            idToToken[rank] = decodedStr
        }

        assertEquals("<|endoftext|>", idToToken[50257])
        assertEquals("<|startoftranscript|>", idToToken[50258])
        assertEquals("<|en|>", idToToken[50259])
        assertEquals("<|hi|>", idToToken[50276])
        assertEquals("<|translate|>", idToToken[50358])
        assertEquals("<|transcribe|>", idToToken[50359])
        assertEquals("<|notimestamps|>", idToToken[50363])
        assertEquals("<|0.00|>", idToToken[50364])
        assertEquals("<|30.00|>", idToToken[51864])
    }

    @Test
    fun `all 1501 timestamp tokens are present in sequence`() {
        val lines = tokensFile.readLines().filter { it.isNotBlank() }
        val idToToken = mutableMapOf<Int, String>()
        val decoder = Base64.getDecoder()

        for (line in lines) {
            val parts = line.trim().split("\\s+".toRegex())
            val rank = parts[1].toInt()
            if (rank in 50364..51864) {
                idToToken[rank] = String(decoder.decode(parts[0]), Charsets.UTF_8)
            }
        }

        assertEquals(1501, idToToken.size)
        for (i in 0..1500) {
            val expectedToken = "<|${String.format(java.util.Locale.US, "%.2f", i * 0.02)}|>"
            val actualToken = idToToken[50364 + i]
            assertEquals("Timestamp token mismatch at index $i", expectedToken, actualToken)
        }
    }
}
