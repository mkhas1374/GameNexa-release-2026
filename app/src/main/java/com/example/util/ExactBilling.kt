package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

object ExactBilling {
    private val MILLIS_PER_HOUR = BigDecimal("3600000")

    fun costForMillis(ratePerHour: Long, elapsedMillis: Long): BigDecimal =
        BigDecimal.valueOf(ratePerHour).multiply(BigDecimal.valueOf(elapsedMillis)).divide(MILLIS_PER_HOUR, 10, RoundingMode.DOWN)

    fun formatToman(value: BigDecimal): String {
        val longVal = value.setScale(0, RoundingMode.DOWN).toLong()
        val formatter = java.text.DecimalFormat("#,##0")
        return "${formatter.format(longVal)} تومان"
    }
}
