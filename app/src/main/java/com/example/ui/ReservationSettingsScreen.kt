package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class ReservationCategory(
    val titleFa: String,
    val subtitleFa: String,
    val icon: ImageVector,
    val badgeColor: Color
) {
    DURATIONS("🎮 انواع و مدت رزرو", "مدیریت مدت‌های رزرو عادی و VIP", Icons.Default.HourglassTop, Color(0xFF1E88E5)),
    PRICING("💰 قیمت‌گذاری", "مدیریت قیمت Station، کنسول‌ها و دسته‌ها", Icons.Default.AttachMoney, Color(0xFF43A047)),
    VIP_RULES("⭐ قوانین VIP", "Gold / Diamond / VIP و اولویت سرویس‌دهی", Icons.Default.Star, Color(0xFFF57C00)),
    PAYMENT("💳 پرداخت و پیش‌پرداخت", "مهلت پرداخت، زمان حضور و یادآورها", Icons.Default.Payment, Color(0xFF8E24AA)),
    CANCELLATION("❌ لغو و جریمه", "بازگشت کیف پول، جریمه‌ها و محرومیت", Icons.Default.Cancel, Color(0xFFE53935)),
    GN_LP("🏆 GN / LP", "پاداش‌های VIP و قوانین امتیازات", Icons.Default.EmojiEvents, Color(0xFFD81B60)),
    FULL_HALL("🏟 رزرو سالن", "شرایط رزرو کامل سالن و اختصاصی", Icons.Default.SportsEsports, Color(0xFF00897B)),
    POLICY_TEXTS("📝 متن قوانین مشتری", "مدیریت متن‌های نمایشی و هشدارها به مشتری", Icons.Default.Description, Color(0xFF5C6BC0))
}

@Composable
private fun RuleField(
    label: String,
    value: String,
    multiline: Boolean = false,
    isNumeric: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (multiline) ImeAction.Default else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(12.dp)
    )
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

