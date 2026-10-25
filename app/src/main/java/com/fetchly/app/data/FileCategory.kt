package com.fetchly.app.data

/** Groups downloads by file type, for the Types filter in the navigation drawer. */
enum class FileCategory(val label: String) {
    EVERYTHING("Everything"),
    COMPRESSED("Compressed"),
    DOCUMENTS("Documents"),
    MUSIC("Music"),
    VIDEOS("Videos"),
    SUBTITLES("Subtitles"),
    PHOTOS("Photos"),
    PROGRAMS("Programs"),
    OTHERS("Others"),
}

/** Picks a category from a file name's extension. Unknown or missing extensions fall into OTHERS. */
fun categoryOf(fileName: String): FileCategory {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (extension) {
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileCategory.COMPRESSED
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "rtf", "odt", "csv" -> FileCategory.DOCUMENTS
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "wma" -> FileCategory.MUSIC
        "mp4", "mkv", "avi", "mov", "webm", "flv", "m4v", "3gp" -> FileCategory.VIDEOS
        "srt", "vtt", "ass", "sub" -> FileCategory.SUBTITLES
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> FileCategory.PHOTOS
        "apk", "exe", "msi", "dmg", "deb" -> FileCategory.PROGRAMS
        else -> FileCategory.OTHERS
    }
}
