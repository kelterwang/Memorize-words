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
    private val posPattern = Regex("^(n|v|adj|adv|prep|conj|pron|num|art)\\.?(?:\\s+|$)", RegexOption.IGNORE_CASE)

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
            val pos = posPattern.find(tail)?.value?.trim()?.removeSuffix(".")?.takeIf { it.isNotBlank() }
            val meaning = posPattern.replaceFirst(tail, "").trim().takeIf { it.isNotBlank() }
            ImportLine(
                lineNumber = index + 1,
                rawText = original,
                word = candidate,
                normalizedWord = normalized,
                partOfSpeech = pos,
                meaning = meaning,
                requiredMeaning = meaning,
                status = if (tail.isBlank() || meaning != null) ImportLineStatus.VALID else ImportLineStatus.NEEDS_REVIEW,
            )
        }
        return ImportPreview(lines)
    }
}

