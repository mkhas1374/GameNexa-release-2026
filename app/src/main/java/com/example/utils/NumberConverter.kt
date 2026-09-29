package com.example.utils

object NumberConverter {
    fun toEnglishDigits(str: String): String {
        val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val arabicDigits = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        var result = str
        for (i in 0..9) {
            result = result.replace(persianDigits[i], i.toString()[0])
            result = result.replace(arabicDigits[i], i.toString()[0])
        }
        return result
    }

    fun normalizePhoneNumber(str: String?): String {
        if (str.isNullOrBlank()) return ""
        var eng = toEnglishDigits(str.trim()).replace(Regex("[^0-9]"), "")
        return when {
            eng.startsWith("0098") -> eng.substring(4).removePrefix("0")
            eng.startsWith("98") && eng.length >= 12 -> eng.substring(2).removePrefix("0")
            eng.startsWith("0") -> eng.removePrefix("0")
            else -> eng
        }
    }
}
