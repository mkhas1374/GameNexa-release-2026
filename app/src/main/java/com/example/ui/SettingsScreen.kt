package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LicenseViewModel
import com.example.ui.AppAccessState
import com.example.ui.LicenseState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.ConsoleType
import com.example.data.Product
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

enum class SettingsSection(
    val titleFa: String,
    val titleEn: String,
    val descriptionFa: String,
    val descriptionEn: String,
    val icon: ImageVector,
    val badgeColor: Color
) {
    LANGUAGE("زبان", "Language", "تغییر زبان برنامه به فارسی یا انگلیسی", "Switch app language between Persian and English", Icons.Default.Language, Color(0xFF1E88E5)),
    THEME("تم", "Theme", "تنظیم ظاهر دارک/روشن، آمار و گزارش‌ها و ارتباط با ما", "Configure dark/light theme, stats, and contact info", Icons.Default.Palette, Color(0xFF8E24AA)),
    BACKUP_RESTORE("پشتیبان گیری و بازیابی اطلاعات", "Backup & Restore", "تهیه فایل پشتیبان JSON و بازیابی کامل داده‌ها", "Create JSON backup and full data restoration", Icons.Default.Backup, Color(0xFF00897B)),
    RESERVATION_RULES("قوانین رزرو و قیمت‌گذاری", "Reservation & Pricing", "تنظیم مدت‌ها، قیمت VIP، مهلت پرداخت، جریمه‌ها و متن‌های رزرو", "Configure durations, VIP price, payment deadlines, penalties and reservation messages", Icons.Default.EventAvailable, Color(0xFFF4511E)),
    BROADCAST_MESSAGE("پیام مدیریت برای اعضا", "Broadcast Message", "ارسال پیام و اعلامیه عمومی مستقیم به اپلیکیشن مشتریان", "Send announcements and notices directly to customer app", Icons.Default.Campaign, Color(0xFFE53935)),
    DEPUTY_ASSIGNMENT("تخصیص معاون یا مدیر اجرایی", "Assign Assistant / Deputy", "تنظیم حساب کاربری، رمز عبور و دسترسی‌های معاون", "Set credentials, password, and permissions for deputy", Icons.Default.SupervisorAccount, Color(0xFFF4511E)),
    MANAGER_SALES("فروش مدیر", "Manager Sales", "ثبت نام و مدیریت حساب سایر مدیران", "Register and manage accounts of other managers", Icons.Default.Store, Color(0xFFD81B60)),
    SUBSCRIPTION_PLANS("مدیریت پلن‌های اشتراک", "Subscription Plans", "تنظیم قیمت، لینک فوربیکس و متن‌های صفحه خرید اشتراک", "Manage subscription prices, Forbix links, and purchase-page texts", Icons.Default.CardMembership, Color(0xFF7B1FA2)),
    DEVICE_CONFIG("پیکر بندی دستگاه ها", "Device Configuration", "تعداد ایستگاه‌ها، اعلان‌ها و قیمت انواع کنسول‌ها", "Station counts, notifications, and console rates", Icons.Default.Devices, Color(0xFF039BE5)),
    BUFFET_CAFE("بوفه و کافه", "Buffet & Cafe", "مدیریت محصولات، قیمت‌گذاری و اقلام بوفه", "Product management, pricing, and cafe items", Icons.Default.Storefront, Color(0xFFFB8C00)),
    PAYMENT_METHODS("تخصیص روش های پرداخت", "Payment Methods", "شماره کارت، شبا، درگاه آنلاین، رمزارز و نرخ تبدیل", "Card numbers, IBAN, online gateway, and crypto", Icons.Default.Payment, Color(0xFF43A047))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GameNetViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    val lang by viewModel.language.collectAsState()

    val isTrialActive by viewModel.isTrialModeFlow.collectAsState()
    var showTrialLockDialog by remember { mutableStateOf(false) }

    if (showTrialLockDialog) {
        AlertDialog(
            onDismissRequest = { showTrialLockDialog = false },
            title = { Text(if (lang == "fa") "نسخه تست" else "Trial Version", fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == "fa") "برای دسترسی به این بخش باید اشتراک معتبر تهیه کنید." else "You need a valid subscription to access this feature.") },
            confirmButton = {
                Button(onClick = {
                    showTrialLockDialog = false
                    viewModel.logoutAdmin()
                }) {
                    Text(if (lang == "fa") "خرید اشتراک" else "Buy Subscription")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrialLockDialog = false }) {
                    Text(if (lang == "fa") "بستن" else "Close")
                }
            }
        )
    }
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()

    val isTrialUser = isTrialActive || currentAdminRole == "TRIAL_USER" || viewModel.isTrialUser

    LaunchedEffect(isTrialUser, selectedSection) {
        if (isTrialUser && selectedSection != null && selectedSection != SettingsSection.LANGUAGE && selectedSection != SettingsSection.THEME) {
            selectedSection = null
        }
    }

    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        if (selectedSection == null) {
            // Main Settings Menu Hub
            SettingsMenuHub(
                lang = lang,
                currentRole = currentAdminRole,
                isTrialActive = isTrialUser,
                onSelectSection = {
                    if (isTrialUser && it != SettingsSection.LANGUAGE && it != SettingsSection.THEME) {
                        showTrialLockDialog = true
                    } else {
                        selectedSection = it
                    }
                },
                onOpenServerTest = { showDiagnosticsDialog = true }
            )
        } else {
            // Dedicated Sub-Screen with Back button
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsSubPageHeader(
                    section = selectedSection!!,
                    lang = lang,
                    onBack = { selectedSection = null }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (selectedSection!!) {
                        SettingsSection.RESERVATION_RULES -> {
                            ReservationSettingsScreen(managerId = com.example.data.network.SelfHostedManager.currentManagerId, onNavigateBack = { selectedSection = null })
                        }
                        SettingsSection.LANGUAGE -> LanguageSettingsSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.THEME -> ThemeSettingsSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.BACKUP_RESTORE -> BackupRestoreSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.BROADCAST_MESSAGE -> BroadcastMessageSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.DEPUTY_ASSIGNMENT -> DeputyAssignmentSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.SUBSCRIPTION_PLANS -> {
                            if (currentAdminRole == "SUPER_MANAGER") {
                                SubscriptionPlansAdminSubScreen(viewModel = viewModel, lang = lang)
                            }
                        }
                        SettingsSection.MANAGER_SALES -> {
                            if (currentAdminRole == "SUPER_MANAGER") {
                                ManagerSalesSubScreen(viewModel = viewModel, lang = lang)
                            } else {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                            Text(
                                                text = "عدم دسترسی - بخش فروش مدیر",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        Text(
                                            text = "بخش «فروش مدیر» فقط برای Super Manager (مدیر ارشد) فعال می‌باشد. شما به عنوان مدیر مجموعه به سایر تمامی امکانات برنامه دسترسی کامل دارید.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                        SettingsSection.DEVICE_CONFIG -> DeviceConfigSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.BUFFET_CAFE -> BuffetCafeSubScreen(viewModel = viewModel, lang = lang)
                        SettingsSection.PAYMENT_METHODS -> PaymentMethodsSubScreen(viewModel = viewModel, lang = lang)
                    }
                }
            }
        }
    }

    if (showDiagnosticsDialog) {
        NetworkDiagnosticsDialog(onDismissRequest = { showDiagnosticsDialog = false })
    }
}

@Composable
fun SettingsMenuHub(
    lang: String,
    currentRole: String,
    isTrialActive: Boolean,
    onSelectSection: (SettingsSection) -> Unit,
    onOpenServerTest: () -> Unit
) {
    val networkStatus by com.example.data.network.NetworkLogger.status.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "fa") "تنظیمات و پیکربندی گیم‌نت" else "GameNet Settings & Config",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (lang == "fa") "برای ویرایش هر بخش روی گزینه مربوطه لمس کنید" else "Tap any category below to configure settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Server Connection Test Quick Action Card (Debug Inspector)
        if (currentRole == "SUPER_MANAGER") {
            item {
                Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0284C7).copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.2.dp, Color(0xFF0284C7).copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenServerTest() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "fa") "بررسی وضعیت اتصال سرور" else "Network Health & Diagnostics",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (lang == "fa") "نمایش جزئیات کامل لاگ‌های شبکه و تأخیر سرور" else "View detailed network logs and latency",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7)
                    ) {
                        Text(
                            text = if (lang == "fa") "آزمایش" else "Test",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        } // End of Super Manager diagnostics card

        item {
            Text(
                text = if (lang == "fa") "فهرست بخش‌های تنظیمات" else "Settings Menu",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        val isTrial = isTrialActive || currentRole == "TRIAL_USER"
        if (isTrial) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = if (lang == "fa")
                                "اکانت تست 24 ساعته: در نسخه آزمایشی فقط دسترسی به تنظیمات زبان و تم مجاز است و سایر تنظیمات برای جلوگیری از تغییر پیکربندی سیستم قفل می‌باشند."
                            else
                                "24h Trial Mode: Only Language and Theme settings are available. All configuration settings are locked.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        val visibleSections = SettingsSection.values().filter { section ->
            if (isTrial) {
                section == SettingsSection.LANGUAGE || section == SettingsSection.THEME
            } else if (section == SettingsSection.MANAGER_SALES || section == SettingsSection.SUBSCRIPTION_PLANS) {
                currentRole == "SUPER_MANAGER"
            } else {
                true
            }
        }

        items(visibleSections) { section ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSection(section) }
                    .testTag("settings_item_${section.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(section.badgeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = section.badgeColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (lang == "fa") section.titleFa else section.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isTrialActive && section != SettingsSection.LANGUAGE && section != SettingsSection.THEME) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (lang == "fa") section.descriptionFa else section.descriptionEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun SettingsSubPageHeader(
    section: SettingsSection,
    lang: String,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(section.badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    tint = section.badgeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (lang == "fa") section.titleFa else section.titleEn,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (lang == "fa") section.descriptionFa else section.descriptionEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(if (lang == "fa") "بازگشت" else "Back", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 1. LANGUAGE SETTINGS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun LanguageSettingsSubScreen(viewModel: GameNetViewModel, lang: String) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (lang == "fa") "انتخاب زبان پیش‌فرض برنامه" else "Default Application Language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == "fa") "تمامی عناوین، پیام‌ها و بخش‌های مختلف بر اساس زبان انتخاب‌شده نمایش داده می‌شوند." else "All titles, messages, and sections will be displayed according to the selected language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.saveLanguageSetting("fa")
                                activity?.let { act ->
                                    com.example.util.LocaleHelper.applyLocale(act, "fa")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (lang == "fa") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            if (lang == "fa") 2.dp else 1.dp,
                            if (lang == "fa") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🇮🇷 فارسی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "fa") "زبان فعال" else "انتخاب",
                                fontSize = 11.sp,
                                color = if (lang == "fa") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.saveLanguageSetting("en")
                                activity?.let { act ->
                                    com.example.util.LocaleHelper.applyLocale(act, "en")
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (lang == "en") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            if (lang == "en") 2.dp else 1.dp,
                            if (lang == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🇬🇧 English", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (lang == "en") "Active Language" else "Select",
                                fontSize = 11.sp,
                                color = if (lang == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. THEME SETTINGS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun ThemeSettingsSubScreen(viewModel: GameNetViewModel, lang: String) {
    val currentTheme by viewModel.appTheme.collectAsState()
    var showStatisticsDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }

    if (showStatisticsDialog) {
        AlertDialog(
            onDismissRequest = { showStatisticsDialog = false },
            confirmButton = {
                TextButton(onClick = { showStatisticsDialog = false }) { Text("بستن") }
            },
            title = { Text("آمار و گزارش‌ها") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    StatisticsScreen(viewModel = viewModel)
                }
            }
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            confirmButton = {
                TextButton(onClick = { showContactDialog = false }) { Text("بستن") }
            },
            title = { Text("ارتباط با ما") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    ContactUsScreen(viewModel = viewModel)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "پوسته و تم گرافیکی (Theme)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                var themeDropdownExpanded by remember { mutableStateOf(false) }
                val themes = listOf("دارک", "سفید", "نئون مات")

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { themeDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "تم انتخابی: $currentTheme", fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        themes.forEach { th ->
                            DropdownMenuItem(
                                text = { Text(th, fontWeight = if (currentTheme == th) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    viewModel.saveAppTheme(th)
                                    themeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStatisticsDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاهده آمار", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showContactDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(10.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContactSupport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارتباط با ما", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. BACKUP & RESTORE SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun BackupRestoreSubScreen(viewModel: GameNetViewModel, lang: String) {
    val context = LocalContext.current
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreSuccessDialog by remember { mutableStateOf(false) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonStr = inputStream?.bufferedReader()?.use { it.readText() }
                if (!jsonStr.isNullOrBlank()) {
                    val success = viewModel.restoreBackupFromJson(jsonStr)
                    if (success) {
                        showRestoreSuccessDialog = true
                    } else {
                        backupMessage = "خطا در خواندن فایل پشتیبان."
                    }
                }
            } catch (e: Exception) {
                backupMessage = "خطا: ${e.message}"
            }
        }
    }

    if (showRestoreSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreSuccessDialog = false },
            confirmButton = {
                TextButton(onClick = { showRestoreSuccessDialog = false }) { Text("تایید") }
            },
            title = { Text("بازیابی موفقیت‌آمیز") },
            text = { Text("اطلاعات مخاطبان، سطوح باشگاه، قیمت‌ها و تنظیمات با موفقیت بازیابی شد.") }
        )
    }

    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            confirmButton = {
                TextButton(onClick = { backupMessage = null }) { Text("بستن") }
            },
            title = { Text("وضعیت پشتیبان‌گیری") },
            text = { Text(backupMessage!!) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "پشتیبان‌گیری خودکار و دستی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "سیستم به صورت خودکار هر روز ساعت 3:30 بامداد از پایگاه داده بکاپ تهیه می‌کند. شما همچنین می‌توانید به صورت دستی فایل پشتیبان JSON تهیه کرده یا فایل قبلی را بازیابی نمایید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val file = viewModel.exportBackupJsonToExternal(context)
                            if (file != null) {
                                backupMessage = "فایل پشتیبان با موفقیت ایجاد و ذخیره شد:\n${file.absolutePath}"
                            } else {
                                backupMessage = "خطا در ایجاد فایل پشتیبان."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("خروجی گرفتن بکاپ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { restoreLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فراخوانی بکاپ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. BROADCAST MESSAGE SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun BroadcastMessageSubScreen(viewModel: GameNetViewModel, lang: String) {
    val context = LocalContext.current
    val broadcastMessage by viewModel.ownerBroadcastMessage.collectAsState()
    val broadcastAnnouncement by viewModel.ownerBroadcastAnnouncement.collectAsState()
    var broadcastInput by remember { mutableStateOf("") }
    var isBroadcasting by remember { mutableStateOf(false) }

    LaunchedEffect(broadcastMessage) {
        if (broadcastInput.isBlank()) {
            broadcastInput = broadcastMessage
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "پیام مدیریت برای اعضای GameNexa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "این پیام در بنر اعلان بالای اپلیکیشن تمامی مشتریان نمایش داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = broadcastInput,
                    onValueChange = { broadcastInput = it },
                    label = { Text("متن پیام و اعلامیه عمومی") },
                    placeholder = { Text("مثال: جمعه تورنمنت فیفا با جوایز نقدی! جهت ثبت نام به پذیرش مراجعه کنید...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (broadcastAnnouncement?.message?.isNotBlank() == true) {
                        Text(
                            text = "✅ همگام‌سازی ابری فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (broadcastInput.isNotBlank()) {
                                isBroadcasting = true
                                viewModel.publishBroadcastMessage(broadcastInput.trim())
                                isBroadcasting = false
                                Toast.makeText(context, "اعلامیه برای کلیه مشتریان ارسال و فعال شد! 📢", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = broadcastInput.isNotBlank() && !isBroadcasting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ارسال و انتشار همگانی", fontWeight = FontWeight.Bold)
                    }
                }

                if (broadcastMessage.isNotBlank() || broadcastAnnouncement?.message?.isNotBlank() == true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "📢 پیام فعال کنونی روی اپلیکیشن مشتریان:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = (broadcastAnnouncement?.message?.takeIf { it.isNotBlank() }) ?: broadcastMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. DEPUTY ASSIGNMENT SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun DeputyAssignmentSubScreen(viewModel: GameNetViewModel, lang: String) {
    val context = LocalContext.current
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    val ownerUsername by viewModel.ownerUsername.collectAsState()
    val operatorUsername by viewModel.operatorUsername.collectAsState()
    val operatorPassword by viewModel.operatorPassword.collectAsState()
    val deputyName by viewModel.deputyName.collectAsState()

    var showChangePasswordDialog by remember { mutableStateOf<String?>(null) }
    var newPassInput by remember { mutableStateOf("") }

    if (showChangePasswordDialog != null) {
        val targetRole = showChangePasswordDialog!!
        AlertDialog(
            onDismissRequest = {
                showChangePasswordDialog = null
                newPassInput = ""
            },
            title = {
                Text(
                    if (targetRole == "SUPER_MANAGER") "تغییر رمز مدیر ارشد (صاحب گیم‌نت)" else "تغییر رمز پرسنل و اپراتور",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "لطفا رمز عبور جدید را وارد نمایید:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it },
                        label = { Text("رمز عبور جدید") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassInput.isNotBlank()) {
                            if (targetRole == "SUPER_MANAGER") {
                                viewModel.saveOwnerCredentials(ownerUsername, newPassInput.trim())
                            } else {
                                viewModel.saveOperatorCredentials(deputyName, operatorUsername, newPassInput.trim())
                            }
                            Toast.makeText(context, "رمز عبور با موفقیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
                            showChangePasswordDialog = null
                            newPassInput = ""
                        }
                    },
                    enabled = newPassInput.isNotBlank()
                ) { Text("ذخیره رمز") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showChangePasswordDialog = null
                    newPassInput = ""
                }) { Text("انصراف") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "تخصیص و معرفی معاونت مالی / مدیر اجرایی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "نقش و سمت همکار جدید را انتخاب نمایید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                var selectedRoleTitle by remember { mutableStateOf("معاونت مالی") }
                val presetRoleTitles = listOf("معاونت مالی", "مدیر اجرایی", "حسابدار", "اپراتور سالن")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetRoleTitles.forEach { roleTitle ->
                        FilterChip(
                            selected = (selectedRoleTitle == roleTitle),
                            onClick = { selectedRoleTitle = roleTitle },
                            label = { Text(roleTitle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                var tempDepName by remember { mutableStateOf(deputyName) }
                var tempOpUser by remember { mutableStateOf(operatorUsername) }
                var tempOpPass by remember { mutableStateOf(operatorPassword) }

                OutlinedTextField(
                    value = tempDepName,
                    onValueChange = { tempDepName = it },
                    label = { Text("نام و نام خانوادگی ($selectedRoleTitle)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = tempOpUser,
                    onValueChange = { tempOpUser = it },
                    label = { Text("نام کاربری ورود ($selectedRoleTitle)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = tempOpPass,
                    onValueChange = { tempOpPass = it },
                    label = { Text("رمز عبور ورود ($selectedRoleTitle)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Button(
                    onClick = {
                        val fullAssignedName = if (tempDepName.contains(selectedRoleTitle)) tempDepName else "$tempDepName ($selectedRoleTitle)"
                        viewModel.saveOperatorCredentials(fullAssignedName, tempOpUser, tempOpPass)
                        Toast.makeText(context, "اطلاعات و دسترسی $selectedRoleTitle با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ذخیره و تخصیص $selectedRoleTitle", fontWeight = FontWeight.Bold)
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = "SUPER_MANAGER" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تغییر رمز مدیر مجموعه", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { showChangePasswordDialog = "OPERATOR" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تغییر رمز معاون/مدیر اجرایی", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. DEVICE CONFIGURATION SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun DeviceConfigSubScreen(viewModel: GameNetViewModel, lang: String) {
    val stationCount by viewModel.stationCount.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notchSafeBarEnabled by viewModel.notchSafeBarEnabled.collectAsState()
    val consoleList by viewModel.consoleTypes.collectAsState()
    val selectedConsoleInSettings by viewModel.selectedConsoleInSettings.collectAsState()

    var stationCountInput by remember { mutableStateOf(stationCount.toString()) }
    var consoleSelectExpanded by remember { mutableStateOf(false) }
    var newConsoleNameInput by remember { mutableStateOf("") }

    val activeConsole = consoleList.find { it.name == selectedConsoleInSettings }
    var p1Input by remember { mutableStateOf("") }
    var p2Input by remember { mutableStateOf("") }
    var p3Input by remember { mutableStateOf("") }
    var p4Input by remember { mutableStateOf("") }

    LaunchedEffect(activeConsole) {
        if (activeConsole != null) {
            p1Input = String.format(Locale.US, "%d", activeConsole.price1)
            p2Input = String.format(Locale.US, "%d", activeConsole.price2)
            p3Input = String.format(Locale.US, "%d", activeConsole.price3)
            p4Input = String.format(Locale.US, "%d", activeConsole.price4)
        } else {
            p1Input = ""
            p2Input = ""
            p3Input = ""
            p4Input = ""
        }
    }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Station Count & Alerts Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "تعداد ایستگاه‌ها و سیستم هشدارها",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = stationCountInput,
                        onValueChange = { stationCountInput = it },
                        label = { Text("تعداد ایستگاه‌های سالن") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val parsed = stationCountInput.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                viewModel.saveStationCountSetting(parsed)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Text("ذخیره", fontWeight = FontWeight.Bold)
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "هشدارهای صوتی و لرزشی پایان زمان",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "پخش هشدار صوتی به هنگام اتمام دقایق نشست فعال",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.saveNotificationsEnabled(it) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (lang == "fa") "نوار ایمنی ناچ/دوربین بالای صفحه" else "Notch / camera safe-area bar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (lang == "fa")
                                "به‌صورت پیش‌فرض خاموش است؛ فقط اگر ناچ با ساعت و تاریخ تداخل دارد فعال کنید."
                            else
                                "Off by default. Enable only when the device cutout overlaps the app clock/date.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notchSafeBarEnabled,
                        onCheckedChange = { viewModel.saveNotchSafeBarEnabled(it) }
                    )
                }
            }
        }

        // Console Types & Pricing Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "انواع کنسول و تعرفه قیمت‌ها (ساعتی)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Select Console Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { consoleSelectExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedConsoleInSettings.ifEmpty { "کنسولی موجود نیست" },
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = consoleSelectExpanded,
                        onDismissRequest = { consoleSelectExpanded = false }
                    ) {
                        consoleList.forEach { console ->
                            DropdownMenuItem(
                                text = { Text(console.name) },
                                onClick = {
                                    viewModel.selectConsoleInSettings(console.name)
                                    consoleSelectExpanded = false
                                }
                            )
                        }
                    }
                }

                if (activeConsole != null) {
                    Text(
                        text = "ویرایش تعرفه ساعتی کنسول: ${activeConsole.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = p1Input,
                            onValueChange = { p1Input = it },
                            label = { Text("1 دسته (تومان)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = p2Input,
                            onValueChange = { p2Input = it },
                            label = { Text("2 دسته (تومان)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = p3Input,
                            onValueChange = { p3Input = it },
                            label = { Text("3 دسته (تومان)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = p4Input,
                            onValueChange = { p4Input = it },
                            label = { Text("4 دسته (تومان)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.updateConsolePrices(
                                    activeConsole.name,
                                    p1Input.toLongOrNull() ?: 0L,
                                    p2Input.toLongOrNull() ?: 0L,
                                    p3Input.toLongOrNull() ?: 0L,
                                    p4Input.toLongOrNull() ?: 0L
                                )
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ذخیره قیمت‌ها", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.deleteConsoleType(activeConsole.name) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف", fontSize = 12.sp)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Add New Console Type
                Text("افزودن نوع کنسول جدید:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newConsoleNameInput,
                        onValueChange = { newConsoleNameInput = it },
                        label = { Text("نام کنسول (مثال: PS5 Pro)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (newConsoleNameInput.isNotBlank()) {
                                viewModel.addConsoleType(newConsoleNameInput.trim())
                                newConsoleNameInput = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("افزودن")
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 7. BUFFET & CAFE SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun BuffetCafeSubScreen(viewModel: GameNetViewModel, lang: String) {
    val productList by viewModel.products.collectAsState()
    val selectedProductInSettings by viewModel.selectedProductInSettings.collectAsState()

    var productSelectExpanded by remember { mutableStateOf(false) }
    val activeProduct = productList.find { it.name == selectedProductInSettings }
    var prodPriceInput by remember { mutableStateOf("") }

    LaunchedEffect(activeProduct) {
        if (activeProduct != null) {
            prodPriceInput = String.format(Locale.US, "%d", activeProduct.price)
        } else {
            prodPriceInput = ""
        }
    }

    var newProductNameInput by remember { mutableStateOf("") }
    var newProductPriceInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "مدیریت اقلام بوفه و کافه",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Select Product Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { productSelectExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedProductInSettings.ifEmpty { "محصولی انتخاب نشده" },
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = productSelectExpanded,
                        onDismissRequest = { productSelectExpanded = false }
                    ) {
                        productList.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text("${prod.name} (%,d تومان)".format(Locale.US, prod.price)) },
                                onClick = {
                                    viewModel.selectProductInSettings(prod.name)
                                    productSelectExpanded = false
                                }
                            )
                        }
                    }
                }

                if (activeProduct != null) {
                    Text(
                        text = "ویرایش قیمت: ${activeProduct.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = prodPriceInput,
                            onValueChange = { prodPriceInput = it },
                            label = { Text("قیمت محصول (تومان)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.updateProductPrice(
                                    activeProduct.name,
                                    prodPriceInput.toLongOrNull() ?: 0L
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ذخیره")
                        }

                        Button(
                            onClick = { viewModel.deleteProduct(activeProduct.name) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(54.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Add New Product Form
                Text("افزودن محصول جدید به بوفه:", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newProductNameInput,
                        onValueChange = { newProductNameInput = it },
                        label = { Text("نام محصول (مثال: چیپس)") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = newProductPriceInput,
                        onValueChange = { newProductPriceInput = it },
                        label = { Text("قیمت (تومان)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.9f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (newProductNameInput.isNotBlank()) {
                            val price = newProductPriceInput.toLongOrNull() ?: 0L
                            viewModel.addProduct(newProductNameInput.trim(), price)
                            newProductNameInput = ""
                            newProductPriceInput = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن به لیست محصولات بوفه", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 8. PAYMENT METHODS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun PaymentMethodsSubScreen(viewModel: GameNetViewModel, lang: String) {
    val context = LocalContext.current
    val cardsJson by viewModel.gnPaymentCards.collectAsState()
    val gatewaysJson by viewModel.gnPaymentGateways.collectAsState()
    val cryptosJson by viewModel.gnPaymentCryptos.collectAsState()

    var cardNumberInput by remember { mutableStateOf("") }
    var cardOwnerInput by remember { mutableStateOf("") }
    var cardBankInput by remember { mutableStateOf("") }

    var gatewayNameInput by remember { mutableStateOf("") }
    var gatewayUrlInput by remember { mutableStateOf("") }

    var cryptoCoinInput by remember { mutableStateOf("USDT (TRC20)") }
    var cryptoAddressInput by remember { mutableStateOf("") }

    // Parse existing configs
    val cardList = remember(cardsJson) {
        val list = mutableListOf<Triple<String, String, String>>() // number, owner, bank
        try {
            val arr = JSONArray(cardsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val num = obj.optString("cardNumber").ifEmpty { obj.optString("number") }
                val own = obj.optString("ownerName").ifEmpty { obj.optString("owner") }
                val bank = obj.optString("bank", "بانک")
                if (num.isNotBlank()) {
                    list.add(Triple(num, own, bank))
                }
            }
        } catch (_: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in SettingsScreen.kt", _) }
        list
    }

    val gatewayList = remember(gatewaysJson) {
        val list = mutableListOf<Pair<String, String>>() // name, url
        try {
            val arr = JSONArray(gatewaysJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.optString("name"), obj.optString("url")))
            }
        } catch (_: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in SettingsScreen.kt", _) }
        list
    }

    val cryptoList = remember(cryptosJson) {
        val list = mutableListOf<Pair<String, String>>() // coin, address
        try {
            val arr = JSONArray(cryptosJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.optString("coin"), obj.optString("address")))
            }
        } catch (_: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in SettingsScreen.kt", _) }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bank Cards Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("کارت‌های بانکی واریز مشتریان (کارت به کارت)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (cardList.isNotEmpty()) {
                    cardList.forEach { (num, owner, bank) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "$bank - $owner", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = num, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = {
                                    val updated = cardList.filterNot { it.first == num }
                                    val arr = JSONArray()
                                    updated.forEach { (n, o, b) ->
                                        arr.put(JSONObject().apply {
                                            put("number", n)
                                            put("cardNumber", n)
                                            put("owner", o)
                                            put("ownerName", o)
                                            put("bank", b)
                                        })
                                    }
                                    viewModel.savePaymentSetting("gn_payment_cards", arr.toString())
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = cardNumberInput,
                    onValueChange = { cardNumberInput = it },
                    label = { Text("شماره کارت 16 رقمی") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cardOwnerInput,
                        onValueChange = { cardOwnerInput = it },
                        label = { Text("نام صاحب حساب") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = cardBankInput,
                        onValueChange = { cardBankInput = it },
                        label = { Text("نام بانک") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Button(
                    onClick = {
                        if (cardNumberInput.isNotBlank() && cardOwnerInput.isNotBlank()) {
                            val arr = try { JSONArray(cardsJson) } catch (_: Exception) { JSONArray() }
                            arr.put(JSONObject().apply {
                                put("number", cardNumberInput.trim())
                                put("cardNumber", cardNumberInput.trim())
                                put("owner", cardOwnerInput.trim())
                                put("ownerName", cardOwnerInput.trim())
                                put("bank", cardBankInput.trim().ifEmpty { "بانک" })
                            })
                            viewModel.savePaymentSetting("gn_payment_cards", arr.toString())
                            cardNumberInput = ""
                            cardOwnerInput = ""
                            cardBankInput = ""
                            Toast.makeText(context, "کارت بانکی افزوده شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن کارت بانکی")
                }
            }
        }

        // Online Gateway Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("درگاه‌های پرداخت اینترنتی آنلاین", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (gatewayList.isNotEmpty()) {
                    gatewayList.forEach { (name, url) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    val updated = gatewayList.filterNot { it.first == name }
                                    val arr = JSONArray()
                                    updated.forEach { (n, u) ->
                                        arr.put(JSONObject().apply {
                                            put("name", n)
                                            put("url", u)
                                        })
                                    }
                                    viewModel.savePaymentSetting("gn_payment_gateways", arr.toString())
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = gatewayNameInput,
                    onValueChange = { gatewayNameInput = it },
                    label = { Text("نام درگاه (مثال: درگاه زیبال یا زرین‌پال)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = gatewayUrlInput,
                    onValueChange = { gatewayUrlInput = it },
                    label = { Text("آدرس لینک درگاه (URL)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (gatewayNameInput.isNotBlank() && gatewayUrlInput.isNotBlank()) {
                            val arr = JSONArray(gatewaysJson)
                            arr.put(JSONObject().apply {
                                put("name", gatewayNameInput.trim())
                                put("url", gatewayUrlInput.trim())
                            })
                            viewModel.savePaymentSetting("gn_payment_gateways", arr.toString())
                            gatewayNameInput = ""
                            gatewayUrlInput = ""
                            Toast.makeText(context, "درگاه آنلاین اضافه شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن درگاه پرداخت")
                }
            }
        }

        // Crypto Wallets Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CurrencyBitcoin, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("آدرس کیف‌پول‌های رمزارز (Crypto)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (cryptoList.isNotEmpty()) {
                    cryptoList.forEach { (coin, addr) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = coin, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(text = addr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                IconButton(onClick = {
                                    val updated = cryptoList.filterNot { it.first == coin }
                                    val arr = JSONArray()
                                    updated.forEach { (c, a) ->
                                        arr.put(JSONObject().apply {
                                            put("coin", c)
                                            put("address", a)
                                        })
                                    }
                                    viewModel.savePaymentSetting("gn_payment_cryptos", arr.toString())
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = cryptoCoinInput,
                    onValueChange = { cryptoCoinInput = it },
                    label = { Text("نام شبکه و رمزارز (مثال: USDT TRC20 یا TON)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = cryptoAddressInput,
                    onValueChange = { cryptoAddressInput = it },
                    label = { Text("آدرس کیف‌پول (Wallet Address)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Button(
                    onClick = {
                        if (cryptoCoinInput.isNotBlank() && cryptoAddressInput.isNotBlank()) {
                            val arr = JSONArray(cryptosJson)
                            arr.put(JSONObject().apply {
                                put("coin", cryptoCoinInput.trim())
                                put("address", cryptoAddressInput.trim())
                            })
                            viewModel.savePaymentSetting("gn_payment_cryptos", arr.toString())
                            cryptoAddressInput = ""
                            Toast.makeText(context, "کیف پول رمزارز اضافه شد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("افزودن کیف‌پول رمزارز")
                }
            }
        }
    }
}


@Composable
fun ManagerSalesSubScreen(viewModel: GameNetViewModel, lang: String) {
    var currentTab by remember { mutableStateOf("menu") }
    val managers by viewModel.managersList.collectAsState()

    LaunchedEffect(currentTab) {
        if (currentTab == "list_managers") {
            viewModel.fetchManagers()
        } else if (currentTab == "trial_management") {
            viewModel.fetchDeviceTrials()
        }
    }

    BackHandler(enabled = currentTab != "menu") {
        currentTab = "menu"
    }

    if (currentTab == "menu") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (lang == "fa") "بخش فروش مدیر" else "Manager Sales",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Option 1
            Card(
                modifier = Modifier.fillMaxWidth().clickable { currentTab = "create_manager" },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = if (lang == "fa") "ساخت پنل مدیر" else "Create Manager Panel", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Option 2
            Card(
                modifier = Modifier.fillMaxWidth().clickable { currentTab = "list_managers" },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = if (lang == "fa") "دسترسی به مدیران فعلی" else "Access to Current Managers", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Option 3
            Card(
                modifier = Modifier.fillMaxWidth().clickable { currentTab = "trial_management" },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = if (lang == "fa") "مدیریت تست 24 ساعته" else "24h Trial Management", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

        }
    } else if (currentTab == "create_manager") {
        var fullName by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var gameneName by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var planType by remember { mutableStateOf("1 ماهه") }
        var durationDays by remember { mutableStateOf("30") }
        var maxDevices by remember { mutableStateOf("1") }
        var paymentAmount by remember { mutableStateOf("0") }
        var paymentStatus by remember { mutableStateOf("PENDING") }
        val context = LocalContext.current
        var isLoading by remember { mutableStateOf(false) }
        var createdLicenseCode by remember { mutableStateOf<String?>(null) }

        Column(modifier = Modifier.fillMaxSize().imePadding().padding(16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                IconButton(onClick = { currentTab = "menu" }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (lang == "fa") "ساخت پنل مدیر" else "Create Manager Panel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (createdLicenseCode != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ثبت مدیر با موفقیت انجام شد!", color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("نام کاربری: $phone", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("رمز عبور: $password", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Manager ID: ${createdLicenseCode?.removePrefix("ACTIVE_") ?: "-"}", fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { createdLicenseCode = null; currentTab = "list_managers" }) {
                            Text("مشاهده لیست مدیران")
                        }
                    }
                }
            } else {
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("نام و نام خانوادگی") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = gameneName, onValueChange = { gameneName = it }, label = { Text("نام گیم نت") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره موبایل (نام کاربری)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("رمز عبور") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(8.dp))
                Text("نوع پلن:")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("1 ماهه", "3 ماهه", "12 ماهه").forEach { plan ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = planType == plan, onClick = {
                                planType = plan
                                durationDays = when(plan) {
                                    "1 ماهه" -> "30"
                                    "3 ماهه" -> "90"
                                    "12 ماهه" -> "365"
                                    else -> durationDays
                                }
                            })
                            Text(plan, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    text = "مدت اعتبار: \${durationDays} روز (توسط سیستم و مطابق پلن انتخابی)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(value = maxDevices, onValueChange = { maxDevices = it }, label = { Text("تعداد دستگاه مجاز") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (fullName.isBlank() || phone.isBlank() || password.isBlank()) {
                            android.widget.Toast.makeText(context, "فیلدهای ضروری را پر کنید", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        viewModel.createManager(
                            fullName = fullName,
                            gameneName = gameneName,
                            phone = phone,
                            pass = password,
                            planType = planType,
                            durationDays = when(planType) { "1 ماهه" -> 30; "3 ماهه" -> 90; "12 ماهه" -> 365; "وی ای پی" -> 365; else -> durationDays.toIntOrNull() ?: 30 },
                            maxDevices = maxDevices.toIntOrNull() ?: 1,
                            paymentAmount = 0L,
                            paymentStatus = "PAID",
                            onSuccess = { manager ->
                                isLoading = false
                                if (!manager.licenseCode.isNullOrEmpty()) {
                                    createdLicenseCode = manager.licenseCode
                                } else {
                                    android.widget.Toast.makeText(context, "پنل مدیر با موفقیت ایجاد و فعال شد.", android.widget.Toast.LENGTH_LONG).show()
                                    currentTab = "list_managers"
                                }
                            },
                            onError = { err ->
                                isLoading = false
                                android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text(if (lang == "fa") "ثبت مدیر جدید" else "Register Manager")
                }
                Spacer(Modifier.height(120.dp))
            }
        }
    } else if (currentTab == "list_managers") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                IconButton(onClick = { currentTab = "menu" }) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (lang == "fa") "مدیران فعلی" else "Current Managers",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Stats Card for Managers
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "تعداد کل مدیران ثبت‌نام شده",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val nonSuperManagers = remember(managers) { managers.filter { !it.isSuperRole } }
                        Text(
                            text = "${nonSuperManagers.size} مدیر",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            var filterState by remember { mutableStateOf("ALL") }
            var searchQuery by remember { mutableStateOf("") }
            var showPurgeConfirmDialog by remember { mutableStateOf(false) }
            var isPurgingServer by remember { mutableStateOf(false) }

            if (showPurgeConfirmDialog) {
                val context = LocalContext.current
                AlertDialog(
                    onDismissRequest = { if (!isPurgingServer) showPurgeConfirmDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("پاکسازی و ایزولاسیون کامل سرور")
                        }
                    },
                    text = {
                        Text(
                            "آیا از پاکسازی کامل داده‌های بدون صاحب و ایزولاسیون دیتابیس سرور اطمینان دارید؟\nاین عملیات تمامی مشتریان، رزروها، و لاگ‌های تستی و حذف شده را از ریشه سرور پاک کرده و فقط داده‌های معتبر و سوپرمدیر را حفظ می‌کند."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isPurgingServer = true
                                viewModel.purgeServerOrphanData(
                                    onSuccess = { msg ->
                                        isPurgingServer = false
                                        showPurgeConfirmDialog = false
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    },
                                    onError = { err ->
                                        isPurgingServer = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isPurgingServer,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            if (isPurgingServer) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("در حال پاکسازی...")
                            } else {
                                Text("تایید و پاکسازی کامل")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showPurgeConfirmDialog = false },
                            enabled = !isPurgingServer
                        ) {
                            Text("انصراف")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = filterState == "ALL",
                        onClick = { filterState = "ALL" },
                        label = { Text(if (lang == "fa") "همه" else "All") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = filterState == "PENDING",
                        onClick = { filterState = "PENDING" },
                        label = { Text(if (lang == "fa") "در انتظار" else "Pending") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = filterState == "ACTIVE",
                        onClick = { filterState = "ACTIVE" },
                        label = { Text(if (lang == "fa") "فعال" else "Active") }
                    )
                }

                OutlinedButton(
                    onClick = { showPurgeConfirmDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("پاکسازی سرور", fontSize = 12.sp)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                placeholder = { Text(if (lang == "fa") "جستجو با نام، نام کاربری، گیم‌نت یا شماره..." else "Search name, user, gamenet...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            val currentAdminRole by viewModel.currentAdminRole.collectAsState()
            val isCurrentAdminSuper = currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER"

            val filteredManagers = managers.filter { manager ->
                if (!isCurrentAdminSuper && manager.isSuperRole) return@filter false
                val matchesFilter = when (filterState) {
                    "PENDING" -> (manager.status?.equals("pending", ignoreCase = true) == true || manager.paymentStatus?.equals("pending", ignoreCase = true) == true)
                    "ACTIVE" -> (manager.status?.equals("active", ignoreCase = true) == true || manager.paymentStatus?.equals("paid", ignoreCase = true) == true)
                    else -> true
                }
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    val query = searchQuery.trim().lowercase()
                    (manager.name?.lowercase()?.contains(query) == true) ||
                    (manager.fullName?.lowercase()?.contains(query) == true) ||
                    (manager.username?.lowercase()?.contains(query) == true) ||
                    (manager.phone?.contains(query) == true) ||
                    (manager.gameNetName?.lowercase()?.contains(query) == true) ||
                    (manager.email?.lowercase()?.contains(query) == true)
                }
                matchesFilter && matchesSearch
            }

            var selectedManagerForDetails by remember { mutableStateOf<com.example.data.network.AdminManagerDto?>(null) }

            if (selectedManagerForDetails != null) {
                val manager = selectedManagerForDetails!!
                val context = LocalContext.current
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

                AlertDialog(
                    onDismissRequest = { selectedManagerForDetails = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = manager.fullName ?: manager.name ?: "جزئیات حساب مدیر",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ID: ${manager.stringId}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Status Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isStatusActive = manager.status?.equals("active", ignoreCase = true) == true
                                val statusBg = if (isStatusActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                                Surface(
                                    color = statusBg.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, statusBg),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "وضعیت حساب: ${if (isStatusActive) "فعال" else (manager.status ?: "نامشخص")}",
                                        fontSize = 11.sp,
                                        color = statusBg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                val isPaid = manager.paymentStatus?.equals("paid", ignoreCase = true) == true
                                val payBg = if (isPaid) Color(0xFF10B981) else Color(0xFFEF4444)
                                Surface(
                                    color = payBg.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, payBg),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "وضعیت پرداخت: ${if (isPaid) "پرداخت شده" else (manager.paymentStatus ?: "در انتظار")}",
                                        fontSize = 11.sp,
                                        color = payBg,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Name & GameNet
                            Text(
                                text = "نام و نام خانوادگی: ${manager.fullName ?: manager.name ?: "-"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "نام گیم‌نت: ${if (manager.gameNetName.isNullOrBlank()) "-" else manager.gameNetName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Username (Phone)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "نام کاربری (شماره تلفن): ${manager.username ?: manager.phone ?: "-"}",
                                    fontSize = 13.sp
                                )
                                val userPhone = manager.username ?: manager.phone
                                if (!userPhone.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(userPhone))
                                            Toast.makeText(context, "شماره تلفن کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Phone", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            // Password
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "رمز عبور: ${if (manager.password.isNullOrBlank()) "ثبت نشده" else manager.password}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!manager.password.isNullOrBlank()) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(manager.password))
                                            Toast.makeText(context, "رمز عبور کپی شد", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Password", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            // Email
                            Text(
                                text = "ایمیل: ${if (manager.email.isNullOrBlank()) "ثبت نشده" else manager.email}",
                                fontSize = 13.sp
                            )

                            // Plan & Payment
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "نوع پلن: ${if (manager.planType.isNullOrBlank()) "-" else manager.planType}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "مبلغ: ${if (manager.paymentAmount != null && manager.paymentAmount > 0) String.format(Locale.US, "%,d", manager.paymentAmount.toLong()) + " تومان" else "0 تومان"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (manager.paymentAmount != null && manager.paymentAmount > 0) Color(0xFF10B981) else Color.Gray
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Dates
                            if (manager.createdAt != null && manager.createdAt > 0) {
                                Text(
                                    text = "تاریخ ثبت‌نام: ${com.example.util.JalaliCalendarHelper.formatJalaliDateTime(manager.createdAt)}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            if (manager.firstLoginDate != null && manager.firstLoginDate > 0) {
                                Text(
                                    text = "تاریخ اولین ورود و فعال‌سازی: ${com.example.util.JalaliCalendarHelper.formatJalaliDateTime(manager.firstLoginDate)}",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }
                            if (manager.expiryDate != null && manager.expiryDate > 0) {
                                Text(
                                    text = "تاریخ انقضای اشتراک: ${com.example.util.JalaliCalendarHelper.formatJalaliDateTime(manager.expiryDate)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Approval action in dialog if pending
                            if (manager.status?.equals("pending", ignoreCase = true) == true || manager.paymentStatus?.equals("pending", ignoreCase = true) == true) {
                                Spacer(Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        viewModel.approveManagerPayment(
                                            managerId = manager.stringId,
                                            onSuccess = {
                                                Toast.makeText(context, "پرداخت تایید و لایسنس صادر شد!", Toast.LENGTH_SHORT).show()
                                                selectedManagerForDetails = null
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "خطا: $err", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("تایید پرداخت و صدور لایسنس")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedManagerForDetails = null }) {
                            Text("بستن")
                        }
                    }
                )
            }

            var managerToEdit by remember { mutableStateOf<com.example.data.network.AdminManagerDto?>(null) }
            var managerToDelete by remember { mutableStateOf<com.example.data.network.AdminManagerDto?>(null) }

            if (managerToEdit != null) {
                var editName by remember { mutableStateOf(managerToEdit!!.fullName ?: managerToEdit!!.name ?: "") }
                var editGameNet by remember { mutableStateOf(managerToEdit!!.gameNetName ?: "") }
                var editPlan by remember { mutableStateOf(managerToEdit!!.planType ?: "1 ماهه") }
                var editPass by remember { mutableStateOf("") }
                val context = LocalContext.current
                var isEditing by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { managerToEdit = null },
                    title = { Text("ویرایش مدیر") },
                    text = {
                        Column {
                            OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("نام") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = editGameNet, onValueChange = { editGameNet = it }, label = { Text("نام گیم‌نت") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = editPlan, onValueChange = { editPlan = it }, label = { Text("نوع پلن") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = editPass, onValueChange = { editPass = it }, label = { Text("رمز عبور جدید (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isEditing = true
                                viewModel.editManager(
                                    id = managerToEdit!!.stringId,
                                    name = editName.ifBlank { null },
                                    gameNetName = editGameNet.ifBlank { null },
                                    password = editPass.ifBlank { null },
                                    planType = editPlan.ifBlank { null },
                                    onSuccess = {
                                        isEditing = false
                                        Toast.makeText(context, "مدیر ویرایش شد", Toast.LENGTH_SHORT).show()
                                        managerToEdit = null
                                    },
                                    onError = { err ->
                                        isEditing = false
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            enabled = !isEditing
                        ) {
                            Text("ذخیره")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { managerToEdit = null }) { Text("انصراف") }
                    }
                )
            }

            if (managerToDelete != null) {
                val context = LocalContext.current
                var isDeleting by remember { mutableStateOf(false) }
                AlertDialog(
                    onDismissRequest = { managerToDelete = null },
                    title = { Text("حذف مدیر") },
                    text = { Text("آیا از حذف مدیر '${managerToDelete!!.fullName ?: managerToDelete!!.name}' اطمینان دارید؟ این کار غیرقابل بازگشت است.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                isDeleting = true
                                viewModel.deleteManager(
                                    id = managerToDelete!!.stringId,
                                    onSuccess = {
                                        isDeleting = false
                                        Toast.makeText(context, "مدیر حذف شد", Toast.LENGTH_SHORT).show()
                                        managerToDelete = null
                                    },
                                    onError = { err ->
                                        isDeleting = false
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            enabled = !isDeleting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("بله، حذف کن")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { managerToDelete = null }) { Text("انصراف") }
                    }
                )
            }

            if (filteredManagers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (lang == "fa") "هیچ مدیری با این مشخصات یافت نشد." else "No managers found.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredManagers, key = { it.id ?: it.username ?: System.currentTimeMillis().toString() }) { manager ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val isStatusActive = manager.status?.equals("active", ignoreCase = true) == true
                        val statusBg = if (isStatusActive) Color(0xFF10B981) else Color(0xFFF59E0B)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = manager.fullName ?: manager.name ?: "مدیر گیم‌نت",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            color = statusBg.copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, statusBg),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (isStatusActive) "فعال" else (manager.status ?: "در انتظار"),
                                                fontSize = 10.sp,
                                                color = statusBg,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { isExpanded = !isExpanded },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "بستن" else "باز کردن",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.6.dp)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(text = "نام کاربری / شماره تماس:", fontSize = 11.sp, color = Color.Gray)
                                                Text(text = manager.username ?: manager.phone ?: "-", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { selectedManagerForDetails = manager },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("جزئیات بیشتر", fontSize = 11.sp)
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            OutlinedButton(
                                                onClick = { managerToEdit = manager },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("ویرایش", fontSize = 11.sp)
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            Button(
                                                onClick = { managerToDelete = manager },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("حذف", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (currentTab == "trial_management") {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { currentTab = "menu" }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = if (lang == "fa") "مدیریت تست 24 ساعته" else "24h Trial Management",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { viewModel.fetchDeviceTrials() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }

            val context = LocalContext.current
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            val deviceTrials by viewModel.deviceTrials.collectAsState()

            // Stats Card for Trial Devices
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "تست‌های 24 ساعته فعال",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "${deviceTrials.size} دستگاه",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            var showResetDialog by remember { mutableStateOf<com.example.data.network.DeviceTrialDto?>(null) }

            LaunchedEffect(Unit) {
                viewModel.fetchDeviceTrials()
            }

            if (showResetDialog != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = null },
                    title = { Text(if (lang == "fa") "تمدید اشتراک تستی" else "Extend Trial") },
                    text = {
                        Text(if (lang == "fa") "آیا مطمئن هستید که می‌خواهید اشتراک تستی این دستگاه را 24 ساعت دیگر تمدید کنید؟" else "Are you sure you want to extend this trial by 24 hours?")
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.extendDeviceTrial(showResetDialog!!.deviceId) { success ->
                                Toast.makeText(context, if (success) "با موفقیت ریست شد" else "خطا در ریست", Toast.LENGTH_SHORT).show()
                            }
                            showResetDialog = null
                        }) {
                            Text(if (lang == "fa") "بله، تمدید کن" else "Yes, Extend")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showResetDialog = null }) {
                            Text(if (lang == "fa") "انصراف" else "Cancel")
                        }
                    }
                )
            }

            if (deviceTrials.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (lang == "fa") "هیچ دستگاه تستی ثبت نشده است." else "No trial devices registered.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deviceTrials, key = { it.deviceId }) { trial ->
                        var isExpanded by remember { mutableStateOf(false) }
                        val expireMillis = trial.expiryDate ?: trial.expireTime ?: (trial.startTime?.let { it + (trial.durationMinutes ?: (24 * 60)) * 60_000L })
                        val remainingMs = if (expireMillis != null) expireMillis - System.currentTimeMillis() else 0L
                        val isExpired = remainingMs <= 0L

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                // Very compact header row: only device name + status badge + arrow
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExpanded = !isExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.SportsEsports,
                                            contentDescription = null,
                                            tint = if (!isExpired) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = trial.displayDeviceName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (!isExpired) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (!isExpired) "فعال" else "منقضی",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!isExpired) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { isExpanded = !isExpanded },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "بستن" else "باز کردن",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Expandable details with actions
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.6.dp)

                                        // Device ID + Copy
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "شناسه: ${trial.deviceId}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(trial.deviceId))
                                                    Toast.makeText(context, "شناسه دستگاه کپی شد", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        // Times
                                        if (trial.startTime != null && trial.startTime > 0) {
                                            Text(
                                                text = "شروع: ${com.example.util.JalaliCalendarHelper.formatJalaliDateTime(trial.startTime)}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (expireMillis != null && expireMillis > 0) {
                                            val remainingStr = if (!isExpired) {
                                                val h = remainingMs / 3_600_000L
                                                val m = (remainingMs % 3_600_000L) / 60_000L
                                                "$h ساعت و $m دقیقه باقی‌مانده"
                                            } else "منقضی شده"
                                            Text(
                                                text = "انقضا: ${com.example.util.JalaliCalendarHelper.formatJalaliDateTime(expireMillis)} ($remainingStr)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (!isExpired) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }

                                        // Action buttons: Extend / Reset & Delete / Deactivate
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { showResetDialog = trial },
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (lang == "fa") "تمدید زمان" else "Extend Time", fontSize = 11.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.deleteDeviceTrial(trial.deviceId) { success ->
                                                        Toast.makeText(context, if (success) "رکورد تست حذف شد" else "خطا در عملیات", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(0.9f).height(34.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (lang == "fa") "حذف" else "Delete", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.resetCurrentDeviceTrial()
                    Toast.makeText(context, if (lang == "fa") "دسترسی تست 24 ساعته برای این دستگاه ریست شد!" else "Trial reset successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text(if (lang == "fa") "ریست لوکال دستگاه فعلی" else "Local Reset for Current Device")
            }
        }
    }
}


@Composable
private fun SubscriptionPlansAdminSubScreen(
    viewModel: GameNetViewModel,
    lang: String
) {
    val remote by viewModel.subscriptionPlansAdmin.collectAsState()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var selectedSubscriptionSection by remember { mutableStateOf<String?>(null) }

    var titleFa by remember { mutableStateOf("") }
    var titleEn by remember { mutableStateOf("") }
    var subtitleFa by remember { mutableStateOf("") }
    var subtitleEn by remember { mutableStateOf("") }
    var instructionFa by remember { mutableStateOf("") }
    var instructionEn by remember { mutableStateOf("") }
    var buttonFa by remember { mutableStateOf("") }
    var buttonEn by remember { mutableStateOf("") }
    var currencyFa by remember { mutableStateOf("") }
    var currencyEn by remember { mutableStateOf("") }
    var supportFa by remember { mutableStateOf("") }
    var supportEn by remember { mutableStateOf("") }

    data class Draft(
        val id: String,
        var nameFa: String,
        var nameEn: String,
        var price: String,
        var durationDays: String,
        var paymentUrl: String,
        var descriptionFa: String,
        var descriptionEn: String,
        var active: Boolean
    )

    var drafts by remember {
        mutableStateOf(
            listOf(
                Draft("MONTHLY", "", "", "", "30", "", "", "", true),
                Draft("THREE_MONTHS", "", "", "", "90", "", "", "", true),
                Draft("YEARLY", "", "", "", "365", "", "", "", true)
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.fetchSubscriptionPlansAdmin()
    }

    LaunchedEffect(remote) {
        if (remote.isNotEmpty() && !loaded) {
            fun str(key: String) = remote[key]?.toString().orEmpty()
            titleFa = str("pageTitleFa")
            titleEn = str("pageTitleEn")
            subtitleFa = str("pageSubtitleFa")
            subtitleEn = str("pageSubtitleEn")
            instructionFa = str("paymentInstructionFa")
            instructionEn = str("paymentInstructionEn")
            buttonFa = str("purchaseButtonFa")
            buttonEn = str("purchaseButtonEn")
            currencyFa = str("currencyFa")
            currencyEn = str("currencyEn")
            supportFa = str("supportMessageFa")
            supportEn = str("supportMessageEn")
            val rawPlans = remote["plans"] as? List<*>
            if (rawPlans != null) {
                drafts = drafts.map { old ->
                    val m = rawPlans.mapNotNull { it as? Map<*, *> }
                        .firstOrNull { it["id"]?.toString() == old.id }
                    if (m == null) old else old.copy(
                        nameFa = m["nameFa"]?.toString().orEmpty(),
                        nameEn = m["nameEn"]?.toString().orEmpty(),
                        price = m["price"]?.toString()?.removeSuffix(".0").orEmpty(),
                        durationDays = m["durationDays"]?.toString()?.removeSuffix(".0").orEmpty(),
                        paymentUrl = m["paymentUrl"]?.toString().orEmpty(),
                        descriptionFa = m["descriptionFa"]?.toString().orEmpty(),
                        descriptionEn = m["descriptionEn"]?.toString().orEmpty(),
                        active = m["active"]?.toString()?.toBooleanStrictOrNull() ?: true
                    )
                }
            }
            loaded = true
        }
    }

    if (selectedSubscriptionSection == null) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (lang == "fa") "مدیریت پلن‌های اشتراک" else "Subscription Plans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (lang == "fa") "هر گزینه را لمس کنید تا تنظیمات همان بخش باز شود." else "Tap an item to open its settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                "page" to if (lang == "fa") "متن و ظاهر صفحه خرید" else "Purchase page texts",
                "MONTHLY" to if (lang == "fa") "پلن 1 ماهه" else "1 Month Plan",
                "THREE_MONTHS" to if (lang == "fa") "پلن 3 ماهه" else "3 Month Plan",
                "YEARLY" to if (lang == "fa") "پلن 1 ساله" else "1 Year Plan"
            ).forEach { (key, title) ->
                Card(Modifier.fillMaxWidth().clickable { selectedSubscriptionSection = key }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text("←", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    } else {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = { selectedSubscriptionSection = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (lang == "fa") "بازگشت به فهرست" else "Back to list")
            }
        }
        item {
            Text(
                if (lang == "fa") "مدیریت کامل صفحه خرید اشتراک" else "Subscription Purchase Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (lang == "fa") "این بخش فقط برای Super Manager است. تغییرات بلافاصله منبع سروری صفحه خرید را تغییر می‌دهد." else "Only Super Manager can edit these settings. Changes update the server-side purchase page source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedSubscriptionSection == "page") item {
            SubscriptionTextField("عنوان فارسی", titleFa) { titleFa = it }
            SubscriptionTextField("عنوان انگلیسی", titleEn) { titleEn = it }
            SubscriptionTextField("زیرعنوان فارسی", subtitleFa) { subtitleFa = it }
            SubscriptionTextField("زیرعنوان انگلیسی", subtitleEn) { subtitleEn = it }
            SubscriptionTextField("پیام راهنمای پرداخت فارسی", instructionFa, minLines = 3) { instructionFa = it }
            SubscriptionTextField("Payment instruction (English)", instructionEn, minLines = 3) { instructionEn = it }
            SubscriptionTextField("متن دکمه پرداخت فارسی", buttonFa) { buttonFa = it }
            SubscriptionTextField("Payment button text (English)", buttonEn) { buttonEn = it }
            SubscriptionTextField("واحد پول فارسی", currencyFa) { currencyFa = it }
            SubscriptionTextField("Currency label (English)", currencyEn) { currencyEn = it }
            SubscriptionTextField("پیام پیگیری/تأیید فارسی", supportFa, minLines = 3) { supportFa = it }
            SubscriptionTextField("Support / verification message (English)", supportEn, minLines = 3) { supportEn = it }
        }

        val selectedDraftIndex = drafts.indexOfFirst { it.id == selectedSubscriptionSection }
        if (selectedDraftIndex >= 0) item {
            val index = selectedDraftIndex
            val d = drafts[index]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.id, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    SubscriptionTextField("نام فارسی", d.nameFa) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(nameFa = v) }
                    }
                    SubscriptionTextField("نام انگلیسی", d.nameEn) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(nameEn = v) }
                    }
                    SubscriptionTextField("قیمت (تومان)", d.price, keyboardType = KeyboardType.Number) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(price = v.filter { c -> c.isDigit() }) }
                    }
                    SubscriptionTextField("مدت اعتبار (روز)", d.durationDays, keyboardType = KeyboardType.Number) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(durationDays = v.filter { c -> c.isDigit() }) }
                    }
                    SubscriptionTextField("لینک پرداخت فوربیکس", d.paymentUrl) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(paymentUrl = v.trim()) }
                    }
                    SubscriptionTextField("توضیح فارسی", d.descriptionFa, minLines = 2) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(descriptionFa = v) }
                    }
                    SubscriptionTextField("توضیح انگلیسی", d.descriptionEn, minLines = 2) { v ->
                        drafts = drafts.toMutableList().also { it[index] = d.copy(descriptionEn = v) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = d.active,
                            onCheckedChange = { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(active = v) } }
                        )
                        Text(if (lang == "fa") "نمایش این پلن در صفحه خرید" else "Show this plan")
                    }
                }
            }
        }

        item {
            Button(
                enabled = !saving && drafts.size == 3 && drafts.all { it.price.toLongOrNull()?.let { n -> n > 0 } == true && it.paymentUrl.startsWith("https://pay.forbix.ir/") },
                onClick = {
                    saving = true
                    message = ""
                    val plans = drafts.map {
                        mapOf<String, Any>(
                            "id" to it.id,
                            "nameFa" to it.nameFa,
                            "nameEn" to it.nameEn,
                            "price" to (it.price.toLongOrNull() ?: 0L),
                            "durationDays" to (it.durationDays.toIntOrNull() ?: 0),
                            "paymentUrl" to it.paymentUrl,
                            "descriptionFa" to it.descriptionFa,
                            "descriptionEn" to it.descriptionEn,
                            "active" to it.active
                        )
                    }
                    val settings = mapOf<String, Any?>(
                        "pageTitleFa" to titleFa,
                        "pageTitleEn" to titleEn,
                        "pageSubtitleFa" to subtitleFa,
                        "pageSubtitleEn" to subtitleEn,
                        "paymentInstructionFa" to instructionFa,
                        "paymentInstructionEn" to instructionEn,
                        "purchaseButtonFa" to buttonFa,
                        "purchaseButtonEn" to buttonEn,
                        "currencyFa" to currencyFa,
                        "currencyEn" to currencyEn,
                        "supportMessageFa" to supportFa,
                        "supportMessageEn" to supportEn,
                        "plans" to plans
                    )
                    viewModel.updateSubscriptionPlansAdmin(settings) { ok, msg ->
                        saving = false
                        message = if (ok) {
                            if (lang == "fa") "تنظیمات با موفقیت ذخیره شد." else "Settings saved successfully."
                        } else msg
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "..." else if (lang == "fa") "ذخیره تغییرات" else "Save Changes")
            }
            if (message.isNotBlank()) {
                Text(message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}

@Composable
private fun SubscriptionTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
