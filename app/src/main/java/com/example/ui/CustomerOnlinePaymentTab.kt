package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.DecimalFormat

fun parseCards(jsonStr: String): List<PaymentCard> {
    val list = mutableListOf<PaymentCard>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val num = obj.optString("cardNumber").ifEmpty { obj.optString("number") }
            val own = obj.optString("ownerName").ifEmpty { obj.optString("owner") }
            val bank = obj.optString("bank", "بانک")
            val displayName = if (own.isNotBlank()) "$bank - $own" else bank
            if (num.isNotBlank()) {
                list.add(
                    PaymentCard(
                        id = obj.optString("id", i.toString()),
                        ownerName = displayName,
                        cardNumber = num,
                        isActive = obj.optBoolean("isActive", true)
                    )
                )
            }
        }
    } catch (e: Exception) {}
    return list
}

fun parseGateways(jsonStr: String): List<PaymentGateway> {
    val list = mutableListOf<PaymentGateway>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PaymentGateway(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    isActive = obj.optBoolean("isActive", true)
                )
            )
        }
    } catch (e: Exception) {}
    return list
}

fun parseCryptos(jsonStr: String): List<PaymentCrypto> {
    val list = mutableListOf<PaymentCrypto>()
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PaymentCrypto(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    network = obj.optString("network"),
                    address = obj.optString("address"),
                    isActive = obj.optBoolean("isActive", true)
                )
            )
        }
    } catch (e: Exception) {}
    return list
}

