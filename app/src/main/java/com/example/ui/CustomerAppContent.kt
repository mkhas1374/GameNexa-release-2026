package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Customer
import com.example.data.GnLedgerEntry
import com.example.data.StationOrder
import com.example.data.StationState
import com.example.data.network.SelfHostedManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDashboardTab(
    viewModel: GameNetViewModel,
    onNavigateTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.language.collectAsState()
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsState()
    val allCloudCustomers by SelfHostedManager.allCloudCustomers.collectAsState()
    val stationStates by viewModel.stationStates.collectAsState()
    val cloudLiveStations by SelfHostedManager.allLiveStations.collectAsState()
    val ownerBroadcastAnnouncement by viewModel.ownerBroadcastAnnouncement.collectAsState()
    val ownerBroadcastMessage by viewModel.ownerBroadcastMessage.collectAsState()

    val clubLevels by viewModel.clubLevels.collectAsState()
    val lpTomanRate by viewModel.lpTomanRate.collectAsState()
    var showProgressDetailsDialog by remember { mutableStateOf(false) }

    val currentCustomerAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()
    val matchedCustomer = remember(customers, allCloudCustomers, currentCustomerAuth) {
        val phone = currentCustomerAuth?.phoneNumber?.trim() ?: ""
        val authId = currentCustomerAuth?.id ?: 0L
        val cloudCust = allCloudCustomers.find { 
            (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) 
        }
        if (cloudCust != null) {
            cloudCust
        } else {
            customers.find { (phone.isNotBlank() && it.phoneNumber.trim() == phone) || (authId > 0 && it.id == authId) }
                ?: currentCustomerAuth
                ?: Customer(fullName = "مشتری عزیز", phoneNumber = "", availableGn = 0L)
        }
    }

    val customer = matchedCustomer

    // Find active station for this customer from local state or cloud live stations
    val activeStation = remember(stationStates, cloudLiveStations, customer, currentCustomerAuth) {
        val targetName = customer.fullName.trim()
        val targetPhone = customer.phoneNumber.trim()
        val targetId = customer.id
        val authId = currentCustomerAuth?.id ?: 0L
        val authPhone = currentCustomerAuth?.phoneNumber?.trim() ?: ""

        val localMatch = stationStates.find { station ->
            (station.status == "RUNNING" || station.status == "PAUSED") && (
                (targetName.isNotBlank() && station.selectedCustomerNamesStr.split(",").any { name ->
                    val clean = name.trim()
                    clean.isNotBlank() && (clean.equals(targetName, ignoreCase = true) || targetName.contains(clean, ignoreCase = true) || clean.contains(targetName, ignoreCase = true))
                }) ||
                (targetPhone.isNotBlank() && (station.selectedCustomerNamesStr.contains(targetPhone) || station.selectedCustomerIdsStr.contains(targetPhone))) ||
                (authPhone.isNotBlank() && (station.selectedCustomerNamesStr.contains(authPhone) || station.selectedCustomerIdsStr.contains(authPhone))) ||
                (targetId > 0 && (station.getCustomerIds().contains(targetId) || station.selectedCustomerIdsStr.contains(targetId.toString()))) ||
                (authId > 0 && (station.getCustomerIds().contains(authId) || station.selectedCustomerIdsStr.contains(authId.toString())))
            )
        }
        if (localMatch != null) return@remember localMatch

        val cloudMatch = cloudLiveStations.find { st ->
            (st.status == "RUNNING" || st.status == "PAUSED") && (
                (targetName.isNotBlank() && st.customerNamesStr.split(",").any { name ->
                    val clean = name.trim()
                    clean.isNotBlank() && (clean.equals(targetName, ignoreCase = true) || targetName.contains(clean, ignoreCase = true) || clean.contains(targetName, ignoreCase = true))
                }) ||
                (targetPhone.isNotBlank() && (st.customerNamesStr.contains(targetPhone) || st.customerIdsStr.contains(targetPhone))) ||
                (authPhone.isNotBlank() && (st.customerNamesStr.contains(authPhone) || st.customerIdsStr.contains(authPhone))) ||
                (targetId > 0 && st.customerIdsStr.isNotBlank() && st.customerIdsStr.split(",").map { it.trim() }.contains(targetId.toString())) ||
                (authId > 0 && st.customerIdsStr.isNotBlank() && st.customerIdsStr.split(",").map { it.trim() }.contains(authId.toString()))
            )
        }
        if (cloudMatch != null) {
            StationState(
                id = cloudMatch.stationId,
                status = cloudMatch.status,
                consoleType = cloudMatch.consoleType,
                controllerCount = cloudMatch.controllerCount,
                startTimeMillis = cloudMatch.startTimeMillis,
                lastStateChangeTimeMillis = cloudMatch.lastStateChangeMillis,
                elapsedPlayingTimeMillis = cloudMatch.elapsedPlayingTimeMillis,
                prepaymentAmount = cloudMatch.prepaymentAmount,
                durationLimitMinutes = cloudMatch.durationLimitMinutes,
                selectedCustomerIdsStr = cloudMatch.customerIdsStr,
                selectedCustomerNamesStr = cloudMatch.customerNamesStr
            )
        } else null
    }

    // Real-time background sync loop while customer is in app
    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                SelfHostedManager.fetchLiveStationsFromCloud()
                // Refresh the authenticated customer with the server token; never re-login with a locally stored password.
                SelfHostedManager.refreshCurrentCustomerProfile()
            } catch (_: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in CustomerAppContent.kt", _) }
            delay(3000L) // Polling every 3s for Real-Time Experience
        }
    }

    val numberFormat = remember { DecimalFormat("#,###") }
    val customerLedger by produceState<List<GnLedgerEntry>>(initialValue = emptyList(), key1 = customer.id) {
        if (customer.id > 0) {
            value = SelfHostedManager.fetchGnLedgerForCustomer(customer.id)
        }
    }

    val currentLp = maxOf(customer.points, customer.lp)
    val sortedLevels = remember(clubLevels) { clubLevels.sortedBy { it.requiredPoints } }

    val currentLevelIndex = remember(sortedLevels, currentLp, customer.tier) {
        var idx = sortedLevels.indexOfLast { currentLp >= it.requiredPoints }
        if (idx < 0) {
            idx = sortedLevels.indexOfFirst {
                it.id.equals(customer.tier, ignoreCase = true) ||
                it.name.equals(customer.tier, ignoreCase = true)
            }.coerceAtLeast(0)
        }
        idx.coerceAtLeast(0)
    }

    val currentClubLevel = sortedLevels.getOrNull(currentLevelIndex) ?: sortedLevels.firstOrNull() ?: ClubLevel("bronze", "برنزی", 0L)
    val nextClubLevel = sortedLevels.getOrNull(currentLevelIndex + 1)

    val targetLp = nextClubLevel?.requiredPoints ?: currentClubLevel.requiredPoints
    val levelStartLp = currentClubLevel.requiredPoints
    val neededLp = if (nextClubLevel != null) (nextClubLevel.requiredPoints - currentLp).coerceAtLeast(0L) else 0L
    val effectiveLpRate = if (lpTomanRate > 0L) lpTomanRate else 1000L
    val neededToman = neededLp * effectiveLpRate

    val progressFraction = if (nextClubLevel != null && targetLp > levelStartLp) {
        ((currentLp - levelStartLp) / (targetLp - levelStartLp)).toFloat().coerceIn(0f, 1f)
    } else {
        1f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -------------------------------------------------------------
        // SECTION 1: GN & LP BALANCE HERO CARD
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "موجودی اعتبار و امتیاز وفاداری",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = customer.fullName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (customer.tier) {
                            "DIAMOND" -> Color(0xFF673AB7)
                            "GOLD" -> Color(0xFFFFC107)
                            "SILVER" -> Color(0xFF9E9E9E)
                            else -> Color(0xFF795548)
                        }
                    ) {
                        Text(
                            text = "سطح ${customer.tier}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // GN Balance
                    Column {
                        Text(
                            text = "اعتبار در دسترس GN:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${numberFormat.format(customer.availableGn)} GN",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (customer.pendingGn > 0) {
                            Text(
                                text = "+ ${numberFormat.format(customer.pendingGn)} PENDING 🟡",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }

                    // LP Points
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "امتیاز باشگاه (LP):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${numberFormat.format(customer.points)} LP",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "قابل تبدیل به پاداش",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (customer.debt > 0 || customer.credit > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (customer.debt > 0) {
                            Column {
                                Text("بدهکاری تومانی:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(customer.debt)} تومان", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (customer.credit > 0) {
                            Column(horizontalAlignment = if (customer.debt > 0) Alignment.End else Alignment.Start) {
                                Text("موجودی کیف پول تومانی:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(customer.credit)} تومان", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }

                // Quick Action Buttons Row inside Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNavigateTab(1) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("شارژ حساب", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onNavigateTab(3) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "رزرو جایگاه" else "Booking", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 1.5: LOYALTY LEVEL PROGRESS CHART & CARD
        // -------------------------------------------------------------
        val levelBadgeColor = when (nextClubLevel?.name?.lowercase() ?: currentClubLevel.name.lowercase()) {
            "bronze", "برنزی" -> Color(0xFFCD7F32)
            "silver", "نقره‌ای" -> Color(0xFF757575)
            "gold", "طلایی" -> Color(0xFFFFB300)
            "diamond", "الماسی", "vip" -> Color(0xFF00ACC1)
            else -> MaterialTheme.colorScheme.primary
        }

        val curBadgeColor = when (currentClubLevel.name.lowercase()) {
            "bronze", "برنزی" -> Color(0xFFCD7F32)
            "silver", "نقره‌ای" -> Color(0xFF757575)
            "gold", "طلایی" -> Color(0xFFFFB300)
            "diamond", "الماسی", "vip" -> Color(0xFF00ACC1)
            else -> MaterialTheme.colorScheme.primary
        }

        Card(
            onClick = { showProgressDetailsDialog = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, levelBadgeColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = levelBadgeColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "مسیر پیشرفت و ارتقای سطح",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = curBadgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, curBadgeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "سطح شما: ${currentClubLevel.name}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = curBadgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پیشرفت: ${(progressFraction * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (nextClubLevel != null) {
                            Text(
                                text = "هدف: سطح ${nextClubLevel.name} (${numberFormat.format(nextClubLevel.requiredPoints)} LP)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = levelBadgeColor
                            )
                        } else {
                            Text(
                                text = "🏆 بالاترین سطح وفاداری (الماسی)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00ACC1)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = levelBadgeColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (nextClubLevel != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "فقط ${numberFormat.format(neededToman)} تومان بازی تا رسیدن به سطح ${nextClubLevel.name}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                text = "🎉 شما عضوی درخشان در بالاترین سطح باشگاه (الماسی) هستید!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "جزئیات 📊",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 2: ACTIVE STATION REAL-TIME COUNTER
        // -------------------------------------------------------------
        if (activeStation != null) {
            ActiveStationLiveCard(station = activeStation, viewModel = viewModel)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "هیچ نشست فعالی در حال حاضر برای شما ثبت نشده است",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "پس از شروع بازی توسط مدیریت، تایمر و هزینه لحظه‌ای اینجا فعال می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Show Last Finished Session details if exists
                val lastSession by androidx.compose.runtime.produceState<com.example.data.CustomerTransaction?>(initialValue = null, key1 = customer.id) {
                    if (customer.id > 0) {
                        try {
                            val txs = com.example.data.network.SelfHostedManager.fetchCustomerTransactionsForCustomer(customer.id)
                            value = txs.maxByOrNull { if (it.id > 0) it.id else it.timestamp }
                        } catch (e: Exception) { android.util.Log.e("GameNexa", "Suppressed exception in CustomerAppContent.kt", e) }
                    }
                }
                if (lastSession != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
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
                                Text(
                                    text = "🏁 آخرین نشست بازی ثبت شده شما:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSession!!.timestamp)),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🎮 کنسول و جایگاه:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(lastSession!!.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🕹️ هزینه بازی:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(lastSession!!.gameCost)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (lastSession!!.foodCost > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("🍿 هزینه بوفه:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${numberFormat.format(lastSession!!.foodCost)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("💰 کل هزینه نشست:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${numberFormat.format(lastSession!!.amount)} تومان", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 3: OWNER BROADCAST ANNOUNCEMENT BANNER
        // -------------------------------------------------------------
        val msgToDisplay = (ownerBroadcastAnnouncement?.message?.takeIf { it.isNotBlank() }) ?: ownerBroadcastMessage
        if (msgToDisplay.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1B4B),
                border = BorderStroke(1.5.dp, Color(0xFF6366F1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(28.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "📢 پیام مدیریت مجموعه GameNexa",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFFA5B4FC)
                        )
                        Text(
                            text = msgToDisplay,
                            fontSize = 12.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 4: QUICK ACTION SHORTCUTS GRID
        // -------------------------------------------------------------
        Text(
            text = "⚡ دسترسی‌های سریع:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { onNavigateTab(1) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                    Text("خرید GN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { onNavigateTab(1) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                    Text("انتقال GN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { onNavigateTab(2) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Text("فاکتورهای من", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Surface(
                onClick = { onNavigateTab(4) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Fastfood, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(24.dp))
                    Text("کافه و بوفه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // -------------------------------------------------------------
        // SECTION 5: REFERRAL & FRIEND INVITATION CARD
        // -------------------------------------------------------------
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "دعوت از دوستان و دریافت GN هدیه",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "کد معرف اختصاصی خود را به دوستانتان معرفی کنید تا با اولین بازی آن‌ها، اعتبار هدیه GN به حساب شما منظور گردد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("کد معرف شما:", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = customer.inviteCode.ifBlank { "GN-${customer.phoneNumber.takeLast(4)}" },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText(
                                    "کد معرف GameNexa",
                                    customer.inviteCode.ifBlank { "GN-${customer.phoneNumber.takeLast(4)}" }
                                )
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "کد معرف کپی شد!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("کپی کد", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showProgressDetailsDialog) {
        CustomerLoyaltyProgressDialog(
            customer = customer,
            currentLevel = currentClubLevel,
            nextLevel = nextClubLevel,
            currentLp = currentLp,
            neededLp = neededLp,
            neededToman = neededToman,
            progressFraction = progressFraction,
            lpTomanRate = effectiveLpRate,
            customerLedger = customerLedger,
            onDismiss = { showProgressDetailsDialog = false }
        )
    }
}

@Composable
fun CustomerLoyaltyProgressDialog(
    customer: Customer,
    currentLevel: ClubLevel,
    nextLevel: ClubLevel?,
    currentLp: Long,
    neededLp: Long,
    neededToman: Long,
    progressFraction: Float,
    lpTomanRate: Long,
    customerLedger: List<GnLedgerEntry>,
    onDismiss: () -> Unit
) {
    val numberFormat = remember { DecimalFormat("#,###") }

    val currentBadgeColor = when (currentLevel.name.lowercase()) {
        "bronze", "برنزی" -> Color(0xFFCD7F32)
        "silver", "نقره‌ای" -> Color(0xFF757575)
        "gold", "طلایی" -> Color(0xFFFFB300)
        "diamond", "الماسی", "vip" -> Color(0xFF00ACC1)
        else -> MaterialTheme.colorScheme.primary
    }

    val nextBadgeColor = when (nextLevel?.name?.lowercase()) {
        "bronze", "برنزی" -> Color(0xFFCD7F32)
        "silver", "نقره‌ای" -> Color(0xFF757575)
        "gold", "طلایی" -> Color(0xFFFFB300)
        "diamond", "الماسی", "vip" -> Color(0xFF00ACC1)
        else -> Color(0xFF00ACC1)
    }

    val daysSinceReview = remember(customer.lastTierReviewTimestamp) {
        if (customer.lastTierReviewTimestamp > 0) {
            ((System.currentTimeMillis() - customer.lastTierReviewTimestamp) / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } else 0
    }
    val graceDays = currentLevel.graceDays
    val remainingDaysInCycle = (graceDays - daysSinceReview).coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = currentBadgeColor,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text("کارنامه و مسیر ارتقای سطح وفاداری", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("گیم‌نت GameNexa - باشگاه مشتریان", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Progress visual card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = currentBadgeColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, currentBadgeColor.copy(alpha = 0.3f))
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
                            Text("سطح فعلی: ${currentLevel.name}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = currentBadgeColor)
                            if (nextLevel != null) {
                                Text("سطح هدف: ${nextLevel.name}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = nextBadgeColor)
                            } else {
                                Text("بالاترین سطح 👑", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF00ACC1))
                            }
                        }

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(CircleShape),
                            color = nextBadgeColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "پیشرفت پیشروی: ${(progressFraction * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                // Section 2: Detailed LP & Toman breakdown
                Text("📊 آمار و وضعیت امتیازات LP:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("مقدار LP فعلی شما:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(currentLp)} LP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }

                        if (nextLevel != null) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("LP مورد نیاز برای سطح ${nextLevel.name}:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(nextLevel.requiredPoints)} LP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("مانده LP تا ارتقا:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${numberFormat.format(neededLp)} LP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFD97706))
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🎮 کارکرد بازی/بوفه مورد نیاز:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${numberFormat.format(neededToman)} تومان", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                            }
                            Text(
                                text = "* بر اساس محاسبه 1 LP به ازای هر ${numberFormat.format(lpTomanRate)} تومان پرداخت",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Section 3: Days remaining / Grace period
                Text("⏳ مهلت و فرجه ماندن در سطح:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("روزهای مانده تا پایان فرجه دوره:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$remainingDaysInCycle روز از کل $graceDays روز", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("حداقل حضور مورد نیاز در ماه:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${currentLevel.minVisitDays} روز مراجعه", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("تعداد مراجعات ثبت‌شده شما:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${customer.totalVisitsCount} نوبت", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("کل خرید معتبر شما تاکنون:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${numberFormat.format(customer.totalQualifiedSpend)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Section 4: Next level perks preview
                if (nextLevel != null) {
                    Text("🎁 مزایایی که در سطح ${nextLevel.name} منتظر شماست:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = nextBadgeColor)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = nextBadgeColor.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, nextBadgeColor.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("پاداش سکه GN هنگام ورود به سطح:", fontSize = 10.sp)
                                Text("${nextLevel.reachGnBonus.toInt()} GN هدیه 🎁", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("درصد دریافت GN از بازی / بوفه:", fontSize = 10.sp)
                                Text("بازی ${nextLevel.gameGnPercent.toInt()}% | بوفه ${nextLevel.buffetGnPercent.toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("پاداش دعوت دوست / سقف پرداخت GN:", fontSize = 10.sp)
                                Text("${nextLevel.inviteGnReward.toInt()} GN / سقف ${nextLevel.maxGnPaymentPercent.toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            val activePerks = nextLevel.nonFinancialPerks.filter { it.isActive }
                            if (activePerks.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = nextBadgeColor.copy(alpha = 0.2f))
                                Text("⭐ مزایای غیرمالی اختصاصی:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = nextBadgeColor)
                                activePerks.forEach { perk ->
                                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp)) {
                                        Text("✦ ", fontSize = 10.sp, color = nextBadgeColor, fontWeight = FontWeight.Bold)
                                        Column {
                                            Text(perk.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            if (perk.description.isNotBlank()) {
                                                Text(perk.description, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 5: Activity history ledger
                if (customerLedger.isNotEmpty()) {
                    Text("📜 تاریخچه و تراکنش‌های اخیر امتیاز/سکه:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        customerLedger.take(5).forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.description.ifBlank { entry.source }, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        val dateStr = remember(entry.timestamp) {
                                            if (entry.timestamp > 0) {
                                                java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))
                                            } else ""
                                        }
                                        if (dateStr.isNotBlank()) {
                                            Text(dateStr, fontSize = 8.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                    val isEarn = entry.gnAmount >= 0
                                    Text(
                                        text = "${if (isEarn) "+" else ""}${numberFormat.format(entry.gnAmount)} GN",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEarn) Color(0xFF2E7D32) else Color(0xFFC2410C)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("متوجه شدم", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ActiveStationLiveCard(
    station: StationState,
    viewModel: GameNetViewModel
) {
    val isRunning = station.status == "RUNNING"
    val isPaused = station.status == "PAUSED"

    var currentTimeMs by remember(station.id, station.startTimeMillis, station.status, station.lastStateChangeTimeMillis) {
        mutableLongStateOf(System.currentTimeMillis())
    }

    // Local device timer ticking every 1s without any network request
    LaunchedEffect(station.id, station.startTimeMillis, station.status, station.lastStateChangeTimeMillis) {
        while (station.status == "RUNNING") {
            currentTimeMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsedMs = remember(isRunning, isPaused, currentTimeMs, station.elapsedPlayingTimeMillis, station.lastStateChangeTimeMillis) {
        when {
            isRunning -> (station.elapsedPlayingTimeMillis + (currentTimeMs - station.lastStateChangeTimeMillis)).coerceAtLeast(0L)
            isPaused -> station.elapsedPlayingTimeMillis.coerceAtLeast(0L)
            else -> 0L
        }
    }

    val elapsedSeconds = (elapsedMs / 1000) % 60
    val elapsedMinutes = (elapsedMs / (1000 * 60)) % 60
    val elapsedHours = elapsedMs / (1000 * 3600)

    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", elapsedHours, elapsedMinutes, elapsedSeconds)

    val cloudLiveStations by SelfHostedManager.allLiveStations.collectAsState()
    val cloudSt = cloudLiveStations.find { it.stationId == station.id }
    val currentCustomerAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()

    val rateFromVm = viewModel.getHourlyRateSync(station.consoleType, station.controllerCount)
    val hourlyRate: Double = if ((cloudSt?.hourlyRate ?: 0L) > 0L) (cloudSt!!.hourlyRate).toDouble() else if (rateFromVm > 0) rateFromVm.toDouble() else 60000.0
    val calculatedGameCost = (elapsedMs.toDouble() / (1000.0 * 3600.0)) * hourlyRate

    // Station Orders / Buffet calculation
    val stationOrdersMap by viewModel.stationOrdersMap.collectAsState()
    val products by viewModel.products.collectAsState()
    val allCustomers by viewModel.customers.collectAsState()

    val sessionCustomerNames = remember(station, cloudLiveStations, allCustomers) {
        val namesList = mutableListOf<String>()
        // 1. From station entity
        namesList.addAll(station.getCustomerNames().filter { it.isNotBlank() })
        // 2. From station customer IDs matching local DB
        val ids = station.getCustomerIds()
        allCustomers.filter { ids.contains(it.id) }.forEach { c ->
            if (!namesList.contains(c.fullName)) {
                namesList.add(c.fullName)
            }
        }
        // 3. From CloudLiveStation
        if (cloudSt != null && cloudSt.customerNamesStr.isNotBlank()) {
            cloudSt.customerNamesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { cName ->
                if (!namesList.contains(cName)) {
                    namesList.add(cName)
                }
            }
        }
        namesList.distinct()
    }

    val ordersForStation = remember(stationOrdersMap, cloudLiveStations, station.id, products) {
        val list = mutableListOf<Pair<String, Pair<Int, Double>>>()
        
        // 1. From local station orders map
        val localOrders = stationOrdersMap[station.id] ?: emptyList()
        if (localOrders.isNotEmpty()) {
            localOrders.forEach { ord ->
                val p = products.find { it.name.trim().equals(ord.productName.trim(), ignoreCase = true) }
                val price: Double = p?.price?.toDouble() ?: 0.0
                list.add(ord.productName to (ord.quantity to price))
            }
        }
        
        // 2. From cloud live station ordersJson (critical for customer app synced from manager)
        if (cloudSt != null && cloudSt.ordersJson.isNotBlank()) {
            try {
                val arr = org.json.JSONArray(cloudSt.ordersJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.optString("product_name").ifEmpty { obj.optString("productName") }.ifEmpty { obj.optString("name") }
                    val qty = obj.optInt("quantity", obj.optInt("qty", 1))
                    val p = products.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
                    val rawPrice = obj.opt("price") ?: obj.opt("unit_price") ?: obj.opt("cost")
                    val price: Double = when (rawPrice) {
                        is Number -> rawPrice.toDouble()
                        is String -> rawPrice.toDoubleOrNull() ?: (p?.price?.toDouble() ?: 0.0)
                        else -> p?.price?.toDouble() ?: 0.0
                    }
                    if (name.isNotBlank()) {
                        val existingIdx = list.indexOfFirst { it.first.trim().equals(name.trim(), ignoreCase = true) }
                        if (existingIdx >= 0) {
                            val currentPair = list[existingIdx]
                            val finalPrice: Double = if (currentPair.second.second > 0.0) currentPair.second.second else price
                            val finalQty: Int = if (currentPair.second.first > 0) currentPair.second.first else qty
                            list[existingIdx] = Pair(currentPair.first, Pair(finalQty, finalPrice))
                        } else {
                            list.add(Pair(name, Pair(qty, price)))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        list
    }

    val calculatedBuffetSum = ordersForStation.sumOf { it.second.first * it.second.second }
    val totalBuffetCost: Double = if (calculatedBuffetSum > 0.0) calculatedBuffetSum else (cloudSt?.currentBuffetCost ?: 0L).toDouble()
    val totalSessionCost = calculatedGameCost + totalBuffetCost
    val numberFormat = remember { DecimalFormat("#,###") }

    // Split Cost Calculation per customer
    val splitMode = cloudSt?.splitMode ?: station.splitMode
    val payerNames = (cloudSt?.payerCustomerNamesStr?.split(",") ?: station.getPayerCustomerNames()).map { it.trim() }.filter { it.isNotBlank() }
    val payerIds = (cloudSt?.payerCustomerIdsStr?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: station.getPayerCustomerIds())
    val myCustId = currentCustomerAuth?.id ?: 0L
    val myCustName = currentCustomerAuth?.fullName ?: ""

    val myGameCostShare = when (splitMode) {
        "SINGLE" -> {
            val isMainPayer = if (payerIds.isNotEmpty()) payerIds.contains(myCustId) else (payerNames.contains(myCustName) || sessionCustomerNames.firstOrNull() == myCustName)
            if (isMainPayer) calculatedGameCost else 0.0
        }
        "CUSTOM" -> {
            val count = maxOf(1, if (payerIds.isNotEmpty()) payerIds.size else payerNames.size)
            val isCustomPayer = payerIds.contains(myCustId) || payerNames.contains(myCustName)
            if (isCustomPayer) calculatedGameCost / count else 0.0
        }
        else -> { // "ALL"
            val totalPlayers = maxOf(1, if (sessionCustomerNames.isNotEmpty()) sessionCustomerNames.size else station.controllerCount)
            calculatedGameCost / totalPlayers
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A)
        ),
        border = BorderStroke(1.5.dp, if (isRunning) Color(0xFF3B82F6) else Color(0xFFF59E0B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = if (isRunning) Color(0xFF60A5FA) else Color(0xFFFBBF24),
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "⚡ نشست فعال: ایستگاه ${station.id}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "نوع کنسول: ${station.consoleType.ifBlank { "PS5" }} | ${station.controllerCount} دسته",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isRunning) Color(0xFF22C55E).copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF22C55E) else Color(0xFFF59E0B))
                        )
                        Text(
                            text = if (isRunning) "در حال کار" else "متوقف شده (پاز)",
                            color = if (isRunning) Color(0xFF22C55E) else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer Counter
                Column {
                    Text(
                        text = "زمان کارکرد:",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = timeFormatted,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isRunning) Color(0xFF60A5FA) else Color(0xFFFBBF24)
                    )
                }

                // Total Session Cost
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "مجموع کل این نشست:",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = "${numberFormat.format(totalSessionCost)} تومان",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4ADE80)
                    )
                }
            }

            // Itemized breakdown: Game Cost & Buffet Cost
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🎮 هزینه زمان بازی:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                        Text("${numberFormat.format(calculatedGameCost)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🍿 سفارشات بوفه و خوراکی:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                        Text("${numberFormat.format(totalBuffetCost)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFCD34D))
                    }

                    if (sessionCustomerNames.size > 1 || splitMode != "ALL") {
                        HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💳 سهم شما از هزینه بازی:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF60A5FA)
                            )
                            Text(
                                text = "${numberFormat.format(myGameCostShare)} تومان",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF60A5FA)
                            )
                        }
                        if (splitMode == "SINGLE" && payerNames.isNotEmpty()) {
                            Text(
                                text = "📌 هزینه کل بازی توسط: ${payerNames.joinToString("، ")} پرداخت می‌شود.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    if (station.prepaymentAmount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("💳 پیش‌پرداخت ثبت شده:", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                            Text("- ${numberFormat.format(station.prepaymentAmount)} تومان", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6EE7B7))
                        }
                    }

                    if (ordersForStation.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                        Text("جزئیات خوراکی‌های سفارش داده شده:", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        ordersForStation.forEach { item ->
                            val itemName = item.first
                            val qty = item.second.first
                            val price = item.second.second
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("• $itemName (تعداد: $qty)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                Text("${numberFormat.format(qty * price)} تومان", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }
            }

            // Players & Friends in this session
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "👥 مخاطبان و دوستان در حال بازی (${station.controllerCount} دسته):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF93C5FD)
                            )
                        }

                        Text(
                            text = "${sessionCustomerNames.size} نفر ثبت شده",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    if (sessionCustomerNames.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sessionCustomerNames.forEach { pName ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, Color(0xFF60A5FA).copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = pName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "مشتری عمومی یا دوستان ثبت نشده (${station.controllerCount} دسته فعال)",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAppContent(
    viewModel: GameNetViewModel,
    modifier: Modifier = Modifier
) {
    val lang by viewModel.language.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val currentCustomerAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()
    val isCustomerKickedOut by SelfHostedManager.isCustomerKickedOut.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    // 1. Instant kick out event watcher
    LaunchedEffect(isCustomerKickedOut) {
        if (isCustomerKickedOut) {
            SelfHostedManager.resetKickedOutFlag()
            viewModel.logout()
            android.widget.Toast.makeText(
                context,
                "حساب کاربری شما توسط مدیریت حذف شد و دسترسی شما قطع گردید.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val customerName = currentCustomerAuth?.fullName ?: "مشتری عزیز"
    val customerPhone = currentCustomerAuth?.phoneNumber ?: ""

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customerName.take(1).ifBlank { "G" },
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Column {
                            Text(
                                text = customerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (customerPhone.isNotBlank()) {
                                Text(
                                    text = customerPhone,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    val coroutineScope = rememberCoroutineScope()
                    IconButton(onClick = { showDiagnosticsDialog = true }) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Network Diagnostics", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { 
                        coroutineScope.launch { 
                            com.example.data.network.SelfHostedManager.fetchAllFromCloud() 
                        } 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = "Logout",
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
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "داشبورد") },
                    label = { Text(if (lang == "fa") "داشبورد" else "Dashboard", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "کیف پولم") },
                    label = { Text(if (lang == "fa") "کیف پولم" else "My Wallet", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, contentDescription = "تاریخچه") },
                    label = { Text(if (lang == "fa") "تاریخچه" else "History", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.EventSeat, contentDescription = "رزرو جایگاه") },
                    label = { Text(if (lang == "fa") "رزرو جایگاه" else "Booking", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "خرید و پرداخت") },
                    label = { Text(if (lang == "fa") "خرید و پرداخت" else "Store", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "قوانین GN/LP") },
                    label = { Text(if (lang == "fa") "قوانین GN/LP" else "GN/LP Rules", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val announcement = (viewModel.ownerBroadcastAnnouncement.collectAsState().value?.message?.takeIf { it.isNotBlank() })
                ?: viewModel.ownerBroadcastMessage.collectAsState().value
            if (announcement.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E3A8A).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "پیام مدیریت گیم‌نکسا",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF2563EB)
                            )
                            Text(
                                text = announcement,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> CustomerDashboardTab(viewModel = viewModel, onNavigateTab = { selectedTab = it })
                    1 -> CustomerWalletTab(viewModel = viewModel)
                    2 -> CustomerHistoryTab(viewModel = viewModel)
                    3 -> CustomerReservationTab(viewModel = viewModel)
                    4 -> CustomerOnlinePaymentTab(viewModel = viewModel)
                    5 -> CustomerClubRulesTab(viewModel = viewModel)
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("خروج از حساب کاربری", fontWeight = FontWeight.Bold) },
            text = { Text("آیا مطمئن هستید که می‌خواهید از حساب کاربری خود خارج شوید؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("خروج")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        NetworkDiagnosticsDialog(onDismissRequest = { showDiagnosticsDialog = false })
    }
}
