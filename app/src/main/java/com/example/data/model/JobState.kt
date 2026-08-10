package com.example.data.model

enum class JobState(val displayNameRu: String) {
    CREATED("Создано"),
    VALIDATING("Проверка медиафайла"),
    PREPARING_AUDIO("Декодирование и конвертация PCM"),
    LOADING_MODEL("Загрузка модели Whisper"),
    DETECTING_LANGUAGE("Определение языка"),
    RUNNING_VAD("Детекция голосовой активности (VAD)"),
    RUNNING_DIARIZATION("Диаризация и разделение спикеров"),
    RUNNING_TRANSCRIPTION("Распознавание речи (Whisper)"),
    ASSEMBLING_RESULT("Сборка и сохранение транскрипта"),
    COMPLETED("Завершено"),
    FAILED("Ошибка"),
    CANCELLED("Отменено")
}
