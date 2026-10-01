package com.example.util

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object JalaliCalendarHelper {

    fun formatJalaliDateTime(timestampMs: Long, includeTime: Boolean = true): String {
        if (timestampMs <= 0L || timestampMs >= Long.MAX_VALUE - (365L * 86400_000L)) return "اشتراک فعال"

        val date = Date(timestampMs)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.time = date

        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)

        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val jalali = gregorianToJalali(gYear, gMonth, gDay)
        val yearStr = toEnglishDigits(jalali[0].toString())
        val monthStr = toEnglishDigits(String.format(Locale.US, "%02d", jalali[1]))
        val dayStr = toEnglishDigits(String.format(Locale.US, "%02d", jalali[2]))

        val dateStr = "$yearStr/$monthStr/$dayStr"
        if (!includeTime) return dateStr

        val timeStr = toEnglishDigits(String.format(Locale.US, "%02d:%02d", hour, minute))
        return "$dateStr - ساعت $timeStr"
    }

    fun getCurrentJalaliDate(): String {
        return formatJalaliDateTime(System.currentTimeMillis(), includeTime = false)
    }

    fun getCurrentJalaliTime(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    fun getPlanTitleFa(planType: String): String {
        return when (planType.uppercase()) {
            "TRIAL" -> "اشتراک تست رایگان 24 ساعته"
            "MONTH1", "1_MONTH", "MONTHLY" -> "اشتراک 1 ماهه"
            "MONTH3", "3_MONTH", "SEASONAL" -> "اشتراک 3 ماهه"
            "MONTH6", "6_MONTH" -> "اشتراک 6 ماهه"
            "MONTH12", "12_MONTH", "YEARLY" -> "اشتراک 1 ساله"
            "VIP" -> "اشتراک ویژه VIP"
            "SUPER_MANAGER" -> "مدیریت ارشد"
            "MANAGER" -> "مدیر سیستم"
            else -> if (planType.isNotBlank()) "اشتراک $planType" else "اشتراک نامشخص"
        }
    }


    private fun gregorianToJalali(gY: Int, gM: Int, gD: Int): IntArray {
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

        var gy = gY - 1600
        var gm = gM - 1
        var gd = gD - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i + 1]
        }
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        for (i in 0..11) {
            val days = if (i < 6) 31 else if (i < 11) 30 else 29
            if (jDayNo < days) {
                jm = i + 1
                break
            }
            jDayNo -= days
        }
        val jd = jDayNo + 1

        return intArrayOf(jy, jm, jd)
    }

    fun toEnglishDigits(input: String): String = input.translateDigits()

    // Kept for source compatibility with older callers; display output is now always Western digits.
    fun toPersianDigits(input: String): String = input.translateDigits()

    private fun String.translateDigits(): String = map { ch ->
        when (ch) {
            '۰', '٠' -> '0'
            '۱', '١' -> '1'
            '۲', '٢' -> '2'
            '۳', '٣' -> '3'
            '۴', '٤' -> '4'
            '۵', '٥' -> '5'
            '۶', '٦' -> '6'
            '۷', '٧' -> '7'
            '۸', '٨' -> '8'
            '۹', '٩' -> '9'
            else -> ch
        }
    }.joinToString("")

    fun formatRemainingTime(expiresAtMs: Long, serverNowMs: Long = System.currentTimeMillis()): String {
        if (expiresAtMs <= 0L || expiresAtMs >= Long.MAX_VALUE - (365L * 86400_000L)) return "اشتراک فعال"
        val diff = expiresAtMs - serverNowMs
        if (diff <= 0L) return "منقضی شده"
        if (diff > 10L * 365L * 86400_000L) return "اشتراک فعال"

        val totalSeconds = diff / 1000L
        val totalMinutes = totalSeconds / 60L
        val totalHours = totalMinutes / 60L
        val days = totalHours / 24L
        val hours = totalHours % 24L
        val minutes = totalMinutes % 60L
        val seconds = totalSeconds % 60L

        return String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
    }
}

fun String.toPersianDigits(): String = JalaliCalendarHelper.toPersianDigits(this)