object ReservationMessageFormatter {
    fun sanitize(
        key: String,
        rawText: String,
        paymentDeadline: String = "15",
        vipPaymentDeadline: String = "15",
        restrictionDays: String = "3",
        surchargePercent: String = "20"
    ): String {
        val pDeadline = paymentDeadline.ifBlank { "15" }
        val vipPDeadline = vipPaymentDeadline.ifBlank { "15" }
        val rDays = restrictionDays.ifBlank { "3" }
        val sPercent = surchargePercent.ifBlank { "20" }

        val text = rawText.trim()
        val isRawTemplate = text.isBlank() || text.contains("Placeholder") || text.contains("{{") || text.contains("{")

        if (isRawTemplate) {
            return when (key) {
                "payment" -> "مهلت پرداخت رزرو کامل: $pDeadline دقیقه. پرداخت موفق به تنهایی به معنی تأیید نهایی رزرو نیست."
                "arrival" -> "لطفاً حداقل ۱۵ دقیقه قبل از زمان رزرو در سالن گیم‌نت حضور داشته باشید."
                "vip" -> "رزرو جایگاه VIP شامل پاداش ویژه GN و اولویت سرویس‌دهی می‌باشد."
                "cancel24" -> "لغو ۲۴ ساعت یا بیشتر قبل از شروع: بازگشت ۱۰۰٪ مبلغ به کیف پول و بدون جریمه."
                "cancel15" -> "لغو ۱۵ تا ۲۴ ساعت قبل از شروع: بازگشت ۸۰٪ مبلغ به کیف پول."
                "cancel5" -> "لغو ۵ تا ۱۵ ساعت قبل از شروع: بازگشت ۵۰٪ مبلغ به کیف پول."
                "cancel2" -> "لغو ۲ تا ۵ ساعت قبل از شروع: بازگشت ۲۰٪ مبلغ به کیف پول."
                "lateCancellation" -> "لغو در زمان شروع یا بعد از آن طبق قانون لغو دیرهنگام، محرومیت $rDays روزه و $sPercent٪ افزایش هزینه برای اولویت رزرو بعدی اعمال می‌شود."
                "noShow" -> "عدم حضور مشتری طبق قانون No Show مدیریت محاسبه و ثبت می‌شود."
                "vipPaymentDeadline" -> "مهلت پرداخت کامل VIP: $vipPDeadline دقیقه. پرداخت موفق به تنهایی به معنی تأیید رزرو نیست."
                else -> text
            }
        }
        return text
            .replace(Regex("""\{\{?\s*payment_deadline\s*\}\}?|\{payment_deadline\}"""), pDeadline)
            .replace(Regex("""\{\{?\s*vip_payment_deadline\s*\}\}?|\{vip_payment_deadline\}"""), vipPDeadline)
            .replace(Regex("""\{\{?\s*restriction_days\s*\}\}?|\{restriction_days\}"""), rDays)
            .replace(Regex("""\{\{?\s*surcharge_percent\s*\}\}?|\{surcharge_percent\}"""), sPercent)
            .replace(Regex("""\{\{?\s*minutes_before_arrival\s*\}\}?|\{minutes_before_arrival\}"""), "15")
            .replace(Regex("""\{\{?\s*duration\s*\}\}?|\{duration\}"""), "60")
            .replace(Regex("""\{\{?\s*gn_penalty\s*\}\}?|\{gn_penalty\}"""), "0")
            .replace(Regex("""\{\{?\s*lp_penalty\s*\}\}?|\{lp_penalty\}"""), "0")
            .replace(Regex("""\{\{?\s*refund_percent\s*\}\}?|\{refund_percent\}"""), "100")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationSettingsScreen(managerId: String, onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf<ReservationCategory?>(null) }

    // State Variables with sensible defaults
    var normalDurations by remember { mutableStateOf("60,120,180,240,300") }
    var vipDurations by remember { mutableStateOf("180,240,300,360") }
    var vipMin by remember { mutableStateOf("180") }
    var vipStart by remember { mutableStateOf("14:00") }
    var vipEnd by remember { mutableStateOf("23:59") }

    var ps4Rate by remember { mutableStateOf("50000") }
    var ps5Rate by remember { mutableStateOf("80000") }
    var simulatorRate by remember { mutableStateOf("90000") }
    var extraControllerRate by remember { mutableStateOf("15000") }
    var fullHallHourlyRate by remember { mutableStateOf("500000") }

    var paymentDeadline by remember { mutableStateOf("15") }
    var vipPaymentDeadline by remember { mutableStateOf("15") }
    var arrivalWarningMinutes by remember { mutableStateOf("15") }
    var arrivalReminderMinutes by remember { mutableStateOf("120,30,15,5") }
    var depositPercent by remember { mutableStateOf("100") }

    var threshold24 by remember { mutableStateOf("1440") }
    var threshold15 by remember { mutableStateOf("900") }
    var threshold5 by remember { mutableStateOf("300") }
    var threshold2 by remember { mutableStateOf("120") }

    var vipGn by remember { mutableStateOf("50") }
    var vipLp by remember { mutableStateOf("20") }
    var vipPrice by remember { mutableStateOf("120000") }

    val gn = remember { mutableStateMapOf("24" to "0", "15" to "10", "5" to "20", "2" to "30", "within2h" to "40", "late" to "50", "noShow" to "100") }
    val lp = remember { mutableStateMapOf("24" to "0", "15" to "5", "5" to "10", "2" to "15", "within2h" to "20", "late" to "25", "noShow" to "50") }
    val wallet = remember { mutableStateMapOf("24" to "100", "15" to "80", "5" to "50", "2" to "20", "within2h" to "0", "late" to "0", "noShow" to "0") }

    var restrictionDays by remember { mutableStateOf("3") }
    var surchargePercent by remember { mutableStateOf("20") }

    val messages = remember {
        mutableStateMapOf(
            "payment" to "مهلت پرداخت رزرو کامل: ۱۵ دقیقه. پرداخت موفق به تنهایی به معنی تأیید نهایی رزرو نیست.",
            "arrival" to "لطفاً حداقل ۱۵ دقیقه قبل از زمان رزرو در سالن گیم‌نت حضور داشته باشید.",
            "vip" to "رزرو جایگاه VIP شامل پاداش ویژه GN و اولویت سرویس‌دهی می‌باشد.",
            "cancel24" to "لغو ۲۴ ساعت یا بیشتر قبل از شروع: بازگشت ۱۰۰٪ مبلغ به کیف پول و بدون جریمه.",
            "cancel15" to "لغو ۱۵ تا ۲۴ ساعت قبل از شروع: بازگشت ۸۰٪ مبلغ به کیف پول.",
            "cancel5" to "لغو ۵ تا ۱۵ ساعت قبل از شروع: بازگشت ۵۰٪ مبلغ به کیف پول.",
            "cancel2" to "لغو ۲ تا ۵ ساعت قبل از شروع: بازگشت ۲۰٪ مبلغ به کیف پول.",
            "lateCancellation" to "لغو در زمان شروع یا بعد از آن طبق قانون لغو دیرهنگام، محرومیت ۳ روزه و ۲۰٪ افزایش هزینه برای اولویت رزرو بعدی اعمال می‌شود.",
            "noShow" to "عدم حضور مشتری طبق قانون No Show مدیریت محاسبه و ثبت می‌شود.",
            "vipPaymentDeadline" to "مهلت پرداخت کامل VIP: ۱۵ دقیقه. پرداخت موفق به تنهایی به معنی تأیید رزرو نیست."
        )
    }

    fun load(json: JSONObject) {
        val root = json.optJSONObject("settings") ?: json
        val r = root.optJSONObject("reservationRules") ?: JSONObject()
        val nDur = jsonIntArray(r, "normalDurationsMinutes")
        if (nDur.isNotBlank()) normalDurations = nDur
        val vDur = jsonIntArray(r, "vipDurationsMinutes")
        if (vDur.isNotBlank()) vipDurations = vDur

        if (r.has("vipMinDurationMinutes")) vipMin = int(r, "vipMinDurationMinutes")
        if (r.optString("vipStartTime", "").isNotBlank()) vipStart = r.optString("vipStartTime", "")
        if (r.optString("vipEndTime", "").isNotBlank()) vipEnd = r.optString("vipEndTime", "")
        if (r.has("paymentDeadlineMinutes")) paymentDeadline = int(r, "paymentDeadlineMinutes")
        if (r.has("vipPaymentDeadlineMinutes")) vipPaymentDeadline = int(r, "vipPaymentDeadlineMinutes")
        if (r.has("arrivalWarningMinutes")) arrivalWarningMinutes = int(r, "arrivalWarningMinutes")
        
        r.optJSONArray("arrivalReminderMinutes")?.let { a ->
            if (a.length() > 0) {
                arrivalReminderMinutes = (0 until a.length()).joinToString(",") { a.optInt(it).toString() }
            }
        }

        val pObj = r.optJSONObject("pricing") ?: JSONObject()
        if (pObj.has("ps4")) ps4Rate = int(pObj, "ps4")
        if (pObj.has("ps5")) ps5Rate = int(pObj, "ps5")
        if (pObj.has("simulator")) simulatorRate = int(pObj, "simulator")
        if (pObj.has("extraController")) extraControllerRate = int(pObj, "extraController")
        if (pObj.has("fullHall")) fullHallHourlyRate = int(pObj, "fullHall")

        val c = r.optJSONObject("cancellation") ?: JSONObject()
        listOf(
            "24" to "atOrAbove24h", "15" to "above15h", "5" to "above5h",
            "2" to "above2h", "within2h" to "within2h"
        ).forEach { (key, name) ->
            val x = c.optJSONObject(name) ?: JSONObject()
            if (x.has("gn")) gn[key] = int(x, "gn")
            if (x.has("lp")) lp[key] = int(x, "lp")
            if (x.has("walletPercent")) wallet[key] = int(x, "walletPercent")
        }
        if (c.has("atOrAbove24h")) threshold24 = int(c.optJSONObject("atOrAbove24h") ?: JSONObject(), "thresholdMinutes")
        if (c.has("above15h")) threshold15 = int(c.optJSONObject("above15h") ?: JSONObject(), "thresholdMinutes")
        if (c.has("above5h")) threshold5 = int(c.optJSONObject("above5h") ?: JSONObject(), "thresholdMinutes")
        if (c.has("above2h")) threshold2 = int(c.optJSONObject("above2h") ?: JSONObject(), "thresholdMinutes")
        
        val within = c.optJSONObject("within2h") ?: JSONObject()
        if (within.has("restrictionDays")) restrictionDays = int(within, "restrictionDays")
        if (within.has("surchargePercent")) surchargePercent = int(within, "surchargePercent")

        val late = r.optJSONObject("lateCancellation") ?: JSONObject()
        val noShow = r.optJSONObject("noShow") ?: JSONObject()
        if (late.has("gn")) gn["late"] = int(late, "gn")
        if (late.has("lp")) lp["late"] = int(late, "lp")
        if (late.has("walletPercent")) wallet["late"] = int(late, "walletPercent")
        if (noShow.has("gn")) gn["noShow"] = int(noShow, "gn")
        if (noShow.has("lp")) lp["noShow"] = int(noShow, "lp")
        if (noShow.has("walletPercent")) wallet["noShow"] = int(noShow, "walletPercent")

        val reward = r.optJSONObject("vipReward") ?: JSONObject()
        if (reward.has("gn")) vipGn = int(reward, "gn")
        if (reward.has("lp")) vipLp = int(reward, "lp")
        if (r.has("vipPrice")) vipPrice = r.optString("vipPrice", "120000")

        val m = r.optJSONObject("messages") ?: JSONObject()
        listOf("payment","arrival","vip","cancel24","cancel15","cancel5","cancel2","lateCancellation","noShow","vipPaymentDeadline").forEach { key ->
            val raw = m.optString(key, "")
            if (raw.isNotBlank()) {
                messages[key] = ReservationMessageFormatter.sanitize(
                    key = key,
                    rawText = raw,
                    paymentDeadline = paymentDeadline,
                    vipPaymentDeadline = vipPaymentDeadline,
                    restrictionDays = restrictionDays,
                    surchargePercent = surchargePercent
                )
            }
        }
    }

    fun buildSettings(): JSONObject {
        val r = JSONObject()
        r.put("normalDurationsMinutes", csvInts(normalDurations))
        r.put("vipDurationsMinutes", csvInts(vipDurations))
        r.put("vipMinDurationMinutes", vipMin.toIntOrNull() ?: 180)
        r.put("vipStartTime", vipStart)
        r.put("vipEndTime", vipEnd)
        r.put("paymentDeadlineMinutes", paymentDeadline.toIntOrNull() ?: 15)
        r.put("vipPaymentDeadlineMinutes", vipPaymentDeadline.toIntOrNull() ?: 15)
        r.put("arrivalWarningMinutes", arrivalWarningMinutes.toIntOrNull() ?: 15)
        r.put("arrivalReminderMinutes", org.json.JSONArray(arrivalReminderMinutes.split(",").mapNotNull { it.trim().toIntOrNull() }))

        val pricingObj = JSONObject()
            .put("ps4", ps4Rate.toLongOrNull() ?: 50000L)
            .put("ps5", ps5Rate.toLongOrNull() ?: 80000L)
            .put("simulator", simulatorRate.toLongOrNull() ?: 90000L)
            .put("extraController", extraControllerRate.toLongOrNull() ?: 15000L)
            .put("fullHall", fullHallHourlyRate.toLongOrNull() ?: 500000L)
        r.put("pricing", pricingObj)

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
                .put("restrictionDays", restrictionDays.toIntOrNull() ?: 3)
                .put("surchargePercent", surchargePercent.toIntOrNull() ?: 20))

        c.getJSONObject("atOrAbove24h").put("thresholdMinutes", threshold24.toIntOrNull() ?: 1440)
        c.getJSONObject("above15h").put("thresholdMinutes", threshold15.toIntOrNull() ?: 900)
        c.getJSONObject("above5h").put("thresholdMinutes", threshold5.toIntOrNull() ?: 300)
        c.getJSONObject("above2h").put("thresholdMinutes", threshold2.toIntOrNull() ?: 120)
        r.put("cancellation", c)
        r.put("lateCancellation", stage("late","late","late"))
        r.put("noShow", stage("noShow","noShow","noShow"))
        r.put("vipReward", JSONObject().put("gn", vipGn.toIntOrNull() ?: 50).put("lp", vipLp.toIntOrNull() ?: 20))
        r.put("vipPrice", vipPrice.toLongOrNull() ?: 120000L)
        r.put("messages", JSONObject(messages.toMap()))

        return JSONObject().put("reservationRules", r)
    }

