package com.example

import android.content.Context
import com.example.util.LocaleHelper
import kotlinx.coroutines.runBlocking


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: GameNetViewModel by viewModels()


    override fun attachBaseContext(newBase: Context) {
        val lang = runBlocking(Dispatchers.IO) {
            val db = com.example.data.AppDatabase.getDatabase(newBase)
            db.appSettingDao().getValue("language") ?: "fa"
        }
        super.attachBaseContext(LocaleHelper.updateLocale(newBase, lang))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        handlePaymentDeepLink(intent)
        testBackendConnectivity()

        setContent {
            val viewModel = mainViewModel
            val lang by viewModel.language.collectAsState()
            val layoutDirection = if (lang == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                val appTheme by mainViewModel.appTheme.collectAsState()
                MyApplicationTheme(themeName = appTheme) {
                    
                    val isFirstLaunch by viewModel.isFirstLaunch.collectAsState()
                    val lang by viewModel.language.collectAsState()
                    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
                    val notchSafeBarEnabled by viewModel.notchSafeBarEnabled.collectAsState()

                    val isAdminAuthenticated by viewModel.isAdminAuthenticated.collectAsState()
                    val isCustomerAuthenticated by viewModel.isCustomerAuthenticated.collectAsState()
                    val currentCustomer by com.example.data.network.SelfHostedManager.currentLoggedInCustomer.collectAsState()
                    val serverClockMillis by viewModel.serverClockMillis.collectAsState()

                    LaunchedEffect(isAdminAuthenticated) {
                        if (isAdminAuthenticated) {
                            viewModel.refreshServerClock()
                            while (true) {
                                delay(5 * 60 * 1000L)
                                viewModel.refreshServerClock()
                            }
                        }
                    }
                    
                    // VPN & Network Lag Detection Global State
                    val networkStatus by com.example.data.network.NetworkLogger.status.collectAsState()
                    val isVpnActive by com.example.util.VpnDetector.isVpnActiveFlow(context).collectAsState(initial = false)
                    var vpnWarningDismissed by remember { mutableStateOf(false) }
                    
                    val hasNetworkIssues = !networkStatus.isConnected ||
                        (networkStatus.lastErrorType != null &&
                            networkStatus.lastErrorTimestamp != null &&
                            System.currentTimeMillis() - networkStatus.lastErrorTimestamp!! < 60000)

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            },
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (notchSafeBarEnabled) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .windowInsetsTopHeight(WindowInsets.displayCutout)
                                            .heightIn(min = 24.dp)
                                    )
                                }
                                if (isAdminAuthenticated && serverClockMillis != null) {
                                    IranTehranClock(serverTimeMillis = serverClockMillis!!, modifier = Modifier.fillMaxWidth())
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    if (isFirstLaunch) {
                                        FirstLaunchGuide(
                                            lang = lang,
                                            onLanguageChange = { viewModel.saveLanguageSetting(it) },
                                            onDismiss = { viewModel.dismissFirstLaunch() },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        if (isCustomerAuthenticated) {
                                            CustomerAppContent(
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (isAdminAuthenticated) {
                                            val licenseState by viewModel.licenseState.collectAsState()
                                            val isServerConnected by viewModel.isServerConnected.collectAsState()
                                            val isTrialUser by viewModel.isTrialModeFlow.collectAsState()
                                            val isGracePeriodExpired by viewModel.isGracePeriodExpired.collectAsState()
                                            val currentAdminRole by viewModel.currentAdminRole.collectAsState()
                                            var showSubscriptionFromLock by remember { mutableStateOf(false) }

                                            val isSuper = currentAdminRole == "SUPER_MANAGER"

                                            if (!isSuper && !isServerConnected && isTrialUser) {
                                                TrialOnlineOnlyLockScreen(
                                                    onRetry = { viewModel.checkLicenseStatus() }
                                                )
                                            } else if (!isSuper && !isServerConnected && !isTrialUser && isGracePeriodExpired) {
                                                GracePeriodExpiredLockScreen(
                                                    onRetry = { viewModel.checkLicenseStatus() }
                                                )
                                            } else if (!isSuper && licenseState is LicenseState.Expired && !showSubscriptionFromLock) {
                                                SubscriptionLockScreen(
                                                    reason = (licenseState as LicenseState.Expired).message,
                                                    deviceId = viewModel.getDeviceId(),
                                                    onRenewSubscription = { showSubscriptionFromLock = true },
                                                    onActivateCode = { code ->
                                                        viewModel.activateLicenseCode(code) { _, msg ->
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                    },
                                                    onRefreshStatus = { viewModel.checkLicenseStatus() }
                                                )
                                            } else if (!isSuper && licenseState is LicenseState.Expired && showSubscriptionFromLock) {
                                                Box(modifier = Modifier.fillMaxSize()) {
                                                    SubscriptionActivationScreen(
                                                        viewModel = viewModel,
                                                        isEmbedded = false
                                                    )
                                                }
                                            } else {
                                                AppContent(viewModel = viewModel)
                                            }
                                        } else {
                                            UnifiedEntryScreen(
                                                viewModel = viewModel,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Global VPN Warning Overlay
                            if (isVpnActive && hasNetworkIssues && !vpnWarningDismissed) {
                                AlertDialog(
                                    onDismissRequest = { vpnWarningDismissed = true },
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Warning",
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text("هشدار تداخل شبکه (VPN)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = "برای استفاده بهتر و روانتر برنامه و همچنین اتصال پایدار به سرور، VPN یا ابزار تغییر آیپی خود را خاموش کنید.",
                                            fontSize = 14.sp,
                                            lineHeight = 24.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Justify
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = { vpnWarningDismissed = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("متوجه شدم", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 8.dp
                                )
                            }
                            
                            val urgentAlert by viewModel.managerUrgentAlert.collectAsState()
                            if (urgentAlert != null) {
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissManagerUrgentAlert() },
                                    title = { Text(urgentAlert!!.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                    text = { Text(urgentAlert!!.message) },
                                    confirmButton = {
                                        Button(onClick = { viewModel.dismissManagerUrgentAlert() }) {
                                            Text(if (lang == "fa") "متوجه شدم" else "Got it")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePaymentDeepLink(intent)
    }

    private fun handlePaymentDeepLink(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        val scheme = data.scheme ?: ""
        val host = data.host ?: ""
        if ((scheme == "gamenet" && (host == "payment-callback" || host == "verify")) ||
            (scheme == "https" && host == "gamenet.com")) {
            val txn = data.getQueryParameter("txn") ?: data.getQueryParameter("transactionId") ?: data.getQueryParameter("Authority") ?: data.getQueryParameter("authority") ?: ""
            val code = data.getQueryParameter("code") ?: data.getQueryParameter("license") ?: ""
            var status = data.getQueryParameter("status") ?: data.getQueryParameter("Status")
            if (status == null) {
                if (data.getQueryParameter("success") == "0" || data.getQueryParameter("success") == "false") {
                    status = "FAILED"
                } else if (data.getQueryParameter("success") == "1" || data.getQueryParameter("success") == "true") {
                    status = "SUCCESS"
                }
            }
            val finalStatus = status ?: "UNKNOWN"
            
            mainViewModel.handlePaymentCallback(txn = txn, licenseCode = code, status = finalStatus) { success, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun testBackendConnectivity() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val tag = "GAMENET_BACKEND_TEST"
            val healthUrl = com.example.data.network.SelfHostedManager.BASE_URL + "api/v1/time"
            android.util.Log.i(tag, "===== GAMENET BACKEND TEST =====")
            android.util.Log.i(tag, "URL: $healthUrl")
            
            try {
                val testResult = com.example.data.network.SelfHostedManager.testServerConnection()
                android.util.Log.i(tag, "HTTP STATUS: 200 OK (Server Connection Test Successful)")
                android.util.Log.i(tag, "RESPONSE BODY: $testResult")
            } catch (e: Exception) {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                e.printStackTrace(pw)
                val stackTraceStr = sw.toString()

                android.util.Log.e(tag, "HTTP STATUS: FAILED")
                android.util.Log.e(tag, "RESPONSE BODY: None")
                android.util.Log.e(tag, "EXCEPTION: ${e.message}")
                android.util.Log.e(tag, "EXCEPTION TYPE: ${e.javaClass.name}")
                android.util.Log.e(tag, "STACKTRACE:\n$stackTraceStr")
            }
            android.util.Log.i(tag, "================================")
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: GameNetViewModel) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    var selectedTab by remember { mutableIntStateOf(2) }
    var showServerTestDialog by remember { mutableStateOf(false) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }
    val lang by viewModel.language.collectAsState()
    val isTrialActive by viewModel.isTrialModeFlow.collectAsState()
    var showTrialClubLockDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseState by viewModel.licenseState.collectAsState()
    val showAuthDialog by viewModel.showAuthDialog.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    if (showTrialClubLockDialog) {
        AlertDialog(
            onDismissRequest = { showTrialClubLockDialog = false },
            title = { Text(if (lang == "fa") "بخش قفل شده" else "Feature Locked", fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == "fa") "دسترسی به امکانات باشگاه مشتریان در نسخه آزمایشی 24 ساعته قفل است. لطفاً جهت فعال‌سازی نامحدود، اشتراک تهیه نمایید." else "Access to Customer Club is locked in 24-hour trial mode. Please purchase a subscription for unlimited access.") },
            confirmButton = {
                Button(onClick = {
                    showTrialClubLockDialog = false
                    selectedTab = 3
                }) {
                    Text(if (lang == "fa") "مشاهده و خرید اشتراک" else "View Subscriptions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrialClubLockDialog = false }) {
                    Text(if (lang == "fa") "متوجه شدم" else "Cancel")
                }
            }
        )
    }

    if (showAuthDialog) {
        AuthDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.dismissAuthDialog() }
        )
    }

    if (showNotificationCenterDialog) {
        AdminNotificationCenterDialog(
            viewModel = viewModel,
            onDismiss = { showNotificationCenterDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GameNexa",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isTrialActive || currentAdminRole == "TRIAL_USER" -> Color(0xFFE65100)
                                currentAdminRole == "SUPER_MANAGER" -> Color(0xFF673AB7)
                                currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER" -> Color(0xFF0284C7)
                                else -> Color(0xFF00796B)
                            }
                        ) {
                            Text(
                                text = when {
                                    isTrialActive || currentAdminRole == "TRIAL_USER" -> if (lang == "fa") "نسخه تستی 24 ساعته" else "24H Trial Mode"
                                    currentAdminRole == "SUPER_MANAGER" -> androidx.compose.ui.res.stringResource(R.string.role_super_manager)
                                    currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER" -> androidx.compose.ui.res.stringResource(R.string.role_manager)
                                    else -> androidx.compose.ui.res.stringResource(R.string.role_deputy)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Dedicated Top-Level Notification Bell with Dynamic Unread Badge
                    AdminNotificationBellAction(
                        viewModel = viewModel,
                        onOpenNotificationCenter = { showNotificationCenterDialog = true }
                    )
                    IconButton(onClick = { showServerTestDialog = true }) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = "تست اتصال به سرور",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            com.example.data.network.SelfHostedManager.fetchAllFromCloud()
                            android.widget.Toast.makeText(context, "همگام‌سازی ابری انجام شد", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Cloud")
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "خروج",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. باشگاه مشتریان
                    CustomBottomNavItem(
                        selected = selectedTab == 0,
                        onClick = {
                            if (currentAdminRole == "OPERATOR") {
                                android.widget.Toast.makeText(context, "شما به عنوان معاون دسترسی به این بخش ندارید", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                                showTrialClubLockDialog = true
                            } else {
                                selectedTab = 0
                            }
                        },
                        icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = "CUSTOMER_CLUB") },
                        label = { Text(if (lang == "fa") "باشگاه" else "Club", fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) },
                        testTag = "nav_tab_customer_club"
                    )
                    // 2. مشتریان/رزرو
                    CustomBottomNavItem(
                        selected = selectedTab == 1,
                        onClick = { 
                            selectedTab = 1 
                        },
                        icon = { Icon(Icons.Default.People, contentDescription = "CUSTOMERS_RESERVATIONS") },
                        label = { Text(if (lang == "fa") "مشتریان" else "Customers", fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) },
                        testTag = "nav_tab_customers_reservations"
                    )
                    // 3. سالن (Prominent & Exact Center)
                    CustomBottomNavItem(
                        selected = selectedTab == 2,
                        onClick = { 
                            selectedTab = 2 
                        },
                        icon = { Icon(Icons.Default.SportsEsports, contentDescription = "MAIN", modifier = Modifier.size(32.dp)) },
                        label = { Text(if (lang == "fa") "سالن" else "Hall", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, maxLines = 1) },
                        testTag = "nav_tab_main",
                        isCenter = true
                    )
                    // 4. اشتراک
                    CustomBottomNavItem(
                        selected = selectedTab == 3,
                        onClick = {
                            if (currentAdminRole == "OPERATOR") {
                                android.widget.Toast.makeText(context, "شما به عنوان معاون دسترسی به این بخش ندارید", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                selectedTab = 3
                            }
                        },
                        icon = { Icon(Icons.Default.CardMembership, contentDescription = "MEMBERSHIP") },
                        label = { Text(if (lang == "fa") "اشتراک" else "Sub", fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) },
                        testTag = "nav_tab_membership"
                    )
                    // 5. تنظیمات
                    CustomBottomNavItem(
                        selected = selectedTab == 4,
                        onClick = {
                            if (currentAdminRole == "OPERATOR") {
                                android.widget.Toast.makeText(context, "شما به عنوان معاون دسترسی به این بخش ندارید", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                selectedTab = 4
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "SETTINGS") },
                        label = { Text(if (lang == "fa") "تنظیمات" else "Settings", fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1) },
                        testTag = "nav_tab_settings"
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        val isServerConnected by viewModel.isServerConnected.collectAsState()
        val isTrialUser by viewModel.isTrialModeFlow.collectAsState()
        val isGracePeriodExpired by viewModel.isGracePeriodExpired.collectAsState()
        val offlineGraceSecondsRemaining by viewModel.offlineGraceSecondsRemaining.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Persistent 24h offline warning. Super Manager has lifetime entitlement and is
            // never disabled by this grace timer, but must still see the same connectivity warning.
            if (!isServerConnected && !isTrialUser && !isGracePeriodExpired) {
                OfflineGracePeriodBanner(
                    secondsRemaining = offlineGraceSecondsRemaining,
                    onRetry = {
                        coroutineScope.launch {
                            viewModel.verifyLicenseStatus()
                        }
                    }
                )
            }

            // Persistent / State-managed Top-Level Overlay Banner for Incoming Requests & Payment Proofs
            AdminUrgentAlertOverlay(
                viewModel = viewModel,
                onOpenNotificationCenter = { showNotificationCenterDialog = true }
            )

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                color = MaterialTheme.colorScheme.background
            ) {
                when (selectedTab) {
                    0 -> CustomerClubScreen(viewModel = viewModel)
                    1 -> CustomersReservationsScreen(viewModel = viewModel)
                    2 -> MainScreen(viewModel = viewModel, onNavigateToSubscription = { selectedTab = 3 })
                    3 -> SubscriptionActivationScreen(viewModel = viewModel, isEmbedded = true)
                    4 -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (showServerTestDialog) {
        ServerConnectionTestDialog(onDismiss = { showServerTestDialog = false })
    }

    val managerAlert by viewModel.managerUrgentAlert.collectAsState()
    if (managerAlert != null) {
        val alert = managerAlert!!
        val isPayment = alert.type == "PAYMENT"
        AlertDialog(
            onDismissRequest = { viewModel.dismissManagerUrgentAlert() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isPayment) Icons.Default.Paid else Icons.Default.EventAvailable,
                        contentDescription = null,
                        tint = if (isPayment) Color(0xFFF59E0B) else Color(0xFF3B82F6),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = alert.title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (isPayment) Color(0xFFF59E0B) else Color(0xFF3B82F6)
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alert.message,
                        fontSize = 13.sp,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (alert.customerName.isNotBlank() || alert.phoneNumber.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (alert.customerName.isNotBlank()) {
                                    Text("👤 مشتری: ${alert.customerName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                if (alert.phoneNumber.isNotBlank()) {
                                    Text("📞 شماره تماس: ${alert.phoneNumber}", fontSize = 12.sp)
                                }
                                if (alert.amount > 0.0) {
                                    Text("💰 مبلغ: %,.0f تومان".format(java.util.Locale.US, alert.amount), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF10B981))
                                }
                                if (alert.trackingCode.isNotBlank()) {
                                    Text("🔖 کد پیگیری: ${alert.trackingCode}", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissManagerUrgentAlert()
                        selectedTab = 1 // Go to Customers & Reservations tab
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPayment) Color(0xFFF59E0B) else Color(0xFF3B82F6)
                    )
                ) {
                    Text("مشاهده و بررسی", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissManagerUrgentAlert() }) {
                    Text("بستن")
                }
            }
        )
    }
}



@Composable
fun RowScope.CustomBottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    testTag: String,
    isCenter: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val iconColor = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .weight(1f)
            .testTag(testTag)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.then(
                if (isCenter) {
                    Modifier
                        .size(46.dp)
                        .background(
                            color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                } else {
                    Modifier.size(32.dp)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                icon()
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        CompositionLocalProvider(LocalContentColor provides iconColor) {
            label()
        }
    }
}

@Composable
fun TrialOnlineOnlyLockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.trial_online_only_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.trial_online_only_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.btn_retry_connection),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GracePeriodExpiredLockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.offline_blocked_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.offline_blocked_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.btn_retry_connection),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineGracePeriodBanner(
    secondsRemaining: Long,
    onRetry: () -> Unit
) {
    val hours = secondsRemaining / 3600
    val minutes = (secondsRemaining % 3600) / 60
    val seconds = secondsRemaining % 60
    val formattedTime = "%02d:%02d:%02d".format(java.util.Locale.US, hours, minutes, seconds)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFB71C1C),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.offline_grace_banner_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formattedTime,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFFFD54F)
                        )
                    }
                }
            }

            Text(
                text = androidx.compose.ui.res.stringResource(R.string.offline_grace_banner_message),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.btn_retry_connection),
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IranTehranClock(
    serverTimeMillis: Long,
    modifier: Modifier = Modifier
) {
    var liveServerTime by remember(serverTimeMillis) { mutableLongStateOf(serverTimeMillis) }
    val referenceElapsed = remember(serverTimeMillis) { SystemClock.elapsedRealtime() }

    LaunchedEffect(serverTimeMillis) {
        while (true) {
            liveServerTime = serverTimeMillis + (SystemClock.elapsedRealtime() - referenceElapsed)
            delay(1000L)
        }
    }

    val timeFormatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }
    }
    val dateText = remember(liveServerTime) {
        com.example.util.JalaliCalendarHelper.formatJalaliDateTime(
            liveServerTime, includeTime = false
        )
    }

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .height(38.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateText,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "🇮🇷  ${timeFormatter.format(Date(liveServerTime))}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
