package com.example.ui
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.network.AtomicReservationRequest
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomerFullHallTab(viewModel: GameNetViewModel) {
    val customer by SelfHostedManager.currentLoggedInCustomer.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var rules by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedDuration by remember { mutableIntStateOf(0) }
    var selectedStartMillis by remember { mutableLongStateOf(0L) }
    var selectedPrice by remember { mutableDoubleStateOf(0.0) }
    var lastReservationId by remember { mutableLongStateOf(0L) }
    var lastReservationAmount by remember { mutableLongStateOf(0L) }
    var paymentTrackingCode by remember { mutableStateOf("") }
    var paymentMethodText by remember { mutableStateOf("") }
    var paymentMessage by remember { mutableStateOf("") }
    var paymentSubmitting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    LaunchedEffect(customer?.id, selectedDuration) {
        loading = true
        rules = SelfHostedManager.fetchReservationRules(durationMinutes = selectedDuration)
        val arr = rules?.optJSONObject("rules")?.optJSONArray("vipDurationsMinutes")
        if (arr != null && arr.length() > 0) selectedDuration = arr.optInt(0)
        loading = false
    }

    val ruleObject = remember(rules) { rules?.optJSONObject("rules") ?: JSONObject() }
    val vipDurations = remember(ruleObject) {
        val arr = ruleObject.optJSONArray("vipDurationsMinutes") ?: JSONArray()
        buildList { for (i in 0 until arr.length()) if (arr.optInt(i) > 0) add(arr.optInt(i)) }
    }
    val minVip = ruleObject.optInt("vipMinDurationMinutes", 0)
    val vipPrice = ruleObject.optLong("vipPrice", 0L)
    val messages = ruleObject.optJSONObject("messages") ?: JSONObject()
    LaunchedEffect(selectedDuration, selectedStartMillis) {
        if (selectedDuration > 0 && selectedStartMillis > 0L) {
            val preview = SelfHostedManager.previewReservationPricing(
                com.example.data.network.PricingPreviewRequest(
                    reservationType = "VIP",
                    durationMinutes = selectedDuration,
                    reservationTimeMillis = selectedStartMillis
                )
            )
            selectedPrice = preview?.finalPrice ?: 0.0
        }
    }

    val startLabel = if (selectedStartMillis <= 0L) "زمان شروع را انتخاب کنید" else
        SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale("fa", "IR")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }.format(Date(selectedStartMillis))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("رزرو VIP / اختصاصی", style = MaterialTheme.typography.titleLarge)
        Text(
            "رزرو VIP فقط برای سطح Gold و Diamond قابل درخواست مستقیم است؛ تأیید نهایی فقط پس از پرداخت کامل و تأیید مدیریت انجام می‌شود.",
            style = MaterialTheme.typography.bodySmall
        )

        if (customer?.tier?.uppercase() !in setOf("GOLD", "DIAMOND")) {
            Text("درخواست مستقیم VIP برای سطح حساب فعلی شما فعال نیست.", color = MaterialTheme.colorScheme.error)
        } else if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Text("مدت VIP:")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vipDurations.forEach { minutes ->
                    FilterChip(
                        selected = selectedDuration == minutes,
                        onClick = { selectedDuration = minutes },
                        label = { Text(if (minutes % 60 == 0) "${minutes / 60} ساعت" else "${minutes} دقیقه") }
                    )
                }
            }

            Text("حداقل مدت VIP: ${minVip} دقیقه")
            if (vipPrice > 0) Text("قیمت تنظیم‌شده VIP: ${vipPrice}")

            val vipMsg = ReservationMessageFormatter.sanitize("vip", messages.optString("vip"))
            if (vipMsg.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) { Text(vipMsg, Modifier.padding(12.dp)) }
            }
            val vipPaymentMsg = ReservationMessageFormatter.sanitize("vipPaymentDeadline", messages.optString("vipPaymentDeadline"))
            if (vipPaymentMsg.isNotBlank()) {
                Card(Modifier.fillMaxWidth()) { Text(vipPaymentMsg, Modifier.padding(12.dp)) }
            }

            OutlinedButton(
                onClick = {
                    val tz = TimeZone.getTimeZone("Asia/Tehran")
                    val cal = Calendar.getInstance(tz)
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance(tz).apply {
                                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    picked.set(Calendar.HOUR_OF_DAY, hour); picked.set(Calendar.MINUTE, minute)
                                    selectedStartMillis = picked.timeInMillis
                                },
                                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
                            ).show()
                        },
                        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(startLabel)
            }

            Button(
                enabled = customer != null && vipDurations.contains(selectedDuration) &&
                    selectedDuration >= minVip && selectedStartMillis > System.currentTimeMillis(),
                onClick = {
                    val c = customer ?: return@Button
                    scope.launch {
                        val response = SelfHostedManager.submitAtomicReservation(
                            AtomicReservationRequest(
                                reservationType = "VIP",
                                stationId = null,
                                durationMinutes = selectedDuration,
                                reservationTimeMillis = selectedStartMillis,
                                customerName = c.fullName,
                                customerPhone = c.phoneNumber,
                                idempotencyKey = "vip-request-${c.id}-${selectedStartMillis}-${selectedDuration}"
                            )
                        )
                        message = response?.message?.ifBlank { "درخواست VIP ثبت شد و منتظر پرداخت کامل و تأیید مدیریت است." }
                            ?: "ثبت درخواست VIP ناموفق بود."
                        lastReservationId = response?.reservationId ?: 0L
                        lastReservationAmount = selectedPrice.toLong()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Event, null)
                Spacer(Modifier.width(8.dp))
                Text("ثبت درخواست VIP")
            }
        }

        if (selectedPrice > 0.0) {
            Text("قیمت نهایی محاسبه‌شده توسط سرور: " + selectedPrice.toLong(), fontWeight = FontWeight.Bold)
        }

        if (lastReservationId > 0L && lastReservationAmount > 0L) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("گزارش پرداخت VIP", fontWeight = FontWeight.Bold)
                    Text("رزرو شماره " + lastReservationId + " | مبلغ: " + lastReservationAmount)
                    OutlinedTextField(paymentMethodText, { paymentMethodText = it }, label = { Text("روش پرداخت") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(paymentTrackingCode, { paymentTrackingCode = it }, label = { Text("کد پیگیری / شماره رسید") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        enabled = !paymentSubmitting && paymentTrackingCode.isNotBlank(),
                        onClick = {
                            paymentSubmitting = true
                            scope.launch {
                                val result = SelfHostedManager.requestReservationPaymentReview(
                                    lastReservationId, lastReservationAmount, paymentMethodText, paymentTrackingCode
                                )
                                paymentMessage = result.fold({ it }, { it.message ?: "ثبت گزارش پرداخت ناموفق بود." })
                                paymentSubmitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (paymentSubmitting) "در حال ارسال..." else "ارسال گزارش پرداخت") }
                    if (paymentMessage.isNotBlank()) Text(paymentMessage, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}