@Composable
fun CustomerOnlinePaymentTab(viewModel: GameNetViewModel) {
    var selectedSection by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedSection,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("شارژ / تسویه بدهی", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("خرید GN", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 },
                text = { Text("انتقال GN", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (selectedSection) {
                0 -> ChargeAccountSection(viewModel)
                1 -> BuyGnSection(viewModel)
                2 -> TransferGnSection(viewModel)
            }
        }
    }
}

@Composable
fun ChargeAccountSection(viewModel: GameNetViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val customers by viewModel.customers.collectAsState()
    val allCloudCustomers by SelfHostedManager.allCloudCustomers.collectAsState()
    val currentAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()

    val customer = remember(customers, allCloudCustomers, currentAuth) {
        val phone = currentAuth?.phoneNumber?.trim() ?: ""
        val authId = currentAuth?.id ?: 0L
        val cloudCust = allCloudCustomers.find { 
            (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) 
        }
        if (cloudCust != null) {
            cloudCust
        } else {
            customers.find { (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) }
                ?: currentAuth
                ?: Customer(fullName = "مشتری عزیز", phoneNumber = "")
        }
    }

    var amountTomanText by remember { mutableStateOf("") }
    var trackingCodeText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("کارت به کارت") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionMessage by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val amountToman = amountTomanText.toLongOrNull() ?: 0L
    val formatter = remember { DecimalFormat("#,###") }

    val cardsJson by viewModel.gnPaymentCards.collectAsState()
    val gatewaysJson by viewModel.gnPaymentGateways.collectAsState()
    val cryptosJson by viewModel.gnPaymentCryptos.collectAsState()

    val cards = remember(cardsJson) { parseCards(cardsJson) }
    val gateways = remember(gatewaysJson) { parseGateways(gatewaysJson) }
    val cryptos = remember(cryptosJson) { parseCryptos(cryptosJson) }

    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Balances Overview Mini Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("اعتبار تومانی کیف پول:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${formatter.format(customer.credit)} تومان",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF2E7D32)
                    )
                }

                VerticalDivider(modifier = Modifier.height(28.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Column(horizontalAlignment = Alignment.End) {
                    Text("بدهی جاری شما:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${formatter.format(customer.debt)} تومان",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (customer.debt > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Amount Input Field
        OutlinedTextField(
            value = amountTomanText,
            onValueChange = { amountTomanText = it.filter { ch -> ch.isDigit() } },
            label = { Text("مبلغ واریزی به تومان (مثال: 50000)") },
            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (amountToman > 0) {
            Text(
                text = "مبلغ به حروف: ${formatter.format(amountToman)} تومان",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Quick Settlement Buttons if Customer has Debt
        if (customer.debt > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { amountTomanText = customer.debt.toLong().toString() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("تسویه کامل بدهی (${formatter.format(customer.debt)})", fontSize = 10.sp)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        Text("روش‌های واریز و شماره حساب‌ها:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // Gateways
        if (gateways.any { it.isActive }) {
            Text("درگاه مستقیم آنلاین:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            gateways.filter { it.isActive }.forEach { gw ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPaymentMethod = "درگاه آنلاین (${gw.name})"
                            try { uriHandler.openUri(gw.url) } catch (e: Exception) {}
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(gw.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("پرداخت مستقیم ↗", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card to Card
        val activeCards = cards.filter { it.isActive }

        Text("شماره کارت‌های واریزی (کارت به کارت):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        if (activeCards.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "شماره کارتی توسط این مدیریت تعریف نشده است. جهت پرداخت با مدیریت تماس حاصل فرمایید.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            activeCards.forEach { card ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.cardNumber, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, fontSize = 14.sp)
                            Text(card.ownerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(card.cardNumber.replace("-", "").replace(" ", "")))
                                selectedPaymentMethod = "کارت به کارت (${card.ownerName})"
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("کپی", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Cryptos
        if (cryptos.any { it.isActive }) {
            Text("آدرس‌های پرداخت رمزارز (Crypto):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            cryptos.filter { it.isActive }.forEach { cr ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${cr.name} (${cr.network})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(cr.address, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(cr.address))
                            selectedPaymentMethod = "رمز ارز (${cr.name})"
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }

        // Payment Review Request Form
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ارسال کد پیگیری و رسید به مدیریت:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "پس از واریز مبلغ، کد رهگیری 4 یا 6 رقمی را وارد کنید تا حساب شما فورا تایید و شارژ شود.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = trackingCodeText,
                    onValueChange = { trackingCodeText = it },
                    label = { Text("کد پیگیری یا 4 رقم آخر شماره کارت واریزی") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("توضیحات تکمیلی (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (amountToman <= 0) {
                            submissionMessage = "لطفا مبلغ واریزی را مشخص کنید."
                            showDialog = true
                            return@Button
                        }
                        isSubmitting = true
                        coroutineScope.launch {
                            val res = SelfHostedManager.requestPaymentReview(
                                customer = customer,
                                amountToman = amountToman,
                                paymentMethod = selectedPaymentMethod,
                                trackingCode = trackingCodeText,
                                note = noteText
                            )
                            isSubmitting = false
                            submissionMessage = res.getOrElse { it.localizedMessage ?: "خطا در ثبت رسید" }
                            showDialog = true
                            if (res.isSuccess) {
                                amountTomanText = ""
                                trackingCodeText = ""
                                noteText = ""
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ثبت و بررسی رسید پرداخت توسط مدیریت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Contact Support Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sms = viewModel.gnContactSms.collectAsState().value
            val bale = viewModel.gnContactBale.collectAsState().value

            OutlinedButton(
                onClick = { try { uriHandler.openUri("sms:$sms") } catch (e: Exception) {} },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("پیامک به مدیریت 📩", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { try { uriHandler.openUri("https://ble.ir/${bale.removePrefix("@")}") } catch (e: Exception) {} },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("رسید در بله / پیام‌رسان 📤", fontSize = 11.sp)
            }
        }
    }

    if (showDialog && submissionMessage != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("وضعیت ثبت رسید", fontWeight = FontWeight.Bold) },
            text = { Text(submissionMessage!!) },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("متوجه شدم")
                }
            }
        )
    }
}

@Composable
fun BuyGnSection(viewModel: GameNetViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val ratio by viewModel.gnPurchaseRateToman.collectAsState()
    val effectiveRatio: Double = if (ratio > 0L) ratio.toDouble() else 500.0 // Default 500 Toman per 1 GN

    val customers by viewModel.customers.collectAsState()
    val allCloudCustomers by SelfHostedManager.allCloudCustomers.collectAsState()
    val currentAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()

    val customer = remember(customers, allCloudCustomers, currentAuth) {
        val phone = currentAuth?.phoneNumber?.trim() ?: ""
        val authId = currentAuth?.id ?: 0L
        val cloudCust = allCloudCustomers.find { 
            (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) 
        }
        if (cloudCust != null) {
            cloudCust
        } else {
            customers.find { (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) }
                ?: currentAuth
                ?: Customer(fullName = "مشتری عزیز", phoneNumber = "")
        }
    }

    var gnAmountText by remember { mutableStateOf("10") }
    var tomanAmountText by remember { mutableStateOf((10 * effectiveRatio).toLong().toString()) }
    var trackingCodeText by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf("کارت به کارت") }
    var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val formatter = remember { DecimalFormat("#,###") }
    val gnAmount = gnAmountText.toDoubleOrNull() ?: 0.0
    val totalToman = (gnAmount * effectiveRatio).toLong()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Price Ratio Info Banner
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Column {
                    Text("خرید اعتبار رسمی GN گیم‌نکسا", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        text = "نرخ تبدیل: هر 1 GN = ${formatter.format(effectiveRatio)} تومان",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Amount Selection Preset Chips
        Text("انتخاب مقدار سریع یا تنظیم اهرم (بدون محدودیت):", fontWeight = FontWeight.Bold, fontSize = 12.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(10, 50, 100, 500, 1000, 5000, 10000).forEach { preset ->
                val isSel = gnAmount.toInt() == preset
                Surface(
                    onClick = {
                        gnAmountText = preset.toString()
                        tomanAmountText = (preset * effectiveRatio).toLong().toString()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (preset >= 1000) "${preset / 1000}k" else "$preset",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Interactive Slider (اهرم انتخاب مقدار GN - شناور و کاملاً بدون محدودیت)
        val sliderMax = maxOf(10000f, gnAmount.toFloat())
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اهرم تعیین میزان GN (کاملاً آزاد):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${formatter.format(gnAmount)} GN", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                Slider(
                    value = gnAmount.toFloat().coerceIn(1f, sliderMax),
                    onValueChange = { newVal ->
                        val roundedVal = newVal.toInt()
                        gnAmountText = roundedVal.toString()
                        tomanAmountText = (roundedVal * effectiveRatio).toLong().toString()
                    },
                    valueRange = 1f..sliderMax,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 GN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("500 GN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatter.format(sliderMax.toLong())} GN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Dual Manual Input Fields (ورودی کاملاً آزاد GN و تومان با تبدیل دوطرفه اتوماتیک)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = gnAmountText,
                onValueChange = { input ->
                    val clean = input.filter { ch -> ch.isDigit() || ch == '.' }
                    gnAmountText = clean
                    val parsedGn = clean.toDoubleOrNull() ?: 0.0
                    tomanAmountText = if (parsedGn > 0) (parsedGn * effectiveRatio).toLong().toString() else ""
                },
                label = { Text("تعداد GN (آزاد و دلخواه)") },
                trailingIcon = { Text("GN", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = tomanAmountText,
                onValueChange = { input ->
                    val clean = input.filter { ch -> ch.isDigit() }
                    tomanAmountText = clean
                    val parsedToman = clean.toDoubleOrNull() ?: 0.0
                    if (parsedToman > 0 && effectiveRatio > 0) {
                        val calcGn = parsedToman / effectiveRatio
                        gnAmountText = if (calcGn % 1.0 == 0.0) calcGn.toLong().toString() else String.format("%.1f", calcGn)
                    } else {
                        gnAmountText = ""
                    }
                },
                label = { Text("مبلغ تومان (آزاد و دلخواه)") },
                trailingIcon = { Text("تومان", modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        // Calculation Summary Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مقدار شارژ GN:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatter.format(gnAmount)} GN", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("مبلغ قابل پرداخت:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${formatter.format(totalToman)} تومان", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF2E7D32))
                }
                Text(
                    text = "💡 موجودی فعلی در دسترس شما: ${formatter.format(customer.availableGn)} GN",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = trackingCodeText,
            onValueChange = { trackingCodeText = it },
            label = { Text("کد پیگیری پرداخت یا رسید واریزی") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Button(
            onClick = {
                if (gnAmount <= 0) {
                    statusMessage = "لطفا مقدار GN معتبری وارد کنید."
                    showDialog = true
                    return@Button
                }
                isSubmitting = true
                coroutineScope.launch {
                    val res = SelfHostedManager.buyGnDirect(
                        customer = customer,
                        gnAmount = gnAmount.toLong(),
                        totalToman = totalToman,
                        paymentMethod = selectedMethod,
                        trackingCode = trackingCodeText
                    )
                    isSubmitting = false
                    statusMessage = res.getOrElse { it.localizedMessage ?: "خطا در ثبت درخواست خرید GN" }
                    showDialog = true
                    if (res.isSuccess) {
                        trackingCodeText = ""
                    }
                }
            },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ثبت درخواست خرید و ارسال به مدیریت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (showDialog && statusMessage != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("درخواست خرید GN", fontWeight = FontWeight.Bold) },
            text = { Text(statusMessage!!) },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("تایید")
                }
            }
        )
    }
}

@Composable
fun TransferGnSection(viewModel: GameNetViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val customers by viewModel.customers.collectAsState()
    val currentAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()
    val allCloudCustomers by SelfHostedManager.allCloudCustomers.collectAsState()

    val senderCustomer = remember(customers, allCloudCustomers, currentAuth) {
        val phone = currentAuth?.phoneNumber?.trim() ?: ""
        val authId = currentAuth?.id ?: 0L
        val cloudCust = allCloudCustomers.find { 
            (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) 
        }
        if (cloudCust != null) {
            cloudCust
        } else {
            customers.find { (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) }
                ?: currentAuth
                ?: Customer(fullName = "مشتری عزیز", phoneNumber = "", availableGn = 0L)
        }
    }

    var targetQuery by remember { mutableStateOf("") }
    var transferAmountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var resultDialogMessage by remember { mutableStateOf<String?>(null) }

    val cleanQuery = targetQuery.trim()
    val matchedTarget = remember(cleanQuery, customers, allCloudCustomers) {
        if (cleanQuery.length < 3) null
        else {
            allCloudCustomers.find {
                it.phoneNumber == cleanQuery ||
                it.id.toString() == cleanQuery ||
                (it.inviteCode.isNotBlank() && it.inviteCode.equals(cleanQuery, ignoreCase = true))
            } ?: customers.find {
                it.phoneNumber == cleanQuery ||
                it.id.toString() == cleanQuery ||
                (it.inviteCode.isNotBlank() && it.inviteCode.equals(cleanQuery, ignoreCase = true))
            }
        }
    }

    val feeRate = 0.05 // 5% fee
    val amount = transferAmountText.toDoubleOrNull() ?: 0.0
    val netAmount = (amount * (1.0 - feeRate)).coerceAtLeast(0.0)
    val feeAmount = amount * feeRate
    val formatter = remember { DecimalFormat("#,###.##") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sender Available Balance Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("موجودی در دسترس شما برای انتقال:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${formatter.format(senderCustomer.availableGn)} GN",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text("کارمزد: 5٪", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }

        // Target Customer Input
        OutlinedTextField(
            value = targetQuery,
            onValueChange = { targetQuery = it },
            label = { Text("شماره همراه، کد معرف یا شناسه کاربری گیرنده") },
            leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Matched Customer Banner
        if (matchedTarget != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                    Column {
                        Text("گیرنده: ${matchedTarget.fullName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                        Text("شماره تماس: ${matchedTarget.phoneNumber} | کد: ${matchedTarget.inviteCode.ifBlank { matchedTarget.id.toString() }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (cleanQuery.length >= 4) {
            Text("کاربری با این مشخصات یافت نشد (لطفا شماره موبایل دقیق را وارد کنید)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }

        // Transfer Amount Input
        OutlinedTextField(
            value = transferAmountText,
            onValueChange = { transferAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("مقدار GN برای انتقال") },
            trailingIcon = { Text("GN", modifier = Modifier.padding(end = 12.dp), fontWeight = FontWeight.Bold) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (amount > 0) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("مقدار کسر شده از شما:", fontSize = 11.sp)
                        Text("${formatter.format(amount)} GN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("کارمزد انتقال شبکه (5٪):", fontSize = 11.sp)
                        Text("${formatter.format(feeAmount)} GN", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("خالص دریافتی گیرنده:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${formatter.format(netAmount)} GN", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("پیام یا توضیحات انتقال (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Button(
            onClick = {
                if (amount > senderCustomer.availableGn) {
                    resultDialogMessage = "موجودی اعتبار GN شما برای این انتقال کافی نیست."
                    return@Button
                }
                showConfirmDialog = true
            },
            enabled = matchedTarget != null && amount > 0 && !isSubmitting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("بررسی و تایید نهایی انتقال GN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    if (showConfirmDialog && matchedTarget != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("تایید نهایی انتقال اعتبار GN", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("آیا از انتقال مبلغ زیر اطمینان دارید؟")
                    Text("• گیرنده: ${matchedTarget.fullName} (${matchedTarget.phoneNumber})", fontWeight = FontWeight.Bold)
                    Text("• مبلغ کسر شده: ${formatter.format(amount)} GN")
                    Text("• خالص واریزی به گیرنده: ${formatter.format(netAmount)} GN", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isSubmitting = true
                        coroutineScope.launch {
                            val res = SelfHostedManager.transferGnDirect(
                                senderPhoneOrId = senderCustomer.phoneNumber.ifBlank { senderCustomer.id.toString() },
                                receiverPhoneOrId = matchedTarget.phoneNumber.ifBlank { matchedTarget.id.toString() },
                                amount = amount.toLong(),
                                description = noteText
                            )
                            isSubmitting = false
                            resultDialogMessage = res.getOrElse { it.localizedMessage ?: "خطا در انتقال اعتبار" }
                            if (res.isSuccess) {
                                transferAmountText = ""
                                targetQuery = ""
                                noteText = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("بله، انتقال یابد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (resultDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { resultDialogMessage = null },
            title = { Text("نتیجه انتقال اعتبار", fontWeight = FontWeight.Bold) },
            text = { Text(resultDialogMessage!!) },
            confirmButton = {
                Button(onClick = { resultDialogMessage = null }) {
                    Text("متوجه شدم")
                }
            }
        )
    }
}
