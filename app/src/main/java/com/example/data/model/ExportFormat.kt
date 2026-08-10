package com.example.data.model

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    TXT("txt", "text/plain", "Текстовый документ (.txt)"),
    SRT("srt", "application/x-subrip", "Субтитры SubRip (.srt)"),
    VTT("vtt", "text/vtt", "Субтитры WebVTT (.vtt)"),
    JSON("json", "application/json", "Структурированный JSON (.json)")
}
