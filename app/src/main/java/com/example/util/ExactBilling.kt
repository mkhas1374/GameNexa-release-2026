package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

object ExactBilling {
    private val MILLIS_PER_HOUR = BigDecimal("3600000")

    fun costForMillis(ratePerHour: Long, elapsedMillis: Long): BigDecimal =
        BigDecimal.valueOf(ratePerHour).multiply(BigDecimal.valueOf(elapsedMillis)).divide(MILLIS_PER_HOUR, 10, RoundingMode.DOWN)

    fun formatToman(value: BigDecimal): String {
        // Money displayed/stored by GameNexa is whole Toman. Internal billing may
        // retain sub-Toman precision for exact second-based calculations, but the
        // user-facing amount must never expose decimal fractions.
        val formatter = java.text.DecimalFormat("#,##0")
        formatter.roundingMode = RoundingMode.DOWN
        return "${formatter.format(value.setScale(0, RoundingMode.DOWN))} تومان"
    }
}
