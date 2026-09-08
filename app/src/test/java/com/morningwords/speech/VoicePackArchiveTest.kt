package com.morningwords.speech

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoicePackArchiveTest {
    private fun zip(files: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z -> files.forEach { (name, text) -> z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() } }
        return bytes.toByteArray()
    }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun checkArchive(files: Map<String, String>, expected: Map<String, String>, valid: Boolean) {
        val dir = Files.createTempDirectory("voice-pack-test").toFile()
        try {
            val result = runCatching { extractVoicePack(ByteArrayInputStream(zip(files)), dir, expected) }
            assertEquals(valid, result.isSuccess)
            if (valid) files.forEach { (name, text) -> assertEquals(text, dir.resolve(name).readText()) }
        } finally { dir.deleteRecursively() }
    }
    @Test fun validPackExtractsNestedFiles() = checkArchive(mapOf("data/voice" to "voice"), mapOf("data/voice" to hash("voice")), true)
    @Test fun corruptedPackRejected() = checkArchive(mapOf("voice" to "wrong"), mapOf("voice" to hash("correct")), false)
    @Test fun incompletePackRejected() = checkArchive(emptyMap(), mapOf("voice" to hash("correct")), false)
    @Test fun unexpectedFilesRejected() = checkArchive(mapOf("extra" to "data"), emptyMap(), false)
    @Test fun pathTraversalRejectedEvenInManifest() = checkArchive(mapOf("../escape" to "data"), mapOf("../escape" to hash("data")), false)
    @Test fun accentsUseDifferentVoicesAndPhonemizersAtNormalSpeed() {
        assertEquals("US", EnglishAccent.US.locale.country)
        assertEquals("GB", EnglishAccent.UK.locale.country)
        assertEquals("en-us", EnglishAccent.US.kokoroLanguage)
        assertEquals("en", EnglishAccent.UK.kokoroLanguage)
        assertNotEquals(EnglishAccent.US.speakerId, EnglishAccent.UK.speakerId)
        assertEquals(1f, SPEECH_RATE, 0f)
    }
}
