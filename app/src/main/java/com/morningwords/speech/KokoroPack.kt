package com.morningwords.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Collections

class KokoroPack(private val context: Context) {
    val directory = File(context.noBackupFilesDir, "kokoro-v1")
    val installed: Boolean get() = File(directory, "installed").isFile
    companion object { private val installLock = Any() }
    fun ensureBundled() = synchronized(installLock) {
        if (!installed) {
            val names = context.assets.list("kokoro-bundled")!!.sorted()
            check(names.isNotEmpty()) { "安装包缺少内置语音，请重新安装完整版" }
            val streams = names.map { context.assets.open("kokoro-bundled/$it") }
            SequenceInputStream(Collections.enumeration(streams)).use { install(it) }
        }
    }
    fun install(input: InputStream) = synchronized(installLock) {
        val staging = File(context.noBackupFilesDir, "kokoro-staging")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            val manifest = JSONObject(context.assets.open("kokoro-files.json").bufferedReader().use { it.readText() })
            val hashes = manifest.keys().asSequence().associateWith { manifest.getString(it) }
            extractVoicePack(input, staging, hashes)
            File(staging, "installed").writeText("1")
            if (installed) return@synchronized // The pinned package is immutable; keep an already installed copy.
            directory.deleteRecursively()
            check(staging.renameTo(directory)) { "保存语音包失败，请检查存储空间" }
        } finally { staging.deleteRecursively() }
    }
    fun create(accent: EnglishAccent): OfflineTts {
        check(installed) { "内置语音尚未准备好，请稍后重试" }
        fun path(name: String) = File(directory, name).absolutePath
        return OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            kokoro = OfflineTtsKokoroModelConfig(model = path("model.int8.onnx"), voices = path("voices.bin"),
                tokens = path("tokens.txt"), dataDir = path("espeak-ng-data"),
                lexicon = path(if (accent == EnglishAccent.US) "lexicon-us-en.txt" else "lexicon-gb-en.txt"),
                lang = accent.kokoroLanguage), numThreads = 2,
        )))
    }
}
