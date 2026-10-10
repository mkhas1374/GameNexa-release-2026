package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.LicenseViewModel
import com.example.ui.AppAccessState
import com.example.ui.LicenseState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Customer
import com.example.data.BehaviorRule
import com.example.data.PointLog
import com.example.data.ReferralRule
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.*

enum class ClubSection(
    val titleFa: String,
    val titleEn: String,
    val descriptionFa: String,
    val descriptionEn: String,
    val icon: ImageVector,
    val badgeColor: Color
) {
    CUSTOMER_REQUESTS(
        "درخواست‌های مشتری",
        "Customer Requests",
        "بررسی و تایید شارژ آنلاین، کیف پول و تسویه بدهی",
        "Review and approve top-ups, wallet balance and debt settlements",
        Icons.Default.PendingActions,
        Color(0xFFE53935)
    ),
    BASE_GN_RULES(
        "تنظیم قوانین پایه‌ای GN",
        "Base GN Rules",
        "نرخ‌های پاداش بازی، بوفه، ارزش تومانی، خرید و سقف انتقال",
        "Game and buffet reward rates, Toman value and transfer limits",
        Icons.Default.Tune,
        Color(0xFF1E88E5)
    ),
    LP_RULES(
        "مدیریت و قوانین امتیاز LP",
        "LP Rules & Policies",
        "تنظیم نرخ تبدیل تومان به LP، قوانین اعطا/کسر و تغییر دستی LP",
        "Toman-to-LP rates, grant/deduct policies and manual LP adjustments",
        Icons.Default.Stars,
        Color(0xFFF57C00)
    ),
    REFERRAL_RULES(
        "قوانین دریافت امتیاز دعوت",
        "Referral Rules",
        "شروط اعطای پاداش دعوت از دوستان جدید به گیم‌نت",
        "Conditions and bonus rewards for inviting new friends to the venue",
        Icons.Default.GroupAdd,
        Color(0xFF00897B)
    ),
    BEHAVIORAL_RULES(
        "قوانین رفتاری و انضباطی",
        "Behavior & Discipline Rules",
        "قوانین تشویقی و تنبیهی سالن و تغییرات خودکار GN و LP",
        "Disciplinary and reward rules with automated GN/LP adjustments",
        Icons.Default.Gavel,
        Color(0xFF8E24AA)
    ),
    LOYALTY_LEVELS(
        "تنظیم قوانین سطوح وفاداری",
        "Loyalty Levels",
        "پیکربندی سطوح برنزی، نقره‌ای، طلایی و الماسی (VIP)",
        "Configure Bronze, Silver, Gold, and Diamond VIP membership tiers",
        Icons.Default.WorkspacePremium,
        Color(0xFFFB8C00)
    ),
    SCORING_RULES(
        "قوانین امتیازدهی و پاداش‌ها",
        "Scoring & Reward Rules",
        "تنظیم امتیازات بازی، بوفه، مناسبت‌ها و امتیازدهی دستی",
        "Configure rewards for games, buffet, events and manual scoring",
        Icons.Default.Rule,
        Color(0xFF039BE5)
    ),
    CLUB_MEMBERS(
        "لیست اعضای باشگاه",
        "Club Member List",
        "مشاهده اعضای باشگاه، سوابق فاکتور، پرونده و شارژ دستی",
        "View members, invoices, activity logs and manual wallet recharge",
        Icons.Default.People,
        Color(0xFF43A047)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerClubScreen(
    viewModel: GameNetViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf<ClubSection?>(null) }
    val lang by viewModel.language.collectAsState()
    
    val isTrialActive by viewModel.isTrialModeFlow.collectAsState()
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    val isTrialUser = isTrialActive || currentAdminRole == "TRIAL_USER" || viewModel.isTrialUser
    var showTrialLockDialog by remember { mutableStateOf(false) }

    if (showTrialLockDialog) {
        AlertDialog(
            onDismissRequest = { showTrialLockDialog = false },
            title = { Text(if (lang == "fa") "نسخه آزمایشی 24 ساعته" else "24h Trial Version", fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == "fa") "امکانات باشگاه مشتریان در نسخه آزمایشی قفل است. لطفاً اشتراک تهیه فرمایید." else "Customer Club features are locked in trial mode. Please purchase a subscription.") },
            confirmButton = {
                Button(onClick = { 
                    showTrialLockDialog = false
                    viewModel.handleAccessDenied()
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

    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("customer_club_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isTrialUser) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = if (lang == "fa") "باشگاه مشتریان در نسخه آزمایشی قفل است" else "Customer Club Locked in Trial Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Text(
                            text = if (lang == "fa") 
                                "تمامی امکانات باشگاه مشتریان شامل سطوح عضویت، تخفیف‌های وفاداری، گردونه شانس، قرعه‌کشی و پیامک‌ها در نسخه 24 ساعته غیرفعال می‌باشند."
                            else 
                                "All Customer Club features including membership tiers, loyalty discounts, lucky wheel, lottery, and SMS notifications are locked in the 24-hour trial mode.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Button(
                            onClick = { viewModel.handleAccessDenied() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.CardMembership, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (lang == "fa") "خرید و فعال‌سازی اشتراک" else "Upgrade / Buy Subscription",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (selectedSection == null) {
            ClubMenuHub(
                viewModel = viewModel,
                lang = lang,
                onSelectSection = { 
                    if (isTrialActive) {
                        showTrialLockDialog = true
                    } else {
                        selectedSection = it
                    }
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                ClubSubPageHeader(
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
                        ClubSection.CUSTOMER_REQUESTS -> CustomerRequestsSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.BASE_GN_RULES -> BaseGnRulesSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.LP_RULES -> LpRulesSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.REFERRAL_RULES -> ReferralRulesSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.BEHAVIORAL_RULES -> BehavioralRulesSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.LOYALTY_LEVELS -> LoyaltyLevelsSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.SCORING_RULES -> ScoringRulesSubScreen(viewModel = viewModel, lang = lang)
                        ClubSection.CLUB_MEMBERS -> ClubMembersSubScreen(viewModel = viewModel, lang = lang)
                    }
                }
            }
        }
    }
}

@Composable
fun ClubMenuHub(
    viewModel: GameNetViewModel,
    lang: String,
    onSelectSection: (ClubSection) -> Unit
) {
    val payments by SelfHostedManager.recentPayments.collectAsState()
    val pendingCount = remember(payments) { payments.count { it.status == "PENDING" } }
    val customers by viewModel.customers.collectAsState()
    val qualifiedMembersCount = remember(customers) { customers.count { it.fullName.isNotBlank() && it.phoneNumber.isNotBlank() } }

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
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_customer_club_loyalty_hub_21),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_select_a_section_below_to_mana_22),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_club_options_menu_23),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        items(ClubSection.values()) { section ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSection(section) }
                    .testTag("club_item_${section.name.lowercase()}")
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
                            if (section == ClubSection.CUSTOMER_REQUESTS && pendingCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (lang == "fa") "$pendingCount جدید" else "$pendingCount New",
                                        color = MaterialTheme.colorScheme.onError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else if (section == ClubSection.CLUB_MEMBERS && qualifiedMembersCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = if (lang == "fa") "$qualifiedMembersCount عضو" else "$qualifiedMembersCount Members",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
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
fun ClubSubPageHeader(
    section: ClubSection,
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
                modifier = Modifier.testTag("club_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (lang == "fa") "بازگشت" else "Back",
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
// 1. CUSTOMER REQUESTS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun CustomerRequestsSubScreen(viewModel: GameNetViewModel, lang: String) {
    val payments by SelfHostedManager.recentPayments.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val pendingPayments = remember(payments) { payments.filter { it.status == "PENDING" } }
        val approvedPayments = remember(payments) { payments.filter { it.status != "PENDING" }.take(15) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.PendingActions, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (lang == "fa") "درخواست‌های شارژ آنلاین و خرید GN در انتظار تایید" else "Pending Online Top-ups & GN Purchases",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    if (pendingPayments.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (lang == "fa") "${pendingPayments.size} درخواست جدید" else "${pendingPayments.size} New Requests",
                                color = MaterialTheme.colorScheme.onError,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (pendingPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "fa") "هیچ درخواست شارژ آنلاینی در انتظار تایید نیست ✅" else "No pending online recharge requests ✅",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    pendingPayments.forEach { p ->
                        val typeTitle = when (p.paymentType) {
                            "BUY_POINTS" -> if (lang == "fa") "شارژ امتیاز GN" else "Buy GN Points"
                            "CHARGE_WALLET" -> if (lang == "fa") "شارژ کیف پول" else "Top-up Wallet"
                            "PAY_DEBT" -> if (lang == "fa") "تسویه بدهی" else "Settle Debt"
                            else -> if (lang == "fa") "پرداخت آنلاین" else "Online Payment"
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${p.customerName} - $typeTitle",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lang == "fa") "مبلغ: ${DecimalFormat("#,###").format(p.amount)} تومان | امتیاز: ${p.pointsGained.toInt()} GN" else "Amount: ${DecimalFormat("#,###").format(p.amount)} Toman | Points: ${p.pointsGained.toInt()} GN",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (lang == "fa") "کد پیگیری: ${p.trackingCode}" else "Tracking Code: ${p.trackingCode}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val ok = SelfHostedManager.approvePendingPayment(p)
                                            if (ok) {
                                                viewModel.logOperatorActivity(
                                                    "تایید شارژ آنلاین",
                                                    "تایید شارژ $typeTitle برای ${p.customerName} به مبلغ ${p.amount} تومان و واریز 100 امتیاز پاداش"
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(if (lang == "fa") "تایید و شارژ (+100 GN)" else "Approve (+100 GN)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (approvedPayments.isNotEmpty()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = if (lang == "fa") "تاریخچه درخواست‌های اخیر تاییدشده:" else "Recent Approved Requests:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    approvedPayments.forEach { p ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${p.customerName} (${DecimalFormat("#,###").format(p.amount)} ${if (lang == "fa") "تومان" else "Toman"})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${if (lang == "fa") "کد" else "Code"}: ${p.trackingCode}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF2E7D32).copy(alpha = 0.15f)) {
                                    Text(
                                        text = if (lang == "fa") "تایید شده" else "Approved",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. BASE GN RULES SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun BaseGnRulesSubScreen(viewModel: GameNetViewModel, lang: String) {
    val gameRewardRate by viewModel.gameRewardRate.collectAsState()
    val buffetRewardRate by viewModel.buffetRewardRate.collectAsState()
    val referralRewardGn by viewModel.referralRewardGn.collectAsState()
    val referralQualificationAmount by viewModel.referralQualificationAmount.collectAsState()
    val gnToTomanRate by viewModel.gnToTomanRate.collectAsState()
    val gnPurchaseRateToman by viewModel.gnPurchaseRateToman.collectAsState()
    val gnPurchaseEnabled by viewModel.gnPurchaseEnabled.collectAsState()
    val transferMinGn by viewModel.transferMinGn.collectAsState()
    val transferDailyLimitGn by viewModel.transferDailyLimitGn.collectAsState()
    val transferFeePercent by viewModel.transferFeePercent.collectAsState()

    var gameRewardInput by remember(gameRewardRate) { mutableStateOf(gameRewardRate.toInt().toString()) }
    var buffetRewardInput by remember(buffetRewardRate) { mutableStateOf(buffetRewardRate.toInt().toString()) }
    var refRewardInput by remember(referralRewardGn) { mutableStateOf(referralRewardGn.toInt().toString()) }
    var refQualInput by remember(referralQualificationAmount) { mutableStateOf(referralQualificationAmount.toInt().toString()) }
    var gnTomanRateInput by remember(gnToTomanRate) { mutableStateOf(gnToTomanRate.toInt().toString()) }
    var purchaseRateInput by remember(gnPurchaseRateToman) { mutableStateOf(gnPurchaseRateToman.toInt().toString()) }
    var minTransferInput by remember(transferMinGn) { mutableStateOf(transferMinGn.toInt().toString()) }
    var dailyLimitTransferInput by remember(transferDailyLimitGn) { mutableStateOf(transferDailyLimitGn.toInt().toString()) }
    var feePercentInput by remember(transferFeePercent) { mutableStateOf(transferFeePercent.toString()) }
    var purchaseEnabledToggle by remember(gnPurchaseEnabled) { mutableStateOf(gnPurchaseEnabled) }

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
                    text = if (lang == "fa") "نرخ‌های پاداش و اعطای خودکار GN" else "GN Reward Rates & Automatic Grants",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = gameRewardInput,
                        onValueChange = { gameRewardInput = it },
                        label = { Text(if (lang == "fa") "GN بازی (به ازای 10,000 تومان)" else "Game GN (per 10k T)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = buffetRewardInput,
                        onValueChange = { buffetRewardInput = it },
                        label = { Text(if (lang == "fa") "GN بوفه (به ازای 10,000 تومان)" else "Buffet GN (per 10k T)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = if (lang == "fa") "ارزش‌گذاری و خرید مستقیم GN" else "GN Valuation & Direct Purchase",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = gnTomanRateInput,
                        onValueChange = { gnTomanRateInput = it },
                        label = { Text(if (lang == "fa") "ارزش هر 1 GN به تومان (1 GN = 400 تومان)" else "Value of 1 GN in Toman (1 GN = 400 T)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = purchaseRateInput,
                        onValueChange = { purchaseRateInput = it },
                        label = { Text(if (lang == "fa") "قیمت خرید مستقیم هر GN (تومان)" else "Direct Purchase Price per GN (Toman)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = if (lang == "fa") "قوانین و محدودیت‌های انتقال GN بین مشتریان" else "GN Transfer Rules & Limits Between Customers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minTransferInput,
                        onValueChange = { minTransferInput = it },
                        label = { Text(if (lang == "fa") "حداقل انتقال (GN)" else "Min Transfer (GN)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = dailyLimitTransferInput,
                        onValueChange = { dailyLimitTransferInput = it },
                        label = { Text(if (lang == "fa") "سقف روزانه انتقال (GN)" else "Daily Transfer Limit (GN)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = feePercentInput,
                        onValueChange = { feePercentInput = it },
                        label = { Text(if (lang == "fa") "کارمزد انتقال (%)" else "Transfer Fee (%)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = purchaseEnabledToggle,
                            onCheckedChange = { purchaseEnabledToggle = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == "fa") "خرید مستقیم GN فعال باشد" else "Enable Direct GN Purchase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetGnRulesToDefault()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "fa") "پیش‌فرض سیستم" else "System Defaults", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                val g = gameRewardInput.toLongOrNull() ?: 10L
                                val b = buffetRewardInput.toLongOrNull() ?: 5L
                                val r = refRewardInput.toLongOrNull() ?: 100L
                                val rq = refQualInput.toLongOrNull() ?: 100000L
                                val gt = gnTomanRateInput.toLongOrNull() ?: 400L
                                val pr = purchaseRateInput.toLongOrNull() ?: 500L
                                val mt = minTransferInput.toLongOrNull() ?: 50L
                                val dt = dailyLimitTransferInput.toLongOrNull() ?: 1000L
                                val fee = feePercentInput.toLongOrNull() ?: 5L

                                viewModel.saveSystemPolicy(
                                    gameReward = g,
                                    buffetReward = b,
                                    refReward = r,
                                    refQualAmount = rq,
                                    gnTomanRate = gt,
                                    purchaseRate = pr,
                                    purchaseEnabled = purchaseEnabledToggle,
                                    minTransfer = mt,
                                    dailyLimitTransfer = dt,
                                    feePercent = fee
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "fa") "ذخیره قوانین GN" else "Save GN Rules", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. REFERRAL RULES SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun ReferralRulesSubScreen(viewModel: GameNetViewModel, lang: String) {
    val referralRules by viewModel.referralRules.collectAsState()
    val minInviteSpendAmount by viewModel.minInviteSpendAmount.collectAsState()

    var inviteSpendInput by remember(minInviteSpendAmount) { mutableStateOf(minInviteSpendAmount.toInt().toString()) }
    var showAddRefRuleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Threshold Quick Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inviteSpendInput,
                        onValueChange = { inviteSpendInput = it },
                        label = { Text(if (lang == "fa") "حداقل هزینه کاربر دعوت‌شده برای فعال‌سازی (تومان)" else "Min Invitee Spend to Qualify (Toman)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            val s = inviteSpendInput.toLongOrNull() ?: 0L
                            viewModel.saveInviteSettings(0L, s)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == "fa") "ذخیره شرط هزینه" else "Save Spend Req", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Custom Referral Rules List
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "قوانین تفصیلی پاداش معرفی دوستان" else "Detailed Referral Bonus Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showAddRefRuleDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "قانون جدید" else "New Rule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (referralRules.isEmpty()) {
                    Text(if (lang == "fa") "هیچ قانون تفصیلی ثبت نشده است." else "No detailed referral rules registered.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    referralRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (rule.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (rule.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rule.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (rule.isEnabled) Color(0xFF2E7D32).copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (rule.isEnabled) (if (lang == "fa") "فعال" else "Active") else (if (lang == "fa") "غیرفعال" else "Inactive"),
                                                color = if (rule.isEnabled) Color(0xFF2E7D32) else Color.Gray,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (rule.description.isNotBlank()) {
                                        Text(rule.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        if (lang == "fa") "شرط: ${rule.requiredAmount.toInt()} تومان | پاداش: ${rule.rewardGn.toInt()} GN" else "Req: ${rule.requiredAmount.toInt()} Toman | Reward: ${rule.rewardGn.toInt()} GN",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = rule.isEnabled,
                                        onCheckedChange = { viewModel.toggleReferralRule(rule.id, it) }
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteReferralRule(rule.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRefRuleDialog) {
        var refRuleTitle by remember { mutableStateOf("") }
        var refRuleDesc by remember { mutableStateOf("") }
        var refRuleAmount by remember { mutableStateOf("100000") }
        var refRuleGn by remember { mutableStateOf("100") }

        AlertDialog(
            onDismissRequest = { showAddRefRuleDialog = false },
            title = { Text(if (lang == "fa") "تعریف قانون دعوت از دوستان جدید" else "Define Referral Rule", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = refRuleTitle,
                        onValueChange = { refRuleTitle = it },
                        label = { Text(if (lang == "fa") "عنوان قانون" else "Rule Title", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = refRuleDesc,
                        onValueChange = { refRuleDesc = it },
                        label = { Text(if (lang == "fa") "توضیحات کوتاه" else "Short Description", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = refRuleAmount,
                            onValueChange = { refRuleAmount = it },
                            label = { Text(if (lang == "fa") "شرط بازی (تومان)" else "Play Req (Toman)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = refRuleGn,
                            onValueChange = { refRuleGn = it },
                            label = { Text(if (lang == "fa") "پاداش (GN)" else "Reward (GN)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (refRuleTitle.isNotBlank()) {
                            val amt = refRuleAmount.toLongOrNull() ?: 100000L
                            val gn = refRuleGn.toLongOrNull() ?: 100L
                            viewModel.addOrUpdateReferralRule(
                                ReferralRule(
                                    id = java.util.UUID.randomUUID().toString(),
                                    title = refRuleTitle,
                                    description = refRuleDesc,
                                    type = "GAMING_SPEND",
                                    requiredAmount = amt,
                                    rewardGn = gn,
                                    isEnabled = true
                                )
                            )
                            showAddRefRuleDialog = false
                        }
                    }
                ) { Text(if (lang == "fa") "ثبت قانون" else "Save Rule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRefRuleDialog = false }) { Text(if (lang == "fa") "انصراف" else "Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// 4. BEHAVIORAL & DISCIPLINE RULES SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun BehavioralRulesSubScreen(viewModel: GameNetViewModel, lang: String) {
    val behaviorRules by viewModel.allBehaviorRules.collectAsState()
    var showAddRuleDialog by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "قوانین رفتاری و انضباطی سالن گیم‌نت" else "Disciplinary & Behavioral Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.resetBehaviorRulesToDefault() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "fa") "پیش‌فرض" else "Default", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showAddRuleDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "fa") "قانون جدید" else "New Rule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (behaviorRules.isEmpty()) {
                    Text(if (lang == "fa") "هیچ قانون رفتاری ثبت نشده است." else "No behavioral rules registered.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    behaviorRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (rule.description.isNotBlank()) {
                                        Text(rule.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "GN: ${if (rule.gnChange > 0) "+${rule.gnChange.toInt()}" else rule.gnChange.toInt()}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rule.gnChange >= 0) Color(0xFF2E7D32) else Color(0xFFD32F2F)
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteBehaviorRule(rule) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        var ruleTitle by remember { mutableStateOf("") }
        var ruleDesc by remember { mutableStateOf("") }
        var ruleGnChange by remember { mutableStateOf("20") }
        var ruleLpChange by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text(if (lang == "fa") "تعریف قانون رفتاری جدید" else "Define New Behavior Rule", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ruleTitle,
                        onValueChange = { ruleTitle = it },
                        label = { Text(if (lang == "fa") "عنوان قانون (مثلا: همکاری در نظافت سالن)" else "Rule Title (e.g. Venue Cleanliness)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = ruleDesc,
                        onValueChange = { ruleDesc = it },
                        label = { Text(if (lang == "fa") "توضیحات کوتاه" else "Short Description", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ruleGnChange,
                            onValueChange = { ruleGnChange = it },
                            label = { Text(if (lang == "fa") "تغییر GN (+/ -)" else "GN (+/ -)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = ruleLpChange,
                            onValueChange = { ruleLpChange = it },
                            label = { Text(if (lang == "fa") "تغییر LP (+/ -)" else "LP (+/ -)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleTitle.isNotBlank()) {
                            val gn = ruleGnChange.toLongOrNull() ?: 0L
                            val lp = ruleLpChange.toLongOrNull() ?: 0L
                            viewModel.addOrUpdateBehaviorRule(
                                com.example.data.BehaviorRule(
                                    title = ruleTitle,
                                    description = ruleDesc,
                                    gnChange = gn,
                                    lpChange = lp
                                )
                            )
                            showAddRuleDialog = false
                        }
                    }
                ) { Text(if (lang == "fa") "ثبت قانون" else "Save Rule") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) { Text(if (lang == "fa") "انصراف" else "Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// 5. LOYALTY LEVELS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun LoyaltyLevelsSubScreen(viewModel: GameNetViewModel, lang: String) {
    val clubLevels by viewModel.clubLevels.collectAsState()
    val isEvaluatingAbsence by viewModel.isEvaluatingAbsence.collectAsState()
    val absenceEvaluationResult by viewModel.absenceEvaluationResult.collectAsState()
    var editingLevel by remember { mutableStateOf<ClubLevel?>(null) }

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "تنظیم قوانین و سطوح وفاداری (Loyalty Tiers)" else "Loyalty Tiers & Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { viewModel.resetClubLevelsToDefault() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (lang == "fa") "پیش‌فرض" else "Default", fontSize = 11.sp)
                    }
                }
                Text(
                    text = if (lang == "fa") "جهت ویرایش تمام شرایط، پاداش‌ها، فرجه و مزایای غیرمالی بر روی سطح مورد نظر لمس کنید:" else "Tap a tier to edit conditions, rewards, grace period, and non-financial perks:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                clubLevels.forEach { level ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingLevel = level },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${if (lang == "fa") "سطح" else "Tier"}: ${level.name}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = if (lang == "fa") "ارتقا: ${String.format(Locale.US, "%,d", level.requiredPoints)} LP | ماندن: ${String.format(Locale.US, "%,d", level.retainLpPoints)} LP" else "Upgrade: ${String.format(Locale.US, "%,d", level.requiredPoints)} LP | Retain: ${String.format(Locale.US, "%,d", level.retainLpPoints)} LP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(if (lang == "fa") "⏳ فرجه: ${level.graceDays} روز" else "⏳ Grace: ${level.graceDays} d", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(if (lang == "fa") "🗓 مراجعه: ${level.minVisitDays} روز" else "🗓 Visits: ${level.minVisitDays} d", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(if (lang == "fa") "🎁 ارتقا: ${level.reachGnBonus.toInt()} GN" else "🎁 Bonus: ${level.reachGnBonus.toInt()} GN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(if (lang == "fa") "🎮 بازی: ${level.gameGnPercent.toInt()}%" else "🎮 Game: ${level.gameGnPercent.toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Text(if (lang == "fa") "🍔 بوفه: ${level.buffetGnPercent.toInt()}%" else "🍔 Buffet: ${level.buffetGnPercent.toInt()}%", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                Text(if (lang == "fa") "👥 دعوت: ${level.inviteGnReward.toInt()} GN" else "👥 Invite: ${level.inviteGnReward.toInt()} GN", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            }

                            val activePerksCount = level.nonFinancialPerks.count { it.isActive }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = if (lang == "fa") "⭐ مزایای غیرمالی: ${level.nonFinancialPerks.size} مورد ($activePerksCount فعال)" else "⭐ Non-Financial Perks: ${level.nonFinancialPerks.size} ($activePerksCount Active)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Automated Absence Penalty System Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.HourglassBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lang == "fa") "سیستم خودکار جریمه غیبت (Absence Penalty)" else "Absence Penalty Automation",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = { viewModel.triggerAbsenceEvaluation() },
                        enabled = !isEvaluatingAbsence,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isEvaluatingAbsence) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "fa") "بررسی..." else "Evaluating...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "fa") "بررسی و اعمال جریمه‌ها" else "Evaluate & Apply Penalties", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = if (lang == "fa") "قانون انضباطی: تا 20 روز عدم مراجعه هیچ جریمه‌ای ندارد. از روز 21، به ازای هر روز غیبت 5٪ از موجودی فعلی مشتری کسر می‌شود (سقف جریمه کل دوره غیبت: 30٪ موجودی مبنا). موجودی‌ها هرگز منفی نمی‌شوند." else "Disciplinary rule: Grace period of 20 days absence. From day 21, 5% of current balance is deducted per absent day (max cumulative 30%). Balance never goes negative.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                if (!absenceEvaluationResult.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = absenceEvaluationResult ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearAbsenceEvaluationResult() },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingLevel != null) {
        val lvl = editingLevel!!
        var nameInput by remember(lvl) { mutableStateOf(lvl.name) }
        var ptsInput by remember(lvl) { mutableStateOf(lvl.requiredPoints.toInt().toString()) }
        var retainLpInput by remember(lvl) { mutableStateOf(lvl.retainLpPoints.toInt().toString()) }
        var graceDaysInput by remember(lvl) { mutableStateOf(lvl.graceDays.toString()) }
        var minVisitDaysInput by remember(lvl) { mutableStateOf(lvl.minVisitDays.toString()) }
        var maxAbsenceDaysInput by remember(lvl) { mutableStateOf(lvl.maxAbsenceWithoutPenaltyDays.toString()) }

        var reachGnBonusInput by remember(lvl) { mutableStateOf(lvl.reachGnBonus.toInt().toString()) }
        var gameGnPercentInput by remember(lvl) { mutableStateOf(lvl.gameGnPercent.toInt().toString()) }
        var buffetGnPercentInput by remember(lvl) { mutableStateOf(lvl.buffetGnPercent.toInt().toString()) }
        var maxGnPaymentPercentInput by remember(lvl) { mutableStateOf(lvl.maxGnPaymentPercent.toInt().toString()) }
        var inviteGnRewardInput by remember(lvl) { mutableStateOf(lvl.inviteGnReward.toInt().toString()) }
        var accessSpecialEventsInput by remember(lvl) { mutableStateOf(lvl.accessSpecialEvents) }

        var gameDiscInput by remember(lvl) { mutableStateOf(lvl.gameDiscountPercent.toString()) }
        var buffetDiscInput by remember(lvl) { mutableStateOf(lvl.buffetDiscountPercent.toString()) }
        var fixedDiscInput by remember(lvl) { mutableStateOf(lvl.fixedDiscountToman.toString()) }
        var freeHoursInput by remember(lvl) { mutableStateOf(lvl.freePlayHours.toString()) }
        var rewardsTextInput by remember(lvl) { mutableStateOf(lvl.rewardsText) }

        val editablePerks = remember(lvl) { mutableStateListOf<NonFinancialPerk>().apply { addAll(lvl.nonFinancialPerks) } }

        AlertDialog(
            onDismissRequest = { editingLevel = null },
            title = { Text(if (lang == "fa") "ویرایش سطح وفاداری ${lvl.name}" else "Edit Loyalty Tier: ${lvl.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section 1: Conditions & LP
                    Text(if (lang == "fa") "1. شرایط ارتقا، ماندن و فرجه (بر اساس LP)" else "1. LP, Retention & Grace Conditions", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text(if (lang == "fa") "نام سطح" else "Tier Name", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ptsInput,
                            onValueChange = { ptsInput = it },
                            label = { Text(if (lang == "fa") "LP ارتقا به این سطح" else "Upgrade LP", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retainLpInput,
                            onValueChange = { retainLpInput = it },
                            label = { Text(if (lang == "fa") "LP ماندن در سطح" else "Retain LP", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = graceDaysInput,
                            onValueChange = { graceDaysInput = it },
                            label = { Text(if (lang == "fa") "روزهای فرجه کسب LP" else "Grace Days", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minVisitDaysInput,
                            onValueChange = { minVisitDaysInput = it },
                            label = { Text(if (lang == "fa") "حداقل روزهای مراجعه" else "Min Visits (Days)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = maxAbsenceDaysInput,
                        onValueChange = { maxAbsenceDaysInput = it },
                        label = { Text(if (lang == "fa") "حداکثر غیبت مجاز بدون جریمه (روز)" else "Max Allowed Absence (Days)", fontSize = 10.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Section 2: GN Bonuses & Payments
                    Text(if (lang == "fa") "2. پاداش‌ها، تخفیف‌ها و دریافت سکه‌های GN" else "2. GN Rewards, Discounts & Coins", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = reachGnBonusInput,
                            onValueChange = { reachGnBonusInput = it },
                            label = { Text(if (lang == "fa") "پاداش GN رسیدن به سطح" else "Tier Reach GN Bonus", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = inviteGnRewardInput,
                            onValueChange = { inviteGnRewardInput = it },
                            label = { Text(if (lang == "fa") "GN دعوت از دوستان" else "Invite GN Bonus", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gameGnPercentInput,
                            onValueChange = { gameGnPercentInput = it },
                            label = { Text(if (lang == "fa") "درصد GN برای بازی (%)" else "Game GN (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = buffetGnPercentInput,
                            onValueChange = { buffetGnPercentInput = it },
                            label = { Text(if (lang == "fa") "درصد GN برای بوفه (%)" else "Buffet GN (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxGnPaymentPercentInput,
                            onValueChange = { maxGnPaymentPercentInput = it },
                            label = { Text(if (lang == "fa") "سقف پرداخت بازی با GN (%)" else "Max GN Payment (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = accessSpecialEventsInput,
                                onCheckedChange = { accessSpecialEventsInput = it }
                            )
                            Text(if (lang == "fa") "دسترسی به رویدادهای ویژه" else "Special Events Access", fontSize = 10.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gameDiscInput,
                            onValueChange = { gameDiscInput = it },
                            label = { Text(if (lang == "fa") "تخفیف بازی (%)" else "Game Disc (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = buffetDiscInput,
                            onValueChange = { buffetDiscInput = it },
                            label = { Text(if (lang == "fa") "تخفیف بوفه (%)" else "Buffet Disc (%)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = rewardsTextInput,
                        onValueChange = { rewardsTextInput = it },
                        label = { Text(if (lang == "fa") "توضیحات خلاصه سطح" else "Tier Summary Description", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Section 3: Non-Financial Perks Manager
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (lang == "fa") "3. مزایای غیرمالی (Non-Financial Perks)" else "3. Non-Financial Perks", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = {
                            editablePerks.add(
                                NonFinancialPerk(
                                    title = if (lang == "fa") "مزیت غیرمالی جدید" else "New Non-Financial Perk",
                                    description = if (lang == "fa") "توضیحات مزیت" else "Perk description",
                                    isActive = true
                                )
                            )
                        }) {
                            Text(if (lang == "fa") "+ افزودن مزیت" else "+ Add Perk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    editablePerks.forEachIndexed { index, perk ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (perk.isActive) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = perk.isActive,
                                            onCheckedChange = { isChecked ->
                                                editablePerks[index] = perk.copy(isActive = isChecked)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (perk.isActive) (if (lang == "fa") "فعال" else "Active") else (if (lang == "fa") "غیرفعال" else "Inactive"), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(
                                        onClick = { editablePerks.removeAt(index) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }

                                OutlinedTextField(
                                    value = perk.title,
                                    onValueChange = { editablePerks[index] = perk.copy(title = it) },
                                    label = { Text(if (lang == "fa") "عنوان مزیت" else "Perk Title", fontSize = 9.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = perk.description,
                                    onValueChange = { editablePerks[index] = perk.copy(description = it) },
                                    label = { Text(if (lang == "fa") "توضیحات مزیت" else "Perk Description", fontSize = 9.sp) },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = perk.startDate,
                                        onValueChange = { editablePerks[index] = perk.copy(startDate = it) },
                                        label = { Text(if (lang == "fa") "تاریخ شروع (مثلا 1403/01/01)" else "Start Date", fontSize = 8.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = perk.endDate,
                                        onValueChange = { editablePerks[index] = perk.copy(endDate = it) },
                                        label = { Text(if (lang == "fa") "تاریخ پایان (اختیاری)" else "End Date (Optional)", fontSize = 8.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newPts = ptsInput.toLongOrNull() ?: lvl.requiredPoints
                    val newRetainLp = retainLpInput.toLongOrNull() ?: lvl.retainLpPoints
                    val newGraceDays = graceDaysInput.toIntOrNull() ?: lvl.graceDays
                    val newMinVisitDays = minVisitDaysInput.toIntOrNull() ?: lvl.minVisitDays
                    val newMaxAbsenceDays = maxAbsenceDaysInput.toIntOrNull() ?: lvl.maxAbsenceWithoutPenaltyDays

                    val newReachGnBonus = reachGnBonusInput.toLongOrNull() ?: lvl.reachGnBonus
                    val newGameGnPercent = gameGnPercentInput.toLongOrNull() ?: lvl.gameGnPercent
                    val newBuffetGnPercent = buffetGnPercentInput.toLongOrNull() ?: lvl.buffetGnPercent
                    val newMaxGnPayment = maxGnPaymentPercentInput.toLongOrNull() ?: lvl.maxGnPaymentPercent
                    val newInviteGn = inviteGnRewardInput.toLongOrNull() ?: lvl.inviteGnReward

                    val gameDisc = gameDiscInput.toLongOrNull() ?: lvl.gameDiscountPercent
                    val buffetDisc = buffetDiscInput.toLongOrNull() ?: lvl.buffetDiscountPercent
                    val fixedDisc = fixedDiscInput.toLongOrNull() ?: lvl.fixedDiscountToman
                    val freeHours = freeHoursInput.toLongOrNull() ?: lvl.freePlayHours

                    val updatedList = clubLevels.map {
                        if (it.id == lvl.id) {
                            it.copy(
                                name = nameInput,
                                requiredPoints = newPts,
                                retainLpPoints = newRetainLp,
                                graceDays = newGraceDays,
                                minVisitDays = newMinVisitDays,
                                maxAbsenceWithoutPenaltyDays = newMaxAbsenceDays,
                                reachGnBonus = newReachGnBonus,
                                gameGnPercent = newGameGnPercent,
                                buffetGnPercent = newBuffetGnPercent,
                                maxGnPaymentPercent = newMaxGnPayment,
                                inviteGnReward = newInviteGn,
                                accessSpecialEvents = accessSpecialEventsInput,
                                gameDiscountPercent = gameDisc,
                                buffetDiscountPercent = buffetDisc,
                                fixedDiscountToman = fixedDisc,
                                freePlayHours = freeHours,
                                rewardsText = rewardsTextInput,
                                nonFinancialPerks = editablePerks.toList()
                            )
                        } else it
                    }
                    viewModel.saveClubLevels(updatedList)
                    editingLevel = null
                }) {
                    Text(if (lang == "fa") "ذخیره تغییرات" else "Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLevel = null }) { Text(if (lang == "fa") "انصراف" else "Cancel", fontSize = 11.sp) }
            }
        )
    }
}

// -------------------------------------------------------------
// 6. SCORING RULES SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun ScoringRulesSubScreen(viewModel: GameNetViewModel, lang: String) {
    val scoringRules by viewModel.scoringRules.collectAsState()
    var editingRule by remember { mutableStateOf<ScoringRule?>(null) }
    var showNewRuleDialog by remember { mutableStateOf(false) }

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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "قوانین امتیازدهی و پاداش‌های سیستم" else "Scoring Rules & System Rewards",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.resetScoringRulesToDefault() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (lang == "fa") "پیش‌فرض" else "Default", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showNewRuleDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (lang == "fa") "افزودن قانون" else "Add Rule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                scoringRules.forEach { rule ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingRule = rule }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Badge(containerColor = if (rule.points >= 0) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)) {
                                Text(
                                    text = "${if (rule.points > 0) "+" else ""}${String.format(Locale.US, "%,d", rule.points)} ${if (lang == "fa") "امتیاز" else "Pts"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (rule.points >= 0) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingRule != null) {
        var titleInput by remember { mutableStateOf(editingRule!!.title) }
        var ptsInput by remember { mutableStateOf(editingRule!!.points.toInt().toString()) }

        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text(if (lang == "fa") "ویرایش قانون امتیازدهی" else "Edit Scoring Rule", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text(if (lang == "fa") "عنوان قانون" else "Rule Title", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ptsInput,
                        onValueChange = { ptsInput = it },
                        label = { Text(if (lang == "fa") "امتیاز (مثبت یا منفی)" else "Points (+ or -)", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val updatedList = scoringRules.filter { it.id != editingRule!!.id }
                        viewModel.saveScoringRules(updatedList)
                        editingRule = null
                    }) {
                        Text(if (lang == "fa") "حذف" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = {
                        val newPts = ptsInput.toLongOrNull() ?: editingRule!!.points
                        val updatedList = scoringRules.map {
                            if (it.id == editingRule!!.id) it.copy(title = titleInput, points = newPts) else it
                        }
                        viewModel.saveScoringRules(updatedList)
                        editingRule = null
                    }) {
                        Text(if (lang == "fa") "ذخیره" else "Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { editingRule = null }) { Text(if (lang == "fa") "لغو" else "Cancel") }
            }
        )
    }

    if (showNewRuleDialog) {
        var titleInput by remember { mutableStateOf("") }
        var ptsInput by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showNewRuleDialog = false },
            title = { Text(if (lang == "fa") "افزودن قانون جدید امتیازدهی" else "Add New Scoring Rule", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text(if (lang == "fa") "عنوان قانون (مثلا دعوت مخاطب)" else "Rule Title (e.g. Invite)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ptsInput,
                        onValueChange = { ptsInput = it },
                        label = { Text(if (lang == "fa") "مقدار امتیاز" else "Points Amount", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (titleInput.isNotBlank()) {
                        val newPts = ptsInput.toLongOrNull() ?: 10L
                        val newRule = ScoringRule(
                            id = "rule_${System.currentTimeMillis()}",
                            title = titleInput,
                            points = newPts
                        )
                        viewModel.saveScoringRules(scoringRules + newRule)
                        showNewRuleDialog = false
                    }
                }) {
                    Text(if (lang == "fa") "افزودن" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewRuleDialog = false }) { Text(if (lang == "fa") "لغو" else "Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// 7. CLUB MEMBERS SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun ClubMembersSubScreen(viewModel: GameNetViewModel, lang: String) {
    val customers by viewModel.customers.collectAsState()
    val clubLevels by viewModel.clubLevels.collectAsState()
    val allPointLogs by viewModel.allPointLogs.collectAsState(initial = emptyList())

    var activeHistoryCustomer by remember { mutableStateOf<Customer?>(null) }
    var showManualPointDialog by remember { mutableStateOf(false) }

    var selectedFilterCustomer by remember { mutableStateOf<Long?>(null) }
    var selectedFilterType by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var reportCardExpanded by remember { mutableStateOf(false) }

    val qualifiedCustomers by remember(customers) {
        derivedStateOf { customers.filter { it.fullName.isNotBlank() && it.phoneNumber.isNotBlank() } }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Point History Report Card
        item {
            PointHistoryReportCard(
                allLogs = allPointLogs,
                customers = customers,
                selectedCustomerId = selectedFilterCustomer,
                onSelectCustomerId = { selectedFilterCustomer = it },
                selectedFilterType = selectedFilterType,
                onSelectFilterType = { selectedFilterType = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                expanded = reportCardExpanded,
                onToggleExpand = { reportCardExpanded = !reportCardExpanded },
                onOpenAddManualPoint = { showManualPointDialog = true },
                lang = lang
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "fa") "👥 اعضای باشگاه (${qualifiedCustomers.size} نفر)" else "👥 Club Members (${qualifiedCustomers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(
                    onClick = { showManualPointDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == "fa") "ثبت امتیاز دستی" else "Add Manual Pts", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (qualifiedCustomers.isEmpty()) {
            item {
                Text(
                    text = if (lang == "fa") "هیچ مشتری با نام و شماره تلفن ثبت نشده است." else "No customers with name and phone registered.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(qualifiedCustomers, key = { it.id }) { cust ->
                val levelName = cust.tier
                val matchedLevel = clubLevels.find { it.name == levelName } ?: clubLevels.firstOrNull()
                val totalEarned = matchedLevel?.freePlayHours ?: 0L
                val consumed = cust.rewardsConsumed
                val remaining = (totalEarned - consumed).coerceAtLeast(0L)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeHistoryCustomer = cust },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cust.fullName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    FilledTonalIconButton(
                                        onClick = { activeHistoryCustomer = cust },
                                        modifier = Modifier.size(28.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = if (lang == "fa") "تاریخچه امتیازات و فعالیت‌ها" else "Points & Activity History",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = cust.phoneNumber,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                                    Text(
                                        text = "${if (lang == "fa") "سطح" else "Tier"}: $levelName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%,d", cust.points)} ${if (lang == "fa") "امتیاز" else "Pts"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == "fa") "⚡ GN: ${cust.availableGn.toInt()} | طلب: %,d | بدهی: %,d تومان".format(Locale.US, cust.credit, cust.debt)
                                       else "⚡ GN: ${cust.availableGn.toInt()} | Credit: %,d | Debt: %,d T".format(Locale.US, cust.credit, cust.debt),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { activeHistoryCustomer = cust },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(if (lang == "fa") "پرونده و تراکنش" else "Profile & Tx", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    if (showManualPointDialog) {
        AddManualPointDialog(
            customers = customers,
            initialCustomerId = null,
            lang = lang,
            onDismiss = { showManualPointDialog = false },
            onSubmit = { custId, pts, title ->
                viewModel.addCustomerPointsWithLog(custId, pts, title)
            }
        )
    }

    if (activeHistoryCustomer != null) {
        CustomerHistoryModalDialog(
            customer = activeHistoryCustomer!!,
            viewModel = viewModel,
            lang = lang,
            onDismiss = { activeHistoryCustomer = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointHistoryReportCard(
    allLogs: List<PointLog>,
    customers: List<Customer>,
    selectedCustomerId: Long?,
    onSelectCustomerId: (Long?) -> Unit,
    selectedFilterType: String,
    onSelectFilterType: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenAddManualPoint: () -> Unit,
    lang: String = "fa"
) {
    val filteredLogs = remember(allLogs, selectedCustomerId, selectedFilterType, searchQuery, customers) {
        allLogs.filter { log ->
            val matchCust = selectedCustomerId == null || log.customerId == selectedCustomerId
            val matchType = when (selectedFilterType) {
                "POSITIVE" -> log.points > 0
                "NEGATIVE" -> log.points < 0
                else -> true
            }
            val custObj = customers.find { it.id == log.customerId }
            val custName = custObj?.fullName ?: ""
            val custPhone = custObj?.phoneNumber ?: ""
            val matchSearch = searchQuery.isBlank() ||
                    log.title.contains(searchQuery, ignoreCase = true) ||
                    custName.contains(searchQuery, ignoreCase = true) ||
                    custPhone.contains(searchQuery, ignoreCase = true)
            matchCust && matchType && matchSearch
        }
    }

    val totalEarned = filteredLogs.filter { it.points > 0 }.sumOf { it.points }
    val totalDeducted = filteredLogs.filter { it.points < 0 }.sumOf { it.points }
    val netBalance = totalEarned + totalDeducted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleExpand() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (lang == "fa") "📊 گزارش‌گیری و تاریخچه امتیازات (GN)" else "📊 GN Points History & Reports",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (lang == "fa") "ثبت و گزارش تمام تغییرات امتیاز به تفکیک مخاطب، نوع فعالیت و تاریخ" else "Detailed report of point transactions by member, activity, and date",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onOpenAddManualPoint,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (lang == "fa") "ثبت امتیاز" else "Add Points", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            if (expanded) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var custDropdownExpanded by remember { mutableStateOf(false) }
                    val selectedCustObj = customers.find { it.id == selectedCustomerId }
                    val selectedCustName = selectedCustObj?.fullName ?: (if (lang == "fa") "👥 همه مخاطبان" else "👥 All Members")

                    ExposedDropdownMenuBox(
                        expanded = custDropdownExpanded,
                        onExpandedChange = { custDropdownExpanded = !custDropdownExpanded },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = selectedCustName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(if (lang == "fa") "مخاطب" else "Customer", fontSize = 10.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdownExpanded) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = custDropdownExpanded,
                            onDismissRequest = { custDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (lang == "fa") "👥 همه مخاطبان (نمایش کامل)" else "👥 All Members (Full View)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    onSelectCustomerId(null)
                                    custDropdownExpanded = false
                                }
                            )
                            Divider()
                            customers.filter { it.fullName.isNotBlank() }.forEach { c ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(c.fullName, fontSize = 11.sp)
                                            Text("${String.format(Locale.US, "%,d", c.points)} GN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        onSelectCustomerId(c.id)
                                        custDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text(if (lang == "fa") "جستجو..." else "Search...", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedFilterType == "ALL",
                        onClick = { onSelectFilterType("ALL") },
                        label = { Text("${if (lang == "fa") "همه" else "All"} (${allLogs.size})", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(12.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilterType == "POSITIVE",
                        onClick = { onSelectFilterType("POSITIVE") },
                        label = { Text(if (lang == "fa") "افزایش (+)" else "Gain (+)", fontSize = 10.sp, color = Color(0xFF2E7D32)) },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp)) }
                    )
                    FilterChip(
                        selected = selectedFilterType == "NEGATIVE",
                        onClick = { onSelectFilterType("NEGATIVE") },
                        label = { Text(if (lang == "fa") "کاهش (-)" else "Loss (-)", fontSize = 10.sp, color = Color(0xFFC62828)) },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(12.dp)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "دریافتی (+)" else "Earned (+)", fontSize = 9.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("+${String.format(Locale.US, "%,d", totalEarned)} GN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "مصرفی (-)" else "Spent (-)", fontSize = 9.sp, color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%,d", totalDeducted)} GN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFB71C1C))
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "خالص / مانده" else "Net Balance", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text("${if (netBalance >= 0) "+" else ""}${String.format(Locale.US, "%,d", netBalance)} GN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "fa") "هیچ تاریخچه امتیازی با مشخصات انتخابی ثبت نشده است." else "No point history matches the selected filters.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredLogs.forEach { log ->
                            val customer = customers.find { it.id == log.customerId }
                            val isPositive = log.points >= 0
                            val formattedDate = com.example.util.JalaliCalendarHelper.formatJalaliDateTime(log.timestamp)

                            val icon = when {
                                log.title.contains("دعوت", ignoreCase = true) || log.title.contains("invite", ignoreCase = true) -> Icons.Default.GroupAdd
                                log.title.contains("کنسول", ignoreCase = true) || log.title.contains("بازی", ignoreCase = true) || log.title.contains("ایستگاه", ignoreCase = true) || log.title.contains("game", ignoreCase = true) -> Icons.Default.SportsEsports
                                log.title.contains("هدیه", ignoreCase = true) || log.title.contains("ثبت نام", ignoreCase = true) || log.title.contains("gift", ignoreCase = true) -> Icons.Default.CardGiftcard
                                log.title.contains("بوفه", ignoreCase = true) || log.title.contains("buffet", ignoreCase = true) -> Icons.Default.Fastfood
                                else -> Icons.Default.Stars
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha=0.2f),
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = customer?.fullName ?: "${if (lang == "fa") "مخاطب شناسه" else "Customer ID"} #${log.customerId}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (customer != null && customer.phoneNumber.isNotBlank()) {
                                                    Text(
                                                        text = "(${customer.phoneNumber})",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Text(
                                                text = log.title,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "⏱ $formattedDate",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha=0.2f),
                                        border = BorderStroke(1.dp, if (isPositive) Color(0xFFA5D6A7) else Color(0xFFEF9A9A))
                                    ) {
                                        Text(
                                            text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%,d", log.points)} GN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isPositive) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualPointDialog(
    customers: List<Customer>,
    initialCustomerId: Long?,
    lang: String = "fa",
    onDismiss: () -> Unit,
    onSubmit: (customerId: Long, points: Long, title: String) -> Unit
) {
    var selectedCustId by remember { mutableStateOf(initialCustomerId ?: customers.firstOrNull()?.id ?: 0L) }
    var pointsInput by remember { mutableStateOf("") }
    var isNegative by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf(if (lang == "fa") "افزایش دستی امتیاز" else "Manual Point Addition") }

    var custDropdownExpanded by remember { mutableStateOf(false) }

    val presetTitles = if (lang == "fa") listOf(
        "🎮 بازی با کنسول",
        "👥 دعوت از دوست",
        "🎁 هدیه/پاداش ویژه",
        "🏆 جایزه مسابقه",
        "🍔 تخفیف خرید بوفه",
        "⚡ تغییر دستی امتیاز"
    ) else listOf(
        "🎮 Console Gaming",
        "👥 Friend Referral",
        "🎁 Special Reward",
        "🏆 Tournament Prize",
        "🍔 Buffet Discount",
        "⚡ Manual Adjustment"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(if (lang == "fa") "ثبت و تخصیص امتیاز جدید (GN)" else "Grant New GN Points", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val selectedCust = customers.find { it.id == selectedCustId }
                ExposedDropdownMenuBox(
                    expanded = custDropdownExpanded,
                    onExpandedChange = { custDropdownExpanded = !custDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCust?.fullName ?: (if (lang == "fa") "انتخاب مخاطب..." else "Select Customer..."),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (lang == "fa") "مخاطب دریافت‌کننده امتیاز" else "Receiving Member", fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = custDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = custDropdownExpanded,
                        onDismissRequest = { custDropdownExpanded = false }
                    ) {
                        customers.filter { it.fullName.isNotBlank() }.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.fullName} (${c.phoneNumber}) - ${String.format(Locale.US, "%,d", c.points)} GN", fontSize = 11.sp) },
                                onClick = {
                                    selectedCustId = c.id
                                    custDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isNegative,
                        onClick = { isNegative = false },
                        label = { Text(if (lang == "fa") "➕ افزایش امتیاز (+GN)" else "➕ Add Points (+GN)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isNegative,
                        onClick = { isNegative = true },
                        label = { Text(if (lang == "fa") "➖ کسر امتیاز (-GN)" else "➖ Deduct Points (-GN)", fontSize = 10.sp, color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = pointsInput,
                    onValueChange = { pointsInput = it },
                    label = { Text(if (lang == "fa") "مقدار امتیاز GN" else "GN Points Amount", fontSize = 11.sp) },
                    placeholder = { Text(if (lang == "fa") "مثلاً 50" else "e.g. 50", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Text(if (lang == "fa") "نوع فعالیت / علت تغییر:" else "Activity / Reason:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetTitles.take(3).forEach { preset ->
                        OutlinedButton(
                            onClick = { titleInput = preset },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(preset, fontSize = 9.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetTitles.drop(3).take(3).forEach { preset ->
                        OutlinedButton(
                            onClick = { titleInput = preset },
                            modifier = Modifier.height(26.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(preset, fontSize = 9.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(if (lang == "fa") "عنوان دقیق فعالیت" else "Exact Activity Title", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rawPts = pointsInput.toLongOrNull() ?: 0L
                    if (rawPts > 0 && selectedCustId != 0L) {
                        val finalPts = if (isNegative) -rawPts else rawPts
                        val defaultTitle = if (lang == "fa") {
                            if (isNegative) "کاهش دستی امتیاز" else "افزایش دستی امتیاز"
                        } else {
                            if (isNegative) "Manual Point Deduction" else "Manual Point Addition"
                        }
                        onSubmit(selectedCustId, finalPts, titleInput.ifBlank { defaultTitle })
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (lang == "fa") "ثبت و اعمال" else "Apply & Save", fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "fa") "انصراف" else "Cancel", fontSize = 11.sp)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHistoryModalDialog(
    customer: Customer,
    viewModel: GameNetViewModel,
    lang: String = "fa",
    onDismiss: () -> Unit
) {
    val pointLogs by viewModel.getPointLogs(customer.id).collectAsState(initial = emptyList())
    var ledgerEntries by remember { mutableStateOf<List<com.example.data.GnLedgerEntry>>(emptyList()) }
    LaunchedEffect(customer.id) {
        ledgerEntries = SelfHostedManager.fetchGnLedgerForCustomer(customer.id)
    }

    var showGnHistory by remember { mutableStateOf(true) }
    var pointsInput by remember { mutableStateOf("") }
    var isNegative by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf(if (lang == "fa") "تغییر دستی" else "Manual Adjustment") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (lang == "fa") "📜 پرونده مخاطب: " else "📜 Member File: "}${customer.fullName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                        Text(customer.tier, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                    }
                }
                Text(
                    text = "${if (lang == "fa") "شماره: " else "Phone: "}${customer.phoneNumber}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "در دسترس (GN)" else "Available (GN)", fontSize = 9.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%,d", customer.availableGn)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "در انتظار (PENDING)" else "Pending (GN)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%,d", customer.pendingGn)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (lang == "fa") "وفاداری (LP)" else "Loyalty (LP)", fontSize = 9.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                            Text("${String.format(Locale.US, "%,d", customer.lp)}", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1565C0))
                        }
                    }
                }

                val absenceInfo = remember(customer) { viewModel.getAbsenceStatusForCustomer(customer) }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (absenceInfo.isPenaltyActive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(0.5.dp, if (absenceInfo.isPenaltyActive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val absenceStatusTitle = if (lang == "fa") {
                                "وضعیت حضور: ${absenceInfo.statusText} (${absenceInfo.absentDays} روز بدون فعالیت)"
                            } else {
                                "Presence: ${absenceInfo.statusText} (${absenceInfo.absentDays} days inactive)"
                            }
                            Text(
                                text = absenceStatusTitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (absenceInfo.isPenaltyActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (absenceInfo.isPenaltyActive) {
                                val penaltyTitle = if (lang == "fa") {
                                    "جریمه اعمال‌شده: ${absenceInfo.totalGnPenalized.toInt()} GN و ${absenceInfo.totalLpPenalized.toInt()} LP"
                                } else {
                                    "Applied penalty: ${absenceInfo.totalGnPenalized.toInt()} GN & ${absenceInfo.totalLpPenalized.toInt()} LP"
                                }
                                Text(
                                    text = penaltyTitle,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (lang == "fa") "⚡ ثبت تراکنش دستی GN:" else "⚡ Manual GN Transaction:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = pointsInput,
                                onValueChange = { pointsInput = it },
                                label = { Text(if (lang == "fa") "مقدار GN" else "GN Amount", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp)
                            )
                            OutlinedTextField(
                                value = titleInput,
                                onValueChange = { titleInput = it },
                                label = { Text(if (lang == "fa") "علت" else "Reason", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = !isNegative,
                                    onClick = { isNegative = false },
                                    label = { Text(if (lang == "fa") "+افزایش" else "+Add", fontSize = 9.sp) }
                                )
                                FilterChip(
                                    selected = isNegative,
                                    onClick = { isNegative = true },
                                    label = { Text(if (lang == "fa") "-کاهش" else "-Deduct", fontSize = 9.sp) }
                                )
                            }
                            Button(
                                onClick = {
                                    val pts = pointsInput.toLongOrNull() ?: 0L
                                    if (pts > 0) {
                                        val finalPts = if (isNegative) -pts else pts
                                        val fallback = if (lang == "fa") {
                                            if (isNegative) "کاهش دستی" else "افزایش دستی"
                                        } else {
                                            if (isNegative) "Manual Deduct" else "Manual Add"
                                        }
                                        viewModel.manualAdjustCustomerGn(customer.id, finalPts, titleInput.ifBlank { fallback })
                                        pointsInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(if (lang == "fa") "ثبت" else "Apply", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showGnHistory = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (showGnHistory) MaterialTheme.colorScheme.primary else Color.Gray)
                    ) {
                        Text(if (lang == "fa") "مالی و پاداش (GN)" else "Financial & Rewards (GN)", fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showGnHistory = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (!showGnHistory) MaterialTheme.colorScheme.primary else Color.Gray)
                    ) {
                        Text(if (lang == "fa") "انضباطی (Point)" else "Disciplinary (Point)", fontSize = 11.sp)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showGnHistory) {
                        if (ledgerEntries.isEmpty()) {
                            item { Text(if (lang == "fa") "تراکنش GN ثبت نشده است." else "No GN transactions recorded.", fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                        } else {
                            items(ledgerEntries) { entry ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(entry.description.ifBlank { entry.transactionType }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                if (entry.status == "PENDING") {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Badge(containerColor = Color(0xFFFEF3C7)) { Text("PENDING", color = Color(0xFFD97706), fontSize = 8.sp) }
                                                }
                                            }
                                            Text(java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.timestamp)), fontSize = 9.sp, color = Color.Gray)
                                        }
                                        Text(
                                            text = "${if(entry.gnAmount>0)"+"else""}${entry.gnAmount.toInt()} GN",
                                            color = if (entry.gnAmount > 0) Color(0xFF00C853) else Color(0xFFD32F2F),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        if (pointLogs.isEmpty()) {
                            item { Text(if (lang == "fa") "هیچ سابقه انضباطی ثبت نشده است." else "No disciplinary records found.", fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                        } else {
                            items(pointLogs) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(log.title.ifBlank { if (lang == "fa") "تغییر دستی" else "Manual Change" }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(log.timestamp)), fontSize = 9.sp, color = Color.Gray)
                                        }
                                        Text(
                                            text = "${if(log.points>0)"+"else""}${log.points.toInt()} Point",
                                            color = if (log.points > 0) Color(0xFF00C853) else Color(0xFFD32F2F),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "fa") "بستن" else "Close")
            }
        }
    )
}

// -------------------------------------------------------------
// LP RULES SUB-SCREEN
// -------------------------------------------------------------
@Composable
fun LpRulesSubScreen(viewModel: GameNetViewModel, lang: String) {
    val lpTomanRate by viewModel.lpTomanRate.collectAsState()
    val buffetLpPer10000 by viewModel.buffetLpPer10000.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val behaviorRules by viewModel.allBehaviorRules.collectAsState()

    var lpRateInput by remember(lpTomanRate) { mutableStateOf(lpTomanRate.toInt().toString()) }
    var buffetLpInput by remember(buffetLpPer10000) { mutableStateOf(buffetLpPer10000.toInt().toString()) }
    var showAddLpRuleDialog by remember { mutableStateOf(false) }
    var editingLpRule by remember { mutableStateOf<BehaviorRule?>(null) }
    
    // Manual LP Adjustment State
    var selectedCustForLp by remember { mutableStateOf<Customer?>(null) }
    var custSearchQuery by remember { mutableStateOf("") }
    var manualLpAmountInput by remember { mutableStateOf("") }
    var manualLpReasonInput by remember { mutableStateOf("") }
    var isDeductLp by remember { mutableStateOf(false) }
    var lpActionMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD 1: Base LP Conversion Rate Setting
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lang == "fa") "تنظیم نرخ پایه اعطای امتیاز LP" else "Base LP Grant Rate Setting",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = if (lang == "fa") "نرخ LP بازی را تعیین کنید؛ نرخ LP بوفه در فیلد جداگانه قابل تنظیم است:"
                    else "Set the game LP grant rate; buffet LP has a separate configurable rate:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = lpRateInput,
                        onValueChange = { lpRateInput = it },
                        label = { Text(if (lang == "fa") "مبلغ به تومان (به ازای 1 LP)" else "Amount in Tomans (per 1 LP)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            val rate = lpRateInput.toLongOrNull() ?: 1000L
                            viewModel.saveLpTomanRate(rate)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "ذخیره نرخ LP" else "Save LP Rate", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = buffetLpInput,
                        onValueChange = { buffetLpInput = it },
                        label = { Text(if (lang == "fa") "LP بوفه (به ازای 10,000 تومان)" else "Buffet LP (per 10k Toman)", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = {
                            val rate = buffetLpInput.toLongOrNull()?.coerceAtLeast(0L) ?: 5L
                            viewModel.saveBuffetLpPer10000(rate)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "ذخیره LP بوفه" else "Save Buffet LP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Live Preview calculation
                val currentRateNum = lpRateInput.toLongOrNull() ?: 1000L
                val sampleLpFor10k = if (currentRateNum > 0) (10000L / currentRateNum) else 10L
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha=0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val previewText = if (lang == "fa") {
                            "💡 پیش‌نمایش: با این نرخ، به ازای هر 10,000 تومان هزینه در گیم‌نت ⬅️ ${DecimalFormat("#.#").format(sampleLpFor10k)} LP تعلق می‌گیرد."
                        } else {
                            "💡 Preview: With this rate, per 10,000 Tomans spent ⬅️ ${DecimalFormat("#.#").format(sampleLpFor10k)} LP will be granted."
                        }
                        Text(
                            text = previewText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // CARD 2: LP Behavioral & Reward Rules Overview
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "قوانین تشویقی و تنبیهی اعطا/کسر LP" else "LP Reward & Penalty Rules",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showAddLpRuleDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "تعریف قانون جدید" else "New Rule", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (behaviorRules.isEmpty()) {
                    Text(if (lang == "fa") "هیچ قانون انضباطی یا تشویقی ثبت نشده است." else "No behavior or reward rules defined.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    behaviorRules.forEach { rule ->
                        val isPositive = rule.lpChange >= 0
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha=0.2f),
                            border = BorderStroke(1.dp, if (isPositive) Color(0xFF4CAF50).copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha=0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (rule.description.isNotBlank()) {
                                        Text(rule.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPositive) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                                    ) {
                                        Text(
                                            text = if (isPositive) "+${rule.lpChange.toInt()} LP" else "${rule.lpChange.toInt()} LP",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingLpRule = rule },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = if (lang == "fa") "ویرایش قانون" else "Edit Rule",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteBehaviorRule(rule) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = if (lang == "fa") "حذف قانون" else "Delete Rule",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // CARD 3: Manual LP Adjustment Tool
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (lang == "fa") "اعطا یا کسر دستی امتیاز LP به مشتری" else "Manual LP Award / Deduction to Member",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Customer Selection Input
                OutlinedTextField(
                    value = custSearchQuery,
                    onValueChange = { 
                        custSearchQuery = it
                        selectedCustForLp = null
                    },
                    label = { Text(if (lang == "fa") "جستجوی نام یا شماره مشتری" else "Search member name or phone", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                if (custSearchQuery.isNotBlank() && selectedCustForLp == null) {
                    val filteredCusts = customers.filter { 
                        it.fullName.contains(custSearchQuery, ignoreCase = true) || 
                        it.phoneNumber.contains(custSearchQuery) 
                    }.take(4)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredCusts.forEach { c ->
                            Surface(
                                onClick = {
                                    selectedCustForLp = c
                                    custSearchQuery = "${c.fullName} (${c.phoneNumber})"
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${c.fullName} - ${c.phoneNumber}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("${if (lang == "fa") "موجودی LP: " else "LP Balance: "}${c.lp.toInt()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                if (selectedCustForLp != null) {
                    val c = selectedCustForLp!!
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${if (lang == "fa") "انتخاب شده: " else "Selected: "}${c.fullName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${if (lang == "fa") "فعلی: " else "Current: "}${c.lp.toInt()} LP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualLpAmountInput,
                            onValueChange = { manualLpAmountInput = it },
                            label = { Text(if (lang == "fa") "مقدار LP" else "LP Amount", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = manualLpReasonInput,
                            onValueChange = { manualLpReasonInput = it },
                            label = { Text(if (lang == "fa") "علت یا بابت" else "Reason / Details", fontSize = 10.sp) },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !isDeductLp,
                                onClick = { isDeductLp = false },
                                label = { Text(if (lang == "fa") "افزایش LP (+)" else "Add LP (+)", fontSize = 10.sp) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            FilterChip(
                                selected = isDeductLp,
                                onClick = { isDeductLp = true },
                                label = { Text(if (lang == "fa") "کسر LP (-)" else "Deduct LP (-)", fontSize = 10.sp) }
                            )
                        }

                        Button(
                            onClick = {
                                val amt = manualLpAmountInput.toLongOrNull() ?: 0L
                                if (amt > 0L && selectedCustForLp != null) {
                                    val delta = if (isDeductLp) -amt else amt
                                    val fallbackReason = if (lang == "fa") "تغییر دستی مدیریت LP" else "Manual LP Admin Adjustment"
                                    val reason = if (manualLpReasonInput.isNotBlank()) manualLpReasonInput else fallbackReason
                                    viewModel.updateCustomerLp(selectedCustForLp!!.id, delta, reason)
                                    lpActionMessage = if (lang == "fa") {
                                        "موفقیت: مقدار ${delta.toInt()} LP به کاربر ${selectedCustForLp!!.fullName} اعمال شد."
                                    } else {
                                        "Success: ${delta.toInt()} LP applied to member ${selectedCustForLp!!.fullName}."
                                    }
                                    manualLpAmountInput = ""
                                    manualLpReasonInput = ""
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (lang == "fa") "ثبت تراکنش LP" else "Apply LP Transaction", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (lpActionMessage != null) {
                        Text(
                            text = lpActionMessage!!,
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAddLpRuleDialog) {
        var ruleTitle by remember { mutableStateOf("") }
        var ruleDesc by remember { mutableStateOf("") }
        var ruleLpChange by remember { mutableStateOf("10") }
        var isDeductRule by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddLpRuleDialog = false },
            title = { Text(if (lang == "fa") "تعریف قانون تشویقی/تنبیهی LP جدید" else "New LP Reward/Penalty Rule", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ruleTitle,
                        onValueChange = { ruleTitle = it },
                        label = { Text(if (lang == "fa") "عنوان قانون (مثلا: پاداش همکاری / جریمه تأخیر)" else "Rule Title (e.g. Clean station bonus)", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = ruleDesc,
                        onValueChange = { ruleDesc = it },
                        label = { Text(if (lang == "fa") "توضیحات اختیاری" else "Optional Description", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ruleLpChange,
                            onValueChange = { ruleLpChange = it },
                            label = { Text(if (lang == "fa") "مقدار LP" else "LP Amount", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !isDeductRule,
                                onClick = { isDeductRule = false },
                                label = { Text(if (lang == "fa") "تشویقی (+)" else "Reward (+)", fontSize = 10.sp) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = isDeductRule,
                                onClick = { isDeductRule = true },
                                label = { Text(if (lang == "fa") "تنبیهی (-)" else "Penalty (-)", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleTitle.isNotBlank()) {
                            val lpVal = ruleLpChange.toLongOrNull() ?: 0L
                            val finalLp = if (isDeductRule) -lpVal else lpVal
                            viewModel.addOrUpdateBehaviorRule(
                                com.example.data.BehaviorRule(
                                    title = ruleTitle,
                                    description = ruleDesc,
                                    gnChange = 0L,
                                    lpChange = finalLp,
                                    severity = if (isDeductRule) "HIGH" else "LOW"
                                )
                            )
                            showAddLpRuleDialog = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (lang == "fa") "ثبت قانون LP" else "Save LP Rule", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddLpRuleDialog = false }) { Text(if (lang == "fa") "انصراف" else "Cancel", fontSize = 11.sp) }
            }
        )
    }

    editingLpRule?.let { ruleToEdit ->
        var ruleTitle by remember(ruleToEdit) { mutableStateOf(ruleToEdit.title) }
        var ruleDesc by remember(ruleToEdit) { mutableStateOf(ruleToEdit.description) }
        var ruleLpChange by remember(ruleToEdit) { mutableStateOf(kotlin.math.abs(ruleToEdit.lpChange).toInt().toString()) }
        var isDeductRule by remember(ruleToEdit) { mutableStateOf(ruleToEdit.lpChange < 0) }

        AlertDialog(
            onDismissRequest = { editingLpRule = null },
            title = { Text(if (lang == "fa") "ویرایش قانون تشویقی/تنبیهی LP" else "Edit LP Rule", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ruleTitle,
                        onValueChange = { ruleTitle = it },
                        label = { Text(if (lang == "fa") "عنوان قانون" else "Rule Title", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedTextField(
                        value = ruleDesc,
                        onValueChange = { ruleDesc = it },
                        label = { Text(if (lang == "fa") "توضیحات اختیاری" else "Optional Description", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = ruleLpChange,
                            onValueChange = { ruleLpChange = it },
                            label = { Text(if (lang == "fa") "مقدار LP" else "LP Amount", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !isDeductRule,
                                onClick = { isDeductRule = false },
                                label = { Text(if (lang == "fa") "تشویقی (+)" else "Reward (+)", fontSize = 10.sp) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = isDeductRule,
                                onClick = { isDeductRule = true },
                                label = { Text(if (lang == "fa") "تنبیهی (-)" else "Penalty (-)", fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleTitle.isNotBlank()) {
                            val lpVal = ruleLpChange.toLongOrNull() ?: 0L
                            val finalLp = if (isDeductRule) -lpVal else lpVal
                            viewModel.addOrUpdateBehaviorRule(
                                ruleToEdit.copy(
                                    title = ruleTitle,
                                    description = ruleDesc,
                                    lpChange = finalLp,
                                    severity = if (isDeductRule) "HIGH" else "LOW"
                                )
                            )
                            editingLpRule = null
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(if (lang == "fa") "ذخیره تغییرات" else "Save Changes", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editingLpRule = null }) { Text(if (lang == "fa") "انصراف" else "Cancel", fontSize = 11.sp) }
            }
        )
    }
}
