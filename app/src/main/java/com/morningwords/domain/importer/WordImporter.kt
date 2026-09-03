package com.morningwords.domain.importer

import java.util.Locale

enum class ImportLineStatus { VALID, DUPLICATE, NEEDS_REVIEW, ERROR }

data class ImportLine(
    val lineNumber: Int,
    val rawText: String,
    val word: String?,
    val normalizedWord: String?,
    val partOfSpeech: String? = null,
    val meaning: String? = null,
    val requiredMeaning: String? = null,
    val example: String? = null,
    val status: ImportLineStatus,
    val reason: String? = null,
)

data class ImportPreview(val lines: List<ImportLine>) {
    val accepted get() = lines.filter { it.status == ImportLineStatus.VALID || it.status == ImportLineStatus.NEEDS_REVIEW }
    val duplicateCount get() = lines.count { it.status == ImportLineStatus.DUPLICATE }
    val errorCount get() = lines.count { it.status == ImportLineStatus.ERROR }
}

object WordImporter {
    private val wordPattern = Regex("^[A-Za-z][A-Za-z'’-]*(?:\\s+[A-Za-z][A-Za-z'’-]*)*$")
    private val leadingPhrasePattern = Regex("^[A-Za-z][A-Za-z'’-]*(?:\\s+[A-Za-z][A-Za-z'’-]*)*")
    private val whitespacePattern = Regex("\\s+")
    private const val POS_TOKEN = "adj|adv|prep|conj|pron|num|art|det|aux|modal|interj|int|abbr|phr|vt|vi|n|v"
    private val posPattern = Regex(
        "^((?:$POS_TOKEN)\\.?(?:\\s*/\\s*(?:$POS_TOKEN)\\.?)*)(?:\\s+|(?=[\\u3400-\\u9FFF（(])|$)",
        RegexOption.IGNORE_CASE,
    )
    // Text copied from textbooks can omit the space between Chinese and English.
    private val exampleBoundaryPattern = Regex("(?<=[\\u3400-\\u9FFF，；。！？）])\\s*(?=[A-Za-z0-9\"“‘'…\\.])|\\s+")
    private val hanPattern = Regex("[\\u3400-\\u9FFF]")
    private val pageReferencePattern = Regex("\\s*[（(][Pp]\\.?\\s*\\d+(?:\\s*[-–]\\s*\\d+)?[）)]$")
    private val exampleStartPattern = Regex("^[\"“‘'…]*(?:\\.{3})?[A-Za-z0-9]")
    private val englishWordPattern = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")
    private val sentenceEndPattern = Regex("[.!?…][\"'’”)]*$")

    fun normalize(value: String): String = value.trim().replace(whitespacePattern, " ")
        .replace('’', '\'').lowercase(Locale.ROOT)

    private fun extractEntry(raw: String): String {
        // A POS marker or an explicit column separator ends the entry. Otherwise,
        // keep the entire English phrase preceding the Chinese definition.
        whitespacePattern.findAll(raw).forEach { boundary ->
            val prefix = raw.substring(0, boundary.range.first)
            val remainder = raw.substring(boundary.range.last + 1)
            if (wordPattern.matches(prefix) && posPattern.containsMatchIn(remainder)) return prefix
        }
        val phrase = leadingPhrasePattern.find(raw)?.value ?: return raw.substringBefore(' ')
        // Tabs can separate an entry from an English-only definition. Spaces
        // within a phrase (including repeated spaces) are normalized for deduplication.
        return phrase.substringBefore('\t').trim()
    }

    fun parseText(content: String): ImportPreview {
        val seen = mutableSetOf<String>()
        val lines = content.lines().mapIndexedNotNull { index, original ->
            val raw = original.trim()
            if (raw.isEmpty()) return@mapIndexedNotNull null
            val entry = extractEntry(raw)
            val candidate = entry.replace(whitespacePattern, " ")
            val next = raw.getOrNull(entry.length)
            val hasEntryBoundary = next == null || next.isWhitespace() || next in '\u3400'..'\u9FFF' || next in "（("
            if (!wordPattern.matches(candidate) || !hasEntryBoundary) {
                return@mapIndexedNotNull ImportLine(index + 1, original, null, null, status = ImportLineStatus.ERROR, reason = "未识别到英文单词或短语")
            }
            val normalized = normalize(candidate)
            if (!seen.add(normalized)) {
                return@mapIndexedNotNull ImportLine(index + 1, original, candidate, normalized, status = ImportLineStatus.DUPLICATE, reason = "批次内重复")
            }
            val tail = raw.removePrefix(entry).trim()
            val posMatch = posPattern.find(tail)
            val rawPos = posMatch?.groupValues?.get(1)?.replace(Regex("\\s*/\\s*"), "/")
            val pos = rawPos?.let { if ('/' in it) it else it.removeSuffix(".") }?.takeIf { it.isNotBlank() }
            val definition = if (posMatch == null) tail else tail.substring(posMatch.range.last + 1).trim()
            val (meaning, example) = splitMeaningAndExample(definition)
            ImportLine(
                lineNumber = index + 1,
                rawText = original,
                word = candidate,
                normalizedWord = normalized,
                partOfSpeech = pos,
                meaning = meaning,
                requiredMeaning = meaning,
                example = example,
                status = if (tail.isBlank() || meaning != null) ImportLineStatus.VALID else ImportLineStatus.NEEDS_REVIEW,
            )
        }
        return ImportPreview(lines)
    }

    private fun splitMeaningAndExample(value: String): Pair<String?, String?> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null to null
        exampleBoundaryPattern.findAll(trimmed).forEach { boundary ->
            val meaning = trimmed.substring(0, boundary.range.first).trim()
            val example = trimmed.substring(boundary.range.last + 1).trim()
            val sentence = example.replace(pageReferencePattern, "").trim()
            // Keep citations in the example, but do not require full-sentence punctuation:
            // textbook examples also include headings, fragments, numbers and quotations.
            val isEnglishExample = exampleStartPattern.containsMatchIn(sentence) &&
                !hanPattern.containsMatchIn(sentence) &&
                (englishWordPattern.findAll(sentence).take(2).count() == 2 ||
                    (englishWordPattern.containsMatchIn(sentence) &&
                        (sentenceEndPattern.containsMatchIn(sentence) || pageReferencePattern.containsMatchIn(example))))
            if (hanPattern.containsMatchIn(meaning) && isEnglishExample) {
                return meaning to example
            }
        }
        return trimmed to null
    }
}
