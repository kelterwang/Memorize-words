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
    private val wordPattern = Regex("^[A-Za-z][A-Za-z'’-]*(?:\\s+[A-Za-z][A-Za-z'’-]*)?$")
    private const val POS_TOKEN = "adj|adv|prep|conj|pron|num|art|det|aux|modal|interj|int|abbr|phr|vt|vi|n|v"
    private val posPattern = Regex(
        "^((?:$POS_TOKEN)\\.?(?:\\s*/\\s*(?:$POS_TOKEN)\\.?)*)(?:\\s+|$)",
        RegexOption.IGNORE_CASE,
    )
    private val whitespacePattern = Regex("\\s+")
    private val hanPattern = Regex("[\\u3400-\\u9FFF]")
    private val englishSentencePattern = Regex("^[A-Z][A-Za-z'’]*[,;:]?(?:\\s+\\S+)+[.!?][\\\"’”)]?$")

    fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun parseText(content: String): ImportPreview {
        val seen = mutableSetOf<String>()
        val lines = content.lines().mapIndexedNotNull { index, original ->
            val raw = original.trim()
            if (raw.isEmpty()) return@mapIndexedNotNull null
            val firstWhitespace = raw.indexOfFirst(Char::isWhitespace)
            val candidate = if (firstWhitespace < 0) raw else raw.substring(0, firstWhitespace)
            if (!wordPattern.matches(candidate)) {
                return@mapIndexedNotNull ImportLine(index + 1, original, null, null, status = ImportLineStatus.ERROR, reason = "未识别到英文单词")
            }
            val normalized = normalize(candidate)
            if (!seen.add(normalized)) {
                return@mapIndexedNotNull ImportLine(index + 1, original, candidate, normalized, status = ImportLineStatus.DUPLICATE, reason = "批次内重复")
            }
            val tail = raw.removePrefix(candidate).trim()
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
        whitespacePattern.findAll(trimmed).forEach { boundary ->
            val meaning = trimmed.substring(0, boundary.range.first).trim()
            val example = trimmed.substring(boundary.range.last + 1).trim()
            if (hanPattern.containsMatchIn(meaning) && englishSentencePattern.matches(example)) {
                return meaning to example
            }
        }
        return trimmed to null
    }
}
