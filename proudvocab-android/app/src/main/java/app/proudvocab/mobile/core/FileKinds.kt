package app.proudvocab.mobile.core

/** Classifies picked files by name/MIME so one picker can be used for "video + subtitle". */
object FileKinds {
    private val SUBTITLE_EXT = setOf("srt", "vtt", "ass", "ssa")
    private val MEDIA_EXT = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp", "3g2", "ts", "m2ts", "mts", "flv", "wmv", "mpg", "mpeg", "ogv",
        "mp3", "m4a", "aac", "ogg", "opus", "wav", "flac",
    )

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isSubtitle(name: String): Boolean = extension(name) in SUBTITLE_EXT

    fun isMedia(name: String, mime: String?): Boolean {
        if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) return true
        return extension(name) in MEDIA_EXT
    }

    fun stripExtension(name: String): String {
        val i = name.lastIndexOf('.')
        return if (i > 0) name.substring(0, i) else name
    }
}
