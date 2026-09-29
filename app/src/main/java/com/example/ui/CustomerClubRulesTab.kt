package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.network.SelfHostedManager
import org.json.JSONObject

@Composable
fun CustomerClubRulesTab(viewModel: GameNetViewModel) {
    var expandedRule by remember { mutableStateOf<String?>(null) }
    var rules by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(Unit) {
        rules = SelfHostedManager.fetchReservationRules()
    }

    val root = remember(rules) { rules?.optJSONObject("rules") ?: JSONObject() }
    val messages = remember(root) { root.optJSONObject("messages") ?: JSONObject() }
    val cancellation = remember(root) { root.optJSONObject("cancellation") ?: JSONObject() }
    val normalDurations = remember(root) { root.optJSONArray("normalDurationsMinutes") }
    val vipDurations = remember(root) { root.optJSONArray("vipDurationsMinutes") }
    val paymentDeadline = root.optInt("paymentDeadlineMinutes", 0)
    val vipPaymentDeadline = root.optInt("vipPaymentDeadlineMinutes", 0)
    val vipMin = root.optInt("vipMinDurationMinutes", 0)
    val vipPrice = root.optLong("vipPrice", 0L)

    fun stageText(key: String): String {
        val stage = cancellation.optJSONObject(key) ?: JSONObject()
        val gn = stage.optInt("gn", 0)
        val lp = stage.optInt("lp", 0)
        val wallet = stage.optInt("walletPercent", 0)
        return "GN: $gn | LP: $lp | بازگشت به Wallet: $wallet٪"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "قوانین و مقررات گیم‌نکسا",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            ExpandableRuleSection(
                title = "قوانین رزرو عادی و VIP",
                icon = Icons.Default.Star,
                iconColor = Color(0xFFFBC02D),
                isExpanded = expandedRule == "VIP",
                onExpand = { expandedRule = if (expandedRule == "VIP") null else "VIP" }
            ) {
                if (messages.optString("payment").isNotBlank()) Text(messages.optString("payment"))
                if (messages.optString("vip").isNotBlank()) Text(messages.optString("vip"))
                if (messages.optString("vipPaymentDeadline").isNotBlank()) Text(messages.optString("vipPaymentDeadline"))
                Text("مهلت پرداخت رزرو: $paymentDeadline دقیقه")
                Text("حداقل مدت VIP: $vipMin دقیقه")
                if (vipPrice > 0) Text("قیمت تنظیم‌شده VIP: $vipPrice")
                Text("مدت‌های عادی تنظیم‌شده: " + (normalDurations?.let { a -> buildString { for (i in 0 until a.length()) { if (i > 0) append("، "); append(a.optInt(i)) } } } ?: "—"))
                Text("مدت‌های VIP تنظیم‌شده: " + (vipDurations?.let { a -> buildString { for (i in 0 until a.length()) { if (i > 0) append("، "); append(a.optInt(i)) } } } ?: "—"))
            }
        }

        item {
            ExpandableRuleSection(
                title = "جریمه کنسلی",
                icon = Icons.Default.Cancel,
                iconColor = MaterialTheme.colorScheme.error,
                isExpanded = expandedRule == "CANCEL",
                onExpand = { expandedRule = if (expandedRule == "CANCEL") null else "CANCEL" }
            ) {
                listOf(
                    messages.optString("cancel24"),
                    messages.optString("cancel15"),
                    messages.optString("cancel5"),
                    messages.optString("cancel2")
                ).filter { it.isNotBlank() }.forEach { Text(it) }
                Text("بازه ≥۲۴ ساعت: " + stageText("atOrAbove24h"))
                Text("بازه >۱۵ ساعت: " + stageText("above15h"))
                Text("بازه >۵ ساعت: " + stageText("above5h"))
                Text("بازه >۲ ساعت: " + stageText("above2h"))
                Text("بازه ≤۲ ساعت: " + stageText("within2h"))
                if (messages.optString("lateCancellation").isNotBlank()) Text(messages.optString("lateCancellation"))
            }
        }

        item {
            ExpandableRuleSection(
                title = "عدم حضور",
                icon = Icons.Default.Warning,
                iconColor = Color(0xFFE64A19),
                isExpanded = expandedRule == "NOSHOW",
                onExpand = { expandedRule = if (expandedRule == "NOSHOW") null else "NOSHOW" }
            ) {
                if (messages.optString("noShow").isNotBlank()) Text(messages.optString("noShow"))
                Text("قانون No Show و جریمه آن توسط تنظیمات همین مدیریت کنترل می‌شود.")
            }
        }

        item {
            ExpandableRuleSection(
                title = "رزرو VIP / کل سالن",
                icon = Icons.Default.Info,
                iconColor = MaterialTheme.colorScheme.secondary,
                isExpanded = expandedRule == "FULL",
                onExpand = { expandedRule = if (expandedRule == "FULL") null else "FULL" }
            ) {
                if (messages.optString("vip").isNotBlank()) Text(messages.optString("vip"))
                if (messages.optString("vipPaymentDeadline").isNotBlank()) Text(messages.optString("vipPaymentDeadline"))
                Text("قیمت VIP و مدت‌های مجاز از تنظیمات فعلی مدیریت دریافت می‌شوند.")
            }
        }
    }
}
