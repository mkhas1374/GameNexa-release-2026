package com.example.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.SubscriptionPlanDto
import com.example.util.JalaliCalendarHelper
import com.example.util.toPersianDigits
import kotlinx.coroutines.launch

@Composable
fun SubscriptionActivationScreen(viewModel: GameNetViewModel, isEmbedded: Boolean = false) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // 0 -> خرید و تمدید اشتراک, 1 -> اطلاعات اشتراک فعلی
    var selectedOptionTab by remember { mutableIntStateOf(0) }

    val lang by viewModel.language.collectAsState()
    val plans by viewModel.subscriptionPlans.collectAsState()
    val licenseState by viewModel.licenseState.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()

    var selectedPlanForPurchase by remember { mutableStateOf<SubscriptionPlanDto?>(null) }
    var couponCodeInput by remember { mutableStateOf("") }
    var userPhoneInput by remember { mutableStateOf("") }
    var userNameInput by remember { mutableStateOf("") }
    var gameNetNameInput by remember { mutableStateOf("") }
    var showPaymentDialog by remember { mutableStateOf(false) }

    var selectedGateway by remember { mutableStateOf("ZARINPAL") }
    var paymentState by remember { mutableStateOf("NEW") }
    var pendingTxnId by remember { mutableStateOf("") }
    var finalPriceAmount by remember { mutableDoubleStateOf(0.0) }

    // Direct manual activation state
    var manualLicenseCode by remember { mutableStateOf("") }
    var isManualActivating by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(false) }

    // Restore by mobile dialog
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restorePhoneInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val deviceId = remember { viewModel.getDeviceId() }
    val deviceModel = remember { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}" }

    LaunchedEffect(Unit) {
        viewModel.fetchSubscriptionPlans()
        viewModel.checkLicenseStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // دو گزینه اصلی در تب اشتراک: ۱. خرید و تمدید اشتراک | ۲. اطلاعات اشتراک فعلی
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // گزینه ۱: خرید و تمدید اشتراک
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedOptionTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedOptionTab = 0
                            focusManager.clearFocus()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = if (lang == "fa") "خرید و تمدید اشتراک" else "Buy / Renew",
                            tint = if (selectedOptionTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "fa") "۱. خرید و تمدید اشتراک" else "1. Buy / Renew",
                            fontSize = 12.5.sp,
                            fontWeight = if (selectedOptionTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedOptionTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // گزینه ۲: اطلاعات اشتراک فعلی
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedOptionTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedOptionTab = 1
                            focusManager.clearFocus()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = if (lang == "fa") "اطلاعات اشتراک فعلی" else "Current Subscription",
                            tint = if (selectedOptionTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "fa") "۲. اطلاعات اشتراک فعلی" else "2. Current Subscription",
                            fontSize = 12.5.sp,
                            fontWeight = if (selectedOptionTab == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedOptionTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (selectedOptionTab) {
            0 -> {
                // ==================== گزینه ۱: خرید و تمدید اشتراک ====================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // کارت معرفی
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (lang == "fa") "پلن‌های اشتراک گیم‌نکسا" else "GameNexa Subscription Plans",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (lang == "fa") "دسترسی کامل، آپدیت مداوم و پشتیبانی اختصاصی" else "Full access, regular updates, and dedicated support",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // لیست پلن‌های اشتراک
                    items(plans) { plan ->
                        val isYearly = plan.id == "YEARLY" || (plan.durationDays ?: 0) >= 360
                        val isThreeMonths = plan.id == "THREE_MONTHS" || (plan.durationDays ?: 0) == 90
                        val priceFormatted = if (lang == "fa") {
                            "%,.0f".format(plan.price ?: 0.0).toPersianDigits()
                        } else {
                            "%,.0f".format(java.util.Locale.US, plan.price ?: 0.0)
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isYearly) 4.dp else 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isYearly) {
                                        Modifier.border(
                                            2.dp,
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFF59E0B), Color(0xFFEAB308), Color(0xFFF59E0B))
                                            ),
                                            RoundedCornerShape(16.dp)
                                        )
                                    } else Modifier
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isYearly) Icons.Default.WorkspacePremium else if (isThreeMonths) Icons.Default.Loyalty else Icons.Default.CardMembership,
                                            contentDescription = null,
                                            tint = if (isYearly) Color(0xFFD97706) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (lang == "fa") (plan.name ?: "") else { if (plan.id == "YEARLY" || (plan.durationDays ?: 0) >= 360) "1 Year Subscription" else if (plan.id == "THREE_MONTHS" || (plan.durationDays ?: 0) in 80..100) "3 Months Subscription" else "1 Month Subscription" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    if (isYearly) {
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color(0xFFFEF3C7)
                                        ) {
                                            Text(
                                                text = if (lang == "fa") "پیشنهاد ویژه اقتصادی ⭐" else "Best Value ⭐",
                                                color = Color(0xFF92400E),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = if (lang == "fa") "مبلغ سرمایه‌گذاری:" else "Price / Investment:",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = priceFormatted,
                                                fontSize = 19.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isYearly) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (lang == "fa") "تومان" else "Toman",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            selectedPlanForPurchase = plan
                                            showPaymentDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isYearly) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Payment,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (lang == "fa") "خرید و فعال‌سازی" else "Buy & Activate",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!plan.savingText.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = plan.savingText ?: "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // بخش فعال‌سازی دستی با کد لایسنس یا بازیابی
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (lang == "fa") "فعال‌سازی با کد لایسنس یا بازیابی اشتراک" else "Activate via License Code or Restore",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                OutlinedTextField(
                                    value = manualLicenseCode,
                                    onValueChange = { manualLicenseCode = it.trim() },
                                    label = { Text(if (lang == "fa") "کد لایسنس دریافتی (مثال: GN-XXXX-XXXX)" else "License Code (e.g. GN-XXXX-XXXX)") },
                                    placeholder = { Text("GN-...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    })
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (manualLicenseCode.isBlank()) {
                                                Toast.makeText(context, if (lang == "fa") "لطفاً کد لایسنس را وارد کنید" else "Please enter license code", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            isManualActivating = true
                                            viewModel.activateLicenseCode(manualLicenseCode) { success, msg ->
                                                isManualActivating = false
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                if (success) {
                                                    manualLicenseCode = ""
                                                    selectedOptionTab = 1
                                                }
                                            }
                                        },
                                        enabled = !isManualActivating && manualLicenseCode.isNotBlank(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isManualActivating) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text(if (lang == "fa") "ثبت و فعال‌سازی" else "Submit & Activate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showRestoreDialog = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (lang == "fa") "بازیابی با شماره" else "Restore by Phone", fontSize = 11.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // ==================== گزینه ۲: اطلاعات اشتراک فعلی ====================
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // بنر وضعیت اشتراک
                    item {
                        val (statusTitle, statusColor, statusBg, statusIcon) = when (licenseState) {
                            is LicenseState.Active -> {
                                val active = licenseState as LicenseState.Active
                                if (active.planType.uppercase().contains("TRIAL")) {
                                    Quadruple(
                                        if (lang == "fa") "اشتراک تست رایگان فعال است" else "Free Trial Active",
                                        Color(0xFF0284C7),
                                        Color(0xFFE0F2FE),
                                        Icons.Default.Timer
                                    )
                                } else {
                                    Quadruple(
                                        if (lang == "fa") "اشتراک شما فعال و معتبر است" else "Subscription Active & Valid",
                                        Color(0xFF16A34A),
                                        Color(0xFFDCFCE7),
                                        Icons.Default.CheckCircle
                                    )
                                }
                            }
                            is LicenseState.Expired -> Quadruple(
                                if (lang == "fa") "اشتراک شما منقضی شده است" else "Subscription Expired",
                                Color(0xFFDC2626),
                                Color(0xFFFEE2E2),
                                Icons.Default.ErrorOutline
                            )
                            is LicenseState.Unactivated -> Quadruple(
                                if (lang == "fa") "هنوز اشتراکی فعال نشده است" else "No Active Subscription",
                                Color(0xFFD97706),
                                Color(0xFFFEF3C7),
                                Icons.Default.WarningAmber
                            )
                            is LicenseState.Checking -> Quadruple(
                                if (lang == "fa") "در حال بررسی وضعیت اشتراک..." else "Checking subscription status...",
                                Color(0xFF4B5563),
                                Color(0xFFF3F4F6),
                                Icons.Default.Sync
                            )
                            is LicenseState.OfflineGrace -> Quadruple(
                                if (lang == "fa") "حالت موقت آفلاین (نیاز به اتصال اینترنت)" else "Offline Grace Period (Internet Required)",
                                Color(0xFFD97706),
                                Color(0xFFFEF3C7),
                                Icons.Default.WifiOff
                            )
                            is LicenseState.ConnectionRequired -> Quadruple(
                                if (lang == "fa") "نیاز به اتصال به اینترنت" else "Internet Connection Required",
                                Color(0xFFDC2626),
                                Color(0xFFFEE2E2),
                                Icons.Default.SignalWifiBad
                            )
                            else -> Quadruple(
                                if (lang == "fa") "در حال بررسی وضعیت..." else "Checking...",
                                Color(0xFF4B5563),
                                Color(0xFFF3F4F6),
                                Icons.Default.Sync
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = statusBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(statusColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = statusTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = statusColor
                                    )
                                    Text(
                                        text = if (isSubscribed) (if (lang == "fa") "دسترسی نامحدود به تمامی بخش‌های نرم‌افزار" else "Full access to all software features") else (if (lang == "fa") "برای ادامه کار، اشتراک خود را تمدید نمایید" else "Please renew subscription to continue"),
                                        fontSize = 11.5.sp,
                                        color = statusColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // کارت جزئیات و مشخصات
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (lang == "fa") "مشخصات و جزئیات اشتراک" else "Subscription Details",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                val planType = when (val s = licenseState) {
                                    is LicenseState.Active -> s.planType
                                    else -> ""
                                }
                                val planTitle = JalaliCalendarHelper.getPlanTitleFa(planType)

                                val expiresAt = when (val s = licenseState) {
                                    is LicenseState.Active -> s.expiresAt
                                    else -> 0L
                                }
                                val activatedAt = when (val s = licenseState) {
                                    is LicenseState.Active -> s.activatedAt
                                    else -> 0L
                                }
                                val licenseCode = when (val s = licenseState) {
                                    is LicenseState.Active -> s.licenseCode
                                    else -> ""
                                }

                                // 1. نوع پلن
                                val displayPlan = if (planTitle.isNotBlank() && planTitle != "اشتراک عمومی") {
                                    if (lang == "fa") planTitle else planType
                                } else if (isSubscribed) {
                                    if (lang == "fa") "اشتراک فعال" else "Active Subscription"
                                } else {
                                    if (lang == "fa") "فاقد اشتراک فعال" else "No Active Subscription"
                                }
                                InfoRow(
                                    icon = Icons.Default.CardMembership,
                                    label = if (lang == "fa") "نوع پلن اشتراک:" else "Subscription Plan:",
                                    value = displayPlan
                                )

                                // 2. تاریخ فعال‌سازی
                                if (activatedAt > 0L) {
                                    val formattedActivated = if (lang == "fa") JalaliCalendarHelper.formatJalaliDateTime(activatedAt) else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(activatedAt))
                                    InfoRow(
                                        icon = Icons.Default.CalendarToday,
                                        label = if (lang == "fa") "تاریخ فعال‌سازی:" else "Activation Date:",
                                        value = formattedActivated
                                    )
                                }

                                // 3. تاریخ انقضا
                                if (expiresAt > 0L) {
                                    val formattedExpires = if (lang == "fa") JalaliCalendarHelper.formatJalaliDateTime(expiresAt) else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(expiresAt))
                                    InfoRow(
                                        icon = Icons.Default.Event,
                                        label = if (lang == "fa") "تاریخ و زمان انقضا:" else "Expiration Date:",
                                        value = formattedExpires
                                    )

                                    // 4. زمان باقیمانده
                                    val remainingTimeText = JalaliCalendarHelper.formatRemainingTime(expiresAt, currentTimeMs)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.HourglassBottom,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (lang == "fa") "زمان باقیمانده (روز:ساعت:دقیقه:ثانیه):" else "Time Remaining:",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (expiresAt > currentTimeMs) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                        ) {
                                            Text(
                                                text = remainingTimeText,
                                                color = if (expiresAt > currentTimeMs) Color(0xFF15803D) else Color(0xFFDC2626),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // 5. کد لایسنس
                                if (licenseCode.isNotBlank() && licenseCode != "SUPER_MANAGER") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (lang == "fa") "کد لایسنس: " else "License Code: ",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = licenseCode,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(licenseCode))
                                                Toast.makeText(context, if (lang == "fa") "کد لایسنس در حافظه کپی شد" else "License code copied", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = if (lang == "fa") "کپی لایسنس" else "Copy License",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // 6. شناسه سخت‌افزاری دستگاه
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = if (lang == "fa") "شناسه سخت‌افزاری دستگاه:" else "Hardware Device ID:",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = deviceId,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(deviceId))
                                            Toast.makeText(context, if (lang == "fa") "شناسه دستگاه کپی شد" else "Device ID copied", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = if (lang == "fa") "کپی شناسه دستگاه" else "Copy Device ID",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                // 7. نام و مدل دستگاه
                                InfoRow(
                                    icon = Icons.Default.Smartphone,
                                    label = if (lang == "fa") "مدل دستگاه ثبت شده:" else "Device Model:",
                                    value = deviceModel
                                )
                            }
                        }
                    }

                    // دکمه‌های عملیاتی
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isCheckingStatus = true
                                    coroutineScope.launch {
                                        viewModel.checkLicenseStatus()
                                        // viewModel.fetchSubscriptionPlans()
                                        kotlinx.coroutines.delay(800)
                                        isCheckingStatus = false
                                        Toast.makeText(context, if (lang == "fa") "وضعیت اشتراک از سرور استعلام شد" else "Subscription status checked from server", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isCheckingStatus,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isCheckingStatus) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (lang == "fa") "در حال بررسی..." else "Checking...", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (lang == "fa") "بروزرسانی وضعیت از سرور" else "Refresh Status from Server", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { selectedOptionTab = 0 },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (lang == "fa") "تمدید یا ارتقای اشتراک" else "Renew or Upgrade Subscription", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Payment Dialog
    if (showPaymentDialog && selectedPlanForPurchase != null) {
        val plan = selectedPlanForPurchase!!
        val isYearly = plan.id == "YEARLY" || (plan.durationDays ?: 0) >= 360
        val basePrice = plan.price ?: 0.0

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCartCheckout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "fa") "تایید خرید و پرداخت اشتراک" else "Confirm Purchase & Payment", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (lang == "fa") "پلن انتخابی:" else "Selected Plan:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (lang == "fa") (plan.name ?: "") else { if (plan.id == "YEARLY" || (plan.durationDays ?: 0) >= 360) "1 Year Subscription" else if (plan.id == "THREE_MONTHS" || (plan.durationDays ?: 0) in 80..100) "3 Months Subscription" else "1 Month Subscription" }, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (lang == "fa") "مبلغ قابل پرداخت:" else "Payable Amount:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val priceFormatted = if (lang == "fa") "${"%,.0f".format(basePrice).toPersianDigits()} تومان" else "${"%,.0f".format(java.util.Locale.US, basePrice)} Toman"
                                Text(
                                    priceFormatted,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (isYearly) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                                )
                            }

                            if (couponCodeInput.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(if (lang == "fa") "کد تخفیف:" else "Discount Code:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(couponCodeInput, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = couponCodeInput,
                        onValueChange = { couponCodeInput = it },
                        label = { Text(if (lang == "fa") "کد تخفیف (در صورت داشتن)" else "Discount Code (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = userPhoneInput,
                        onValueChange = { userPhoneInput = it },
                        label = { Text(if (lang == "fa") "شماره همراه مدیر (جهت صدور لایسنس)" else "Manager Phone (for license)") },
                        placeholder = { Text("09123456789") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = userNameInput,
                        onValueChange = { userNameInput = it },
                        label = { Text(if (lang == "fa") "نام و نام خانوادگی" else "Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = gameNetNameInput,
                        onValueChange = { gameNetNameInput = it },
                        label = { Text(if (lang == "fa") "نام مجموعه / گیم‌نت" else "Venue / Game Net Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val directPaymentUrl = ""

                        viewModel.initiateSubscriptionPurchase(
                            plan = plan.id ?: "",
                            coupon = couponCodeInput,
                            userPhone = userPhoneInput,
                            userName = userNameInput,
                            gameNetName = gameNetNameInput,
                            gateway = selectedGateway
                        ) { res ->
                            if (res != null) {
                                val targetUrl = res.paymentUrl
                                val targetLicense = res.licenseCode
                                viewModel.savePendingPurchasedLicense(targetLicense)

                                if (!targetUrl.isNullOrBlank() && targetUrl.startsWith("https://pay.forbix.ir/")) {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, if (lang == "fa") "باز کردن لینک پرداخت فوربیکس ممکن نشد." else "Could not open the Forbix payment link.", Toast.LENGTH_LONG).show()
                                    }
                                }

                                paymentState = "PENDING"
                                pendingTxnId = res.transactionId ?: ""
                                finalPriceAmount = res.amount ?: 0.0

                                // Payment is completed externally; receipt validation and Manager panel
                                // creation are performed manually by Super Manager.
                            }
                        }
                        showPaymentDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (lang == "fa") "ادامه و پرداخت از طریق فوربیکس" else "Continue to Forbix Payment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text(if (lang == "fa") "انصراف" else "Cancel")
                }
            }
        )
    }

    // Restore Subscription Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (lang == "fa") "بازیابی اشتراک با شماره موبایل" else "Restore Subscription by Phone", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "fa") "شماره موبایلی که هنگام خرید اشتراک ثبت کرده بودید را وارد کنید:" else "Enter the mobile number registered during subscription purchase:",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = restorePhoneInput,
                        onValueChange = { restorePhoneInput = it },
                        label = { Text(if (lang == "fa") "شماره همراه" else "Mobile Number") },
                        placeholder = { Text("09123456789") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restorePhoneInput.isBlank()) {
                            Toast.makeText(context, if (lang == "fa") "شماره موبایل را وارد کنید" else "Please enter mobile number", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isRestoring = true
                        viewModel.checkSubscriptionByPhone(restorePhoneInput) { info ->
                            isRestoring = false
                            if (info != null && info.exists) {
                                val code = info.licenseCode
                                if (code.isNotBlank()) {
                                    viewModel.activateLicenseCode(code, userPhone = restorePhoneInput) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        if (success) {
                                            showRestoreDialog = false
                                            selectedOptionTab = 1
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, if (lang == "fa") "اشتراک فعال بازیابی شد" else "Active subscription restored", Toast.LENGTH_SHORT).show()
                                    showRestoreDialog = false
                                    selectedOptionTab = 1
                                }
                            } else {
                                Toast.makeText(context, info?.message?.ifBlank { if (lang == "fa") "اشتراک فعالی برای این شماره یافت نشد" else "No active subscription found for this number" } ?: if (lang == "fa") "اشتراک فعالی برای این شماره یافت نشد" else "No active subscription found for this number", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isRestoring && restorePhoneInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (lang == "fa") "استعلام و فعال‌سازی" else "Inquire & Activate", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }, enabled = !isRestoring) {
                    Text(if (lang == "fa") "انصراف" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