    fun save() {
        scope.launch {
            saving = true
            val ok = SelfHostedManager.updateManagerConfiguration(buildSettings()) != null
            message = if (ok) "تنظیمات رزرو با موفقیت ذخیره شد." else "خطا در ذخیره‌سازی تنظیمات."
            saving = false
        }
    }

    LaunchedEffect(managerId) {
        loading = true
        val result = SelfHostedManager.fetchManagerConfiguration()
        if (result != null) {
            load(result)
        }
        loading = false
    }

    BackHandler(enabled = activeCategory != null) {
        activeCategory = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeCategory?.titleFa ?: "قوانین رزرو و قیمت‌گذاری",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeCategory != null) {
                            activeCategory = null
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(enabled = !saving, onClick = ::save) {
                        if (saving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "ذخیره")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                if (message.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(message, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            IconButton(onClick = { message = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "بستن", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = activeCategory,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "category_transition"
                ) { cat ->
                    if (cat == null) {
                        // Category Hub List View (Touch-based Category List)
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "برای تنظیم هر بخش، دسته‌بندی مربوطه را لمس کنید:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            ReservationCategory.values().forEach { category ->
                                Surface(
                                    onClick = { activeCategory = category },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 2.dp,
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(category.badgeColor.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = category.icon,
                                                contentDescription = null,
                                                tint = category.badgeColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = category.titleFa,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = category.subtitleFa,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                enabled = !saving,
                                onClick = ::save,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (saving) "در حال ذخیره‌سازی..." else "💾 ذخیره کلیه تنظیمات رزرو")
                            }
                        }
                    } else {
                        // Detailed Sub-Screen per Category
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (cat) {
                                ReservationCategory.DURATIONS -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("🎮 مدت‌های رزرو و زمان‌بندی VIP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("مدت‌های رزرو عادی (دقیقه، با کاما)", normalDurations) { normalDurations = it }
                                            RuleField("مدت‌های رزرو VIP (دقیقه، با کاما)", vipDurations) { vipDurations = it }
                                            RuleField("حداقل مدت رزرو VIP (دقیقه)", vipMin, isNumeric = true) { vipMin = it }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.weight(1f)) {
                                                    RuleField("ساعت شروع VIP (مثال 14:00)", vipStart) { vipStart = it }
                                                }
                                                Box(Modifier.weight(1f)) {
                                                    RuleField("ساعت پایان VIP (مثال 23:59)", vipEnd) { vipEnd = it }
                                                }
                                            }
                                        }
                                    }
                                }

                                ReservationCategory.PRICING -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("💰 نرخ پایه ایستگاه‌ها و کنسول‌ها (تومان / ساعت)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("قیمت پایه PS4", ps4Rate, isNumeric = true) { ps4Rate = it }
                                            RuleField("قیمت پایه PS5", ps5Rate, isNumeric = true) { ps5Rate = it }
                                            RuleField("قیمت پایه شبیه‌ساز / فرمان", simulatorRate, isNumeric = true) { simulatorRate = it }
                                            RuleField("هزینه دسته اضافه (به ازای هر دسته)", extraControllerRate, isNumeric = true) { extraControllerRate = it }
                                            RuleField("قیمت ساعتی رزرو کامل سالن", fullHallHourlyRate, isNumeric = true) { fullHallHourlyRate = it }
                                        }
                                    }
                                }

                                ReservationCategory.VIP_RULES -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("⭐ شرایط و اولویت‌های VIP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("قیمت ساعتی جایگاه VIP (تومان)", vipPrice, isNumeric = true) { vipPrice = it }
                                            RuleField("حداقل زمان رزرو VIP (دقیقه)", vipMin, isNumeric = true) { vipMin = it }
                                            RuleField("مهلت پرداخت VIP (دقیقه)", vipPaymentDeadline, isNumeric = true) { vipPaymentDeadline = it }
                                            RuleField("پاداش GN رزرو VIP", vipGn, isNumeric = true) { vipGn = it }
                                            RuleField("پاداش LP رزرو VIP", vipLp, isNumeric = true) { vipLp = it }

                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Text("👑 سطوح مجاز VIP: DIAMOND و GOLD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("اولویت رزرو جایگاه VIP به صورت خودکار با کاربران Diamond و سپس Gold خواهد بود.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                }
                                            }
                                        }
                                    }
                                }

                                ReservationCategory.PAYMENT -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("💳 مهلت‌های پرداخت و زمان حضور", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("مهلت پرداخت رزرو عادی (دقیقه)", paymentDeadline, isNumeric = true) { paymentDeadline = it }
                                            RuleField("مهلت پرداخت رزرو VIP (دقیقه)", vipPaymentDeadline, isNumeric = true) { vipPaymentDeadline = it }
                                            RuleField("زمان هشدار لزوم حضور قبل از رزرو (دقیقه)", arrivalWarningMinutes, isNumeric = true) { arrivalWarningMinutes = it }
                                            RuleField("یادآورهای رزرو به مشتری (دقیقه قبل - مثال: 120,30,15,5)", arrivalReminderMinutes) { arrivalReminderMinutes = it }
                                        }
                                    }
                                }

                                ReservationCategory.CANCELLATION -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("❌ قوانین لغو، استرداد و جریمه", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            
                                            listOf(
                                                "24" to "۲۴ ساعت یا بیشتر قبل از شروع",
                                                "15" to "بین ۱۵ تا ۲۴ ساعت قبل",
                                                "5" to "بین ۵ تا ۱۵ ساعت قبل",
                                                "2" to "بین ۲ تا ۵ ساعت قبل",
                                                "within2h" to "کمتر از ۲ ساعت قبل",
                                                "late" to "لغو دیرهنگام (زمان شروع یا بعد از آن)",
                                                "noShow" to "عدم حضور (No Show)"
                                            ).forEach { (k, title) ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                                ) {
                                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Text("📍 $title", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Box(Modifier.weight(1f)) {
                                                                RuleField("بازگشت کیف‌پول %", wallet[k] ?: "", isNumeric = true) { wallet[k] = it }
                                                            }
                                                            Box(Modifier.weight(1f)) {
                                                                RuleField("جریمه GN", gn[k] ?: "", isNumeric = true) { gn[k] = it }
                                                            }
                                                            Box(Modifier.weight(1f)) {
                                                                RuleField("جریمه LP", lp[k] ?: "", isNumeric = true) { lp[k] = it }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(4.dp))
                                            RuleField("مدت محرومیت لغو دیرهنگام (روز)", restrictionDays, isNumeric = true) { restrictionDays = it }
                                            RuleField("درصد افزایش هزینه رزرو بعدی برای لغو دیرهنگام (%)", surchargePercent, isNumeric = true) { surchargePercent = it }
                                        }
                                    }
                                }

                                ReservationCategory.GN_LP -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("🏆 پاداش‌ها و جریمه‌های امتیازات GN و LP", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("پاداش GN به ازای رزرو VIP", vipGn, isNumeric = true) { vipGn = it }
                                            RuleField("پاداش LP به ازای رزرو VIP", vipLp, isNumeric = true) { vipLp = it }
                                            RuleField("جریمه GN در عدم حضور (No-Show)", gn["noShow"] ?: "100", isNumeric = true) { gn["noShow"] = it }
                                            RuleField("جریمه LP در عدم حضور (No-Show)", lp["noShow"] ?: "50", isNumeric = true) { lp["noShow"] = it }
                                        }
                                    }
                                }

                                ReservationCategory.FULL_HALL -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("🏟 رزرو سالن کامل و اختصاصی", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("قیمت ساعتی رزرو کامل سالن (تومان)", fullHallHourlyRate, isNumeric = true) { fullHallHourlyRate = it }
                                            RuleField("حداقل مدت رزرو اختصاصی (ساعت)", "2", isNumeric = true) { }
                                            RuleField("درصد پیش‌پرداخت رزرو سالن (%)", depositPercent, isNumeric = true) { depositPercent = it }
                                        }
                                    }
                                }

                                ReservationCategory.POLICY_TEXTS -> {
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text("📝 متن‌های سفارشی قوانین و پیام‌ها برای مشتری", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            RuleField("متن مهلت پرداخت رزرو", messages["payment"] ?: "", multiline = true) { messages["payment"] = it }
                                            RuleField("متن هشدار حضور قبل از شروع", messages["arrival"] ?: "", multiline = true) { messages["arrival"] = it }
                                            RuleField("متن توضیحات جایگاه VIP", messages["vip"] ?: "", multiline = true) { messages["vip"] = it }
                                            RuleField("متن قانون لغو ۲۴ ساعت یا بیشتر", messages["cancel24"] ?: "", multiline = true) { messages["cancel24"] = it }
                                            RuleField("متن قانون لغو ۱۵ ساعت", messages["cancel15"] ?: "", multiline = true) { messages["cancel15"] = it }
                                            RuleField("متن قانون لغو ۵ ساعت", messages["cancel5"] ?: "", multiline = true) { messages["cancel5"] = it }
                                            RuleField("متن قانون لغو ۲ ساعت", messages["cancel2"] ?: "", multiline = true) { messages["cancel2"] = it }
                                            RuleField("متن قانون لغو دیرهنگام", messages["lateCancellation"] ?: "", multiline = true) { messages["lateCancellation"] = it }
                                            RuleField("متن قانون No Show", messages["noShow"] ?: "", multiline = true) { messages["noShow"] = it }
                                            RuleField("متن مهلت پرداخت VIP", messages["vipPaymentDeadline"] ?: "", multiline = true) { messages["vipPaymentDeadline"] = it }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { activeCategory = null },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("بازگشت به دسته‌ها")
                                }

                                Button(
                                    enabled = !saving,
                                    onClick = ::save,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (saving) "در حال ذخیره..." else "💾 ذخیره این بخش")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
