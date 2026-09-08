package com.morningwords.speech

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Extract only the pinned package's files, verifying each before accepting the pack. */
internal fun extractVoicePack(input: InputStream, destination: File, expected: Map<String, String>) {
    val seen = mutableSetOf<String>()
    var total = 0L
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            require(!entry.isDirectory && name in expected && seen.add(name)) { "语音包格式不正确，请选择配套 Kokoro ZIP 文件" }
            val target = File(destination, name)
            require(target.canonicalPath.startsWith(destination.canonicalPath + File.separator)) { "语音包路径无效" }
            target.parentFile!!.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 512L * 1024 * 1024) { "语音包过大" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) } == expected[name]) { "语音包校验失败，请重新下载" }
        }
    }
    require(seen == expected.keys) { "语音包文件不完整，请重新下载" }
}
