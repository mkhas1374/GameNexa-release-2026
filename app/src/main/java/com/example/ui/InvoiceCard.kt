package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CustomerTransaction
import java.util.Locale

@Composable
fun InvoiceCard(
    trans: CustomerTransaction,
    modifier: Modifier = Modifier
) {
    // Calculate and separate game and food costs cleanly
    val effectiveFoodCost = if (trans.foodCost > 0) {
        trans.foodCost
    } else if (trans.buffetDetails.isNotBlank()) {
        val matches = Regex("""\(([\d,]+)\s*تومان\)""").findAll(trans.buffetDetails)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace(",", "")?.toLongOrNull() }
            .toList()
        if (matches.isNotEmpty()) matches.sum() else 0L
    } else {
        0L
    }

    val totalBill = if (trans.paidAmount > 0L) trans.paidAmount else trans.amount
    val effectiveGameCost = if (trans.gameCost > 0) {
        trans.gameCost
    } else {
        (totalBill - effectiveFoodCost).coerceAtLeast(0L)
    }

    val isDebtor = trans.status == "DEBTOR"

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // سطر اول (کنسول): آیکون کنسول + نام ایستگاه و تعداد دسته در راست، و مبلغ بازی در چپ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    val cleanSt = trans.stationName.replace("🛋️", "").replace("🎮", "").replace("ایستگاه", "", ignoreCase = true).replace("جایگاه", "", ignoreCase = true).trim().ifBlank { "دستگاه" }
                    val cleanTt = trans.title.replace("🛋️", "").replace("🎮", "").trim()
                    val titleDisplay = if (cleanTt.isNotBlank()) "🛋️ $cleanSt | 🎮 $cleanTt" else "🛋️ $cleanSt"
                    Text(
                        text = titleDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "%,d تومان".format(Locale.US, effectiveGameCost),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }

            // سطر دوم (در صورت وجود بوفه): نام و تعداد اقلام خوراکی در راست، و مبلغ بوفه در چپ
            if (trans.buffetDetails.isNotBlank() || effectiveFoodCost > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        val cleanBuffetText = trans.buffetDetails
                            .replace(Regex("""\(([\d,]+)\s*تومان\)"""), "")
                            .split(Regex("(?<=\\))\\s*,\\s*|\\n"))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .joinToString("\n") { "• $it" }
                            .ifBlank { "سفارش بوفه" }
                        
                        Text(
                            text = cleanBuffetText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "%,d تومان".format(Locale.US, effectiveFoodCost),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.6.dp,
                modifier = Modifier.padding(vertical = 1.dp)
            )

            // سطر سوم (فوتر فاکتور): تاریخ و ساعت تسویه + نشان وضعیت در راست، و «مجموع: 145,000 تومان» با فونت درشت در چپ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${trans.dateStr} | ${trans.timeStr}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = if (isDebtor) "بدهکار ❌" else "تسویه شده ✅",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDebtor) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        maxLines = 1
                    )
                }

                Text(
                    text = "مجموع: %,d تومان".format(Locale.US, totalBill),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = if (isDebtor) MaterialTheme.colorScheme.error else Color(0xFF00E676),
                    maxLines = 1
                )
            }
        }
    }
}
