package org.itantra.core.communication.protocol

/**
 * Registry of compact 1-byte language identifiers for the M2 wire protocol.
 * Based on Section G of docs/communication/M2_PROTOCOL_SPEC.md.
 *
 * Registered language IDs are the explicitly assigned entries in the protocol registry.
 * Unassigned IDs are reserved/undefined and are not treated as registered languages.
 *
 * @property id 1-byte integer code on the wire (0x00 - 0xFF).
 * @property tag Canonical base BCP-47 / ISO language code.
 */
enum class ProtocolLanguage(val id: Int, val tag: String) {
    UNDEFINED(0x00, "und"),
    ENGLISH(0x01, "en"),
    HINDI(0x02, "hi"),
    TELUGU(0x03, "te"),
    TAMIL(0x04, "ta"),
    MARATHI(0x05, "mr"),
    BENGALI(0x06, "bn"),
    GUJARATI(0x07, "gu"),
    KANNADA(0x08, "kn"),
    MALAYALAM(0x09, "ml"),
    PUNJABI(0x0A, "pa"),
    URDU(0x0B, "ur"),
    ODIA(0x0C, "or"),
    ASSAMESE(0x0D, "as"),
    EXTENDED_BCP47(0xFE, "extended"),
    RESERVED(0xFF, "reserved");

    companion object {
        /** Maximum allowed UTF-8 byte length for extended BCP-47 language tag (UInt8 capacity). */
        const val MAX_EXTENDED_TAG_BYTES: Int = 255

        private val BY_ID = entries.associateBy { it.id }
        private val BY_TAG = entries.associateBy { it.tag.lowercase() }

        /**
         * Resolves a [ProtocolLanguage] from an integer wire ID.
         * Unmapped or unassigned IDs default safely to [UNDEFINED].
         */
        fun fromId(id: Int): ProtocolLanguage {
            return BY_ID[id] ?: UNDEFINED
        }

        /**
         * Resolves a raw language tag into a [ProtocolLanguage] and an optional [extendedLanguageTag].
         *
         * Mapping behavior:
         * - Empty/blank or "und" / "unknown" -> Pair([UNDEFINED], null)
         * - Exact match on registered base tag (e.g. "en", "hi", "te") -> Pair(matching [ProtocolLanguage], null)
         * - Regional/script variants or unregistered tags (e.g. "en-US", "en_GB", "en-GB", "hi-IN", "hi_IN", "fr", "es-ES") -> Pair([EXTENDED_BCP47], rawTag)
         *
         * Preserves the EXACT source string supplied by SpeechMessage for [EXTENDED_BCP47]
         * without trimming, lowercasing, delimiter normalization ("_" to "-"), or mutation.
         *
         * @param rawTag The incoming language string from M1 SpeechMessage.
         * @return Pair containing the [ProtocolLanguage] and the preserved extended tag (or null).
         */
        fun resolve(rawTag: String): Pair<ProtocolLanguage, String?> {
            if (rawTag.isBlank() || rawTag.equals("und", ignoreCase = true) || rawTag.equals("unknown", ignoreCase = true)) {
                return Pair(UNDEFINED, null)
            }

            // 1. Direct match on registered base tag (e.g., "en", "hi", "te")
            val lower = rawTag.lowercase()
            BY_TAG[lower]?.let { registered ->
                if (registered != EXTENDED_BCP47 && registered != RESERVED && registered != UNDEFINED) {
                    return Pair(registered, null)
                }
            }

            // 2. Extended fallback: Retain the EXACT raw tag string verbatim without alteration
            return Pair(EXTENDED_BCP47, rawTag)
        }

        /**
         * Backward-compatible helper returning [ProtocolLanguage].
         * Prefer [resolve] when extended tag preservation is needed.
         */
        fun fromTag(tag: String): ProtocolLanguage = resolve(tag).first
    }
}

