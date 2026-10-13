package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

object ExactBilling {
    private val MILLIS_PER_HOUR = BigDecimal("3600000")

    fun costForMillis(ratePerHour: Long, elapsedMillis: Long): BigDecimal =
        BigDecimal.valueOf(ratePerHour).multiply(BigDecimal.valueOf(elapsedMillis)).divide(MILLIS_PER_HOUR, 10, RoundingMode.DOWN)

    fun durationMillisForAmount(amountToman: Long, ratePerHour: Long): Long {
        if (amountToman <= 0L || ratePerHour <= 0L) return 0L
        return BigDecimal.valueOf(amountToman)
            .multiply(MILLIS_PER_HOUR)
            .divide(BigDecimal.valueOf(ratePerHour), 0, RoundingMode.DOWN)
            .longValueExact()
            .coerceAtLeast(0L)
    }

    fun costForMinutes(ratePerHour: Long, minutes: Int): BigDecimal =
        if (ratePerHour <= 0L || minutes <= 0) BigDecimal.ZERO
        else BigDecimal.valueOf(ratePerHour).multiply(BigDecimal.valueOf(minutes.toLong())).divide(BigDecimal("60"), 10, RoundingMode.DOWN)

    fun formatToman(value: BigDecimal): String {
        // Money displayed/stored by GameNexa is whole Toman. Internal billing may
        // retain sub-Toman precision for exact second-based calculations, but the
        // user-facing amount must never expose decimal fractions.
        val formatter = java.text.DecimalFormat("#,##0")
        formatter.roundingMode = RoundingMode.DOWN
        return "${formatter.format(value.setScale(0, RoundingMode.DOWN))} تومان"
    }
}
