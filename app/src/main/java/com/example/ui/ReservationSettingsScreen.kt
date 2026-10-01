package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
private fun RuleField(label: String, value: String, onValueChange: (String) -> Unit, multiline: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1
    )
}

@Composable
private fun RuleField(label: String, value: String, onValueChange: (String) -> Unit) {
    RuleField(label, value, onValueChange, false)
}

@Composable
private fun RulesSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun csvInts(s: String): JSONArray {
    val a = JSONArray()
    s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().forEach(a::put)
    return a
}

private fun jsonIntArray(o: JSONObject, key: String): String {
    val a = o.optJSONArray(key) ?: return ""
    return (0 until a.length()).joinToString(",") { a.optInt(it).toString() }
}

private fun int(o: JSONObject, key: String) = o.optInt(key, 0).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationSettingsScreen(managerId: String, onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    var normalDurations by remember { mutableStateOf("") }
    var vipDurations by remember { mutableStateOf("") }
    var vipMin by remember { mutableStateOf("") }
    var vipStart by remember { mutableStateOf("") }
    var vipEnd by remember { mutableStateOf("") }
    var paymentDeadline by remember { mutableStateOf("") }
    var vipPaymentDeadline by remember { mutableStateOf("") }
    var arrivalWarningMinutes by remember { mutableStateOf("") }
    var arrivalReminderMinutes by remember { mutableStateOf("") }
    var threshold24 by remember { mutableStateOf("") }
    var threshold15 by remember { mutableStateOf("") }
    var threshold5 by remember { mutableStateOf("") }
    var threshold2 by remember { mutableStateOf("") }
    var vipGn by remember { mutableStateOf("") }
    var vipLp by remember { mutableStateOf("") }
    var vipPrice by remember { mutableStateOf("") }

    val gn = remember { mutableStateMapOf("24" to "", "15" to "", "5" to "", "2" to "", "within2h" to "", "late" to "", "noShow" to "") }
    val lp = remember { mutableStateMapOf("24" to "", "15" to "", "5" to "", "2" to "", "within2h" to "", "late" to "", "noShow" to "") }
    val wallet = remember { mutableStateMapOf("24" to "", "15" to "", "5" to "", "2" to "", "within2h" to "", "late" to "", "noShow" to "") }

    var restrictionDays by remember { mutableStateOf("") }
    var surchargePercent by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateMapOf(
            "payment" to "", "arrival" to "", "vip" to "",
            "cancel24" to "", "cancel15" to "", "cancel5" to "", "cancel2" to "",
            "lateCancellation" to "", "noShow" to "", "vipPaymentDeadline" to ""
        )
    }


    fun load(json: JSONObject) {
        val root = json.optJSONObject("settings") ?: json
        val r = root.optJSONObject("reservationRules") ?: JSONObject()
        normalDurations = jsonIntArray(r, "normalDurationsMinutes")
        vipDurations = jsonIntArray(r, "vipDurationsMinutes")
        vipMin = int(r, "vipMinDurationMinutes")
        vipStart = r.optString("vipStartTime", "")
        vipEnd = r.optString("vipEndTime", "")
        paymentDeadline = int(r, "paymentDeadlineMinutes")
        vipPaymentDeadline = int(r, "vipPaymentDeadlineMinutes")
        arrivalWarningMinutes = int(r, "arrivalWarningMinutes")
        arrivalReminderMinutes = r.optJSONArray("arrivalReminderMinutes")?.let { a -> buildString { for (i in 0 until a.length()) { if (i > 0) append(","); append(a.optInt(i)) } } } ?: ""

        val c = r.optJSONObject("cancellation") ?: JSONObject()
        listOf(
            "24" to "atOrAbove24h", "15" to "above15h", "5" to "above5h",
            "2" to "above2h", "within2h" to "within2h"
        ).forEach { (key, name) ->
            val x = c.optJSONObject(name) ?: JSONObject()
            gn[key] = int(x, "gn")
            lp[key] = int(x, "lp")
            wallet[key] = int(x, "walletPercent")
        }
        threshold24 = int(c.optJSONObject("atOrAbove24h") ?: JSONObject(), "thresholdMinutes")
        threshold15 = int(c.optJSONObject("above15h") ?: JSONObject(), "thresholdMinutes")
        threshold5 = int(c.optJSONObject("above5h") ?: JSONObject(), "thresholdMinutes")
        threshold2 = int(c.optJSONObject("above2h") ?: JSONObject(), "thresholdMinutes")
        val within = c.optJSONObject("within2h") ?: JSONObject()
        restrictionDays = int(within, "restrictionDays")
        surchargePercent = int(within, "surchargePercent")

        val late = r.optJSONObject("lateCancellation") ?: JSONObject()
        val noShow = r.optJSONObject("noShow") ?: JSONObject()
        gn["late"] = int(late, "gn"); lp["late"] = int(late, "lp"); wallet["late"] = int(late, "walletPercent")
        gn["noShow"] = int(noShow, "gn"); lp["noShow"] = int(noShow, "lp"); wallet["noShow"] = int(noShow, "walletPercent")

        val reward = r.optJSONObject("vipReward") ?: JSONObject()
        vipGn = int(reward, "gn")
        vipLp = int(reward, "lp")
        vipPrice = r.optString("vipPrice", "")

        val m = r.optJSONObject("messages") ?: JSONObject()
        listOf("payment","arrival","vip","cancel24","cancel15","cancel5","cancel2","lateCancellation","noShow","vipPaymentDeadline").forEach {
            messages[it] = m.optString(it, "")
        }

    }

    fun buildSettings(): JSONObject {
        val r = JSONObject()
        r.put("normalDurationsMinutes", csvInts(normalDurations))
        r.put("vipDurationsMinutes", csvInts(vipDurations))
        r.put("vipMinDurationMinutes", vipMin.toIntOrNull() ?: 0)
        r.put("vipStartTime", vipStart)
        r.put("vipEndTime", vipEnd)
        r.put("paymentDeadlineMinutes", paymentDeadline.toIntOrNull() ?: 0)
        r.put("vipPaymentDeadlineMinutes", vipPaymentDeadline.toIntOrNull() ?: 0)
        r.put("arrivalWarningMinutes", arrivalWarningMinutes.toIntOrNull() ?: 0)
        r.put("arrivalReminderMinutes", org.json.JSONArray(arrivalReminderMinutes.split(",").mapNotNull { it.trim().toIntOrNull() }))

        fun stage(g: String, l: String, w: String) = JSONObject()
            .put("gn", gn[g]?.toIntOrNull() ?: 0)
            .put("lp", lp[l]?.toIntOrNull() ?: 0)
            .put("walletPercent", wallet[w]?.toIntOrNull() ?: 0)

        val c = JSONObject()
            .put("atOrAbove24h", stage("24","24","24"))
            .put("above15h", stage("15","15","15"))
            .put("above5h", stage("5","5","5"))
            .put("above2h", stage("2","2","2"))
            .put("within2h", stage("within2h","within2h","within2h")
                .put("thresholdMinutes", 0)
                .put("restrictionDays", restrictionDays.toIntOrNull() ?: 0)
                .put("surchargePercent", surchargePercent.toIntOrNull() ?: 0))

        c.getJSONObject("atOrAbove24h").put("thresholdMinutes", threshold24.toIntOrNull() ?: 0)
        c.getJSONObject("above15h").put("thresholdMinutes", threshold15.toIntOrNull() ?: 0)
        c.getJSONObject("above5h").put("thresholdMinutes", threshold5.toIntOrNull() ?: 0)
        c.getJSONObject("above2h").put("thresholdMinutes", threshold2.toIntOrNull() ?: 0)
        r.put("cancellation", c)
        r.put("lateCancellation", stage("late","late","late"))
        r.put("noShow", stage("noShow","noShow","noShow"))
        r.put("vipReward", JSONObject().put("gn", vipGn.toIntOrNull() ?: 0).put("lp", vipLp.toIntOrNull() ?: 0))
        r.put("vipPrice", vipPrice.toLongOrNull() ?: 0)
        r.put("messages", JSONObject(messages.toMap()))

        return JSONObject().put("reservationRules", r)
    }

    fun save() {
        scope.launch {
            saving = true
            val ok = SelfHostedManager.updateManagerConfiguration(buildSettings()) != null
            message = if (ok) "تنظیمات این مدیر با موفقیت ذخیره شد." else "ذخیره تنظیمات ناموفق بود."
            saving = false
        }
    }

    LaunchedEffect(managerId) {
        loading = true
        val result = SelfHostedManager.fetchManagerConfiguration()
        if (result != null) load(result) else message = "دریافت تنظیمات از سرور ناموفق بود."
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("قوانین و تنظیمات رزرو") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") } },
                actions = { IconButton(enabled = !saving, onClick = ::save) { Icon(Icons.Default.Save, "ذخیره") } }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                RulesSection("مدت‌ها و زمان VIP") {
                    RuleField("مدت‌های عادی، دقیقه و جداشده با کاما", normalDurations) { normalDurations = it }
                    RuleField("مدت‌های VIP، دقیقه و جداشده با کاما", vipDurations) { vipDurations = it }
                    RuleField("حداقل مدت VIP، دقیقه", vipMin) { vipMin = it }
                    RuleField("ساعت شروع VIP", vipStart) { vipStart = it }
                    RuleField("ساعت پایان VIP", vipEnd) { vipEnd = it }
                }

                RulesSection("پرداخت") {
                    RuleField("مهلت پرداخت رزرو، دقیقه", paymentDeadline) { paymentDeadline = it }
                    RuleField("مهلت پرداخت VIP، دقیقه", vipPaymentDeadline) { vipPaymentDeadline = it }
                    RuleField("هشدار حضور قبل از رزرو، دقیقه", arrivalWarningMinutes) { arrivalWarningMinutes = it }
                    RuleField("یادآورهای رزرو، دقیقه قبل (مثال: 120,30,15,5)", arrivalReminderMinutes) { arrivalReminderMinutes = it }
                    RuleField("آستانه قانون 24 ساعت، دقیقه", threshold24) { threshold24 = it }
                    RuleField("آستانه قانون 15 ساعت، دقیقه", threshold15) { threshold15 = it }
                    RuleField("آستانه قانون 5 ساعت، دقیقه", threshold5) { threshold5 = it }
                    RuleField("آستانه قانون 2 ساعت، دقیقه", threshold2) { threshold2 = it }
                }

                RulesSection("قیمت VIP") {
                    RuleField("قیمت VIP", vipPrice) { vipPrice = it }
                }

                RulesSection("جریمه، Wallet و محدودیت") {
                    listOf(
                        "24" to "24 ساعت یا بیشتر", "15" to "کمتر از 24 و بیشتر از 15 ساعت",
                        "5" to "15 ساعت یا کمتر و بیشتر از 5 ساعت", "2" to "5 ساعت یا کمتر و بیشتر از 2 ساعت",
                        "within2h" to "2 ساعت یا کمتر", "late" to "لغو در زمان شروع/بعد از شروع", "noShow" to "No Show"
                    ).forEach { (k, title) ->
                        Text(title, fontWeight = FontWeight.SemiBold)
                        RuleField("GN", gn[k] ?: "") { gn[k] = it }
                        RuleField("LP", lp[k] ?: "") { lp[k] = it }
                        RuleField("Wallet درصد", wallet[k] ?: "") { wallet[k] = it }
                    }
                    RuleField("مدت محرومیت، روز", restrictionDays) { restrictionDays = it }
                    RuleField("افزایش هزینه رزرو بعدی، درصد", surchargePercent) { surchargePercent = it }
                }

                RulesSection("پاداش VIP") {
                    RuleField("GN پاداش VIP", vipGn) { vipGn = it }
                    RuleField("LP پاداش VIP", vipLp) { vipLp = it }
                }

                RulesSection("تمام متن‌ها و تذکرهای قابل ویرایش") {
                    RuleField("متن مهلت پرداخت رزرو", messages["payment"] ?: "", { messages["payment"] = it }, true)
                    RuleField("متن هشدار حضور/لغو", messages["arrival"] ?: "", { messages["arrival"] = it }, true)
                    RuleField("متن قوانین/تأیید VIP", messages["vip"] ?: "", { messages["vip"] = it }, true)
                    RuleField("متن لغو 24 ساعت یا بیشتر", messages["cancel24"] ?: "", { messages["cancel24"] = it }, true)
                    RuleField("متن لغو 15 ساعت", messages["cancel15"] ?: "", { messages["cancel15"] = it }, true)
                    RuleField("متن لغو 5 ساعت", messages["cancel5"] ?: "", { messages["cancel5"] = it }, true)
                    RuleField("متن لغو 2 ساعت", messages["cancel2"] ?: "", { messages["cancel2"] = it }, true)
                    RuleField("متن لغو دیرهنگام", messages["lateCancellation"] ?: "", { messages["lateCancellation"] = it }, true)
                    RuleField("متن No Show", messages["noShow"] ?: "", { messages["noShow"] = it }, true)
                    RuleField("متن مهلت پرداخت VIP", messages["vipPaymentDeadline"] ?: "", { messages["vipPaymentDeadline"] = it }, true)
                    Text("Placeholderهای پویا مثل {duration}، {payment_deadline}، {minutes_before_arrival}، {gn_penalty}، {lp_penalty}، {refund_percent}، {restriction_days} و {surcharge_percent} باید توسط Backend هنگام نمایش جایگزین شوند.", style = MaterialTheme.typography.bodySmall)
                }

                Button(enabled = !saving, onClick = ::save, modifier = Modifier.fillMaxWidth()) {
                    Text(if (saving) "در حال ذخیره..." else "ذخیره همه تنظیمات")
                }
            }
        }
    }
}
