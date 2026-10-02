package com.example.data

enum class TranslationMode(val label: String, val badge: String) {
    ORIGINAL_ONLY("원문", "ORG"),
    BILINGUAL("원문+번역", "DUAL"),
    TRANSLATION_ONLY("번역문", "TRN"),
    ROMANIZATION("발음/병음", "ROM")
}
