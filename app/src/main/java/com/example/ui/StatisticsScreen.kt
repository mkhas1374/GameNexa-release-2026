package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SessionHistory
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatisticsScreen(
    viewModel: GameNetViewModel,
    modifier: Modifier = Modifier
) {
    val history by viewModel.sessionHistory.collectAsState()
    val scrollState = rememberScrollState()
    val lang by viewModel.language.collectAsState()

    // Date formatting helpers
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val currentMonthStr = remember { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) }

    // Aggregate stats
    val todayIncome = remember(history, todayStr) {
        history.filter { it.dateString == todayStr }.sumOf { it.totalCost }
    }

    val currentMonthIncome = remember(history, currentMonthStr) {
        history.filter { it.monthString == currentMonthStr }.sumOf { it.totalCost }
    }

    val monthlyGrouped = remember(history) {
        history.groupBy { it.monthString }.mapValues { (_, records) -> records.sumOf { it.totalCost } }
    }

    val averageMonthlyIncome = remember(monthlyGrouped) {
        if (monthlyGrouped.isEmpty()) 0L else monthlyGrouped.values.average()
    }

    // Prepare 30 Days daily income for the chart
    val last30DaysIncome = remember(history) {
        val daysList = mutableListOf<Pair<String, Long>>()

        // Backtrack 30 days
        for (i in 29 downTo 0) {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, -i)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dayKey = format.format(c.time)
            val dayIncome = history.filter { it.dateString == dayKey }.sumOf { it.totalCost }
            val labelFormat = SimpleDateFormat("d", Locale.US)
            daysList.add(Pair(labelFormat.format(c.time), dayIncome))
        }
        daysList
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("statistics_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (lang == "fa") "تجزیه و تحلیل مالی" else "Business Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Reset Income Button
            if (history.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { viewModel.resetHistory() },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == "fa") "پاکسازی" else "Reset Data", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // -------------------------------------------------------------
        // KEY INCOME STATS
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = Localization.get("today_income", lang),
                value = todayIncome,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                lang = lang,
                modifier = Modifier.weight(1f)
            )

            StatCard(
                title = Localization.get("monthly_income", lang),
                value = currentMonthIncome,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                lang = lang,
                modifier = Modifier.weight(1f)
            )
        }

        StatCard(
            title = if (lang == "fa") "میانگین درآمد ماهانه" else "AVERAGE MONTHLY INCOME",
            value = averageMonthlyIncome.toLong(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            lang = lang,
            modifier = Modifier.fillMaxWidth()
        )

        // -------------------------------------------------------------
        // DAILY INCOME CHART (LAST 30 DAYS)
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = Localization.get("daily_income_chart", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Localization.get("no_history", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    val barColor = MaterialTheme.colorScheme.primary
                    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

                    Column {
                        // Drawing the Bar Chart using Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height

                            val maxVal = last30DaysIncome.maxOf { it.second }.coerceAtLeast(1000L)
                            val barCount = last30DaysIncome.size
                            val spacing = 4f
                            val totalSpacing = spacing * (barCount - 1)
                            val barWidth = (canvasWidth - totalSpacing) / barCount

                            last30DaysIncome.forEachIndexed { idx, pair ->
                                val income = pair.second
                                val barHeight = ((income / maxVal) * (canvasHeight - 20f)).toLong()

                                // Draw bar
                                val x = idx * (barWidth + spacing)
                                val y = canvasHeight - barHeight

                                if (income > 0) {
                                    drawRoundRect(
                                        color = barColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth.toFloat(), barHeight.toFloat()),
                                        cornerRadius = CornerRadius(2f, 2f)
                                    )
                                } else {
                                    // Draw a very tiny dot/line for zero days so the grid remains scannable
                                    drawRect(
                                        color = barColor.copy(alpha = 0.15f),
                                        topLeft = Offset(x, canvasHeight - 4f),
                                        size = Size(barWidth, 4f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chart X-Axis markers (First day, Mid day, Today)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = if (lang == "fa") "30 روز پیش" else "30 days ago", style = MaterialTheme.typography.labelSmall, color = labelColor)
                            Text(text = if (lang == "fa") "15 روز پیش" else "15 days ago", style = MaterialTheme.typography.labelSmall, color = labelColor)
                            Text(text = if (lang == "fa") "امروز" else "Today", style = MaterialTheme.typography.labelSmall, color = labelColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // MONTHLY BREAKDOWN
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = Localization.get("monthly_breakdown", lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (monthlyGrouped.isEmpty()) {
                    Text(
                        text = if (lang == "fa") "هیچ داده‌ای در دسترس نیست." else "No data available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val sortedMonths = monthlyGrouped.keys.sortedDescending()
                    sortedMonths.forEachIndexed { idx, monthKey ->
                        val income = monthlyGrouped[monthKey] ?: 0L

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (lang == "fa") "ماه ${sortedMonths.size - idx}" else "Month ${sortedMonths.size - idx}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = monthKey,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "%,d".format(Locale.US, income),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp)) // Avoid navigation cutoff
    }
}

@Composable
fun StatCard(
    title: String,
    value: Long,
    containerColor: Color,
    contentColor: Color,
    lang: String,
    modifier: Modifier = Modifier
) {
    val highlightColor = if (title.contains("TODAY") || title.contains("امروز")) {
        MaterialTheme.colorScheme.primary
    } else if (title.contains("MONTH") || title.contains("ماه")) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "%,d".format(Locale.US, value),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = highlightColor
            )
        }
    }
}
