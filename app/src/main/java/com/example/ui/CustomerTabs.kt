package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.data.network.SelfHostedManager
import com.example.util.JalaliCalendarHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomerWalletTab(viewModel: GameNetViewModel) {
    val customers by viewModel.customers.collectAsState()
    val allCloudCustomers by SelfHostedManager.allCloudCustomers.collectAsState()
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

    var selectedSection by remember { mutableIntStateOf(0) }
    val numberFormat = remember { DecimalFormat("#,###") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Balances Header Card (Toman Credit, Debt, GN Balance, LP Points)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
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
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "کیف پول و موجودی حساب",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = matchedCustomer.fullName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Text(
                            text = "سطح: ${matchedCustomer.tier}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // ROW 1: Toman Credit & Debt
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Toman Balance / Credit
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("موجودی تومانی", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${numberFormat.format(matchedCustomer.credit)} تومان",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF2E7D32)
                            )
                            Text("شارژ نقدی حساب", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Toman Debt
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (matchedCustomer.debt > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("بدهی جاری", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${numberFormat.format(matchedCustomer.debt)} تومان",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = if (matchedCustomer.debt > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (matchedCustomer.debt > 0) "نیازمند تسویه ⚠️" else "تسویه شده ✓",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (matchedCustomer.debt > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                // ROW 2: GN Balance & LP Points
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // GN Balance
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("موجودی GN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${numberFormat.format(matchedCustomer.availableGn)} GN",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (matchedCustomer.pendingGn > 0) {
                                Text(
                                    text = "در انتظار: ${numberFormat.format(matchedCustomer.pendingGn)}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100)
                                )
                            } else {
                                Text("اعتبار رسمی", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // LP Points
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("امتیاز باشگاه (LP)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${numberFormat.format(matchedCustomer.lp)} LP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "رتبه و وفاداری",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Rules Banner Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "قوانین استفاده و پرداخت با اعتبار GN:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "• امکان پرداخت تا 30٪ از هزینه بازی سالن با موجودی GN (قابل تغییر توسط مدیر)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• امکان پرداخت تا 50٪ از هزینه بوفه و کافه با موجودی GN (قابل تغییر توسط مدیر)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• تمام مبالغ پرداختی پس از بررسی و تایید توسط مدیریت به حساب کاربری شما افزوده می‌شود.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Action Tabs: Charge, Buy GN, Transfer GN
        TabRow(
            selectedTabIndex = selectedSection,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("شارژ کیف پول", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
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

        when (selectedSection) {
            0 -> ChargeAccountSection(viewModel)
            1 -> BuyGnSection(viewModel)
            2 -> TransferGnSection(viewModel)
        }
    }
}

@Composable
fun CustomerHistoryTab(viewModel: GameNetViewModel) {
    val customers by viewModel.customers.collectAsState()
    val currentCustomerAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()
    val allTransactions by viewModel.customerTransactions.collectAsState()
    val allBehaviorLogs by viewModel.allBehaviorLogs.collectAsState()

    val matchedCustomer = remember(customers, currentCustomerAuth) {
        customers.find { it.phoneNumber == currentCustomerAuth?.phoneNumber || (it.id > 0 && it.id == currentCustomerAuth?.id) }
            ?: currentCustomerAuth?.let {
                Customer(
                    id = it.id,
                    fullName = it.fullName ?: "",
                    phoneNumber = it.phoneNumber ?: "",
                    availableGn = it.availableGn,
                    pendingGn = it.pendingGn,
                    lp = it.lp,
                    tier = it.tier ?: "",
                    points = it.points
                )
            }
    }

    val customerId = matchedCustomer?.id ?: 0L
    val customerPhone = matchedCustomer?.phoneNumber ?: ""
    val customerName = matchedCustomer?.fullName ?: ""

    var refreshTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(customerId) {
        while (isActive && customerId > 0) {
            refreshTrigger = System.currentTimeMillis()
            delay(20000L) // Relaxed from 5s to 20s
        }
    }

    val cloudTransactions by produceState<List<CustomerTransaction>>(initialValue = emptyList(), key1 = customerId, key2 = refreshTrigger) {
        if (customerId > 0) {
            value = try {
                SelfHostedManager.fetchCustomerTransactionsForCustomer(customerId)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val customerTransactions = remember(allTransactions, cloudTransactions, customerId, customerName, customerPhone) {
        val localFiltered = allTransactions.filter {
            (customerId > 0 && it.customerId == customerId) ||
            (customerName.isNotBlank() && (it.customerName ?: "").contains(customerName, ignoreCase = true)) ||
            (customerPhone.isNotBlank() && ((it.customerName ?: "").contains(customerPhone) || it.title.contains(customerPhone)))
        }
        val map = mutableMapOf<Long, CustomerTransaction>()
        localFiltered.forEach { map[if (it.id > 0) it.id else it.timestamp] = it }
        cloudTransactions.forEach {
            val key = if (it.id > 0) it.id else it.timestamp
            if (!map.containsKey(key)) {
                map[key] = it
            }
        }
        map.values.sortedByDescending { if (it.timestamp > 0) it.timestamp else it.id }
    }

    val customerBehaviorLogs = remember(allBehaviorLogs, customerId) {
        allBehaviorLogs.filter { it.customerId == customerId }.sortedByDescending { it.timestamp }
    }

    val customerLedger by produceState<List<GnLedgerEntry>>(initialValue = emptyList(), key1 = customerId) {
        if (customerId > 0) {
            value = try {
                SelfHostedManager.fetchGnLedgerForCustomer(customerId)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val allRecentPayments by SelfHostedManager.recentPayments.collectAsState()
    val customerPayments = remember(allRecentPayments, customerId) {
        allRecentPayments.filter { it.customerId == customerId }.sortedByDescending { it.timestamp }
    }

    var selectedFilter by remember { mutableIntStateOf(0) }
    val numberFormat = remember { DecimalFormat("#,###") }
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    fun formatDate(timestamp: Long): String {
        return if (timestamp > 0) {
            try {
                dateFormat.format(Date(timestamp))
            } catch (e: Exception) {
                "---"
            }
        } else "---"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Filter Chips
        ScrollableTabRow(
            selectedTabIndex = selectedFilter,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Tab(selected = selectedFilter == 0, onClick = { selectedFilter = 0 }, text = { Text("همه سوابق (${customerTransactions.size + customerLedger.size + customerBehaviorLogs.size + customerPayments.size})", fontSize = 11.sp) })
            Tab(selected = selectedFilter == 1, onClick = { selectedFilter = 1 }, text = { Text("بازی‌ها و فاکتورها (${customerTransactions.size})", fontSize = 11.sp) })
            Tab(selected = selectedFilter == 2, onClick = { selectedFilter = 2 }, text = { Text("تراکنش‌های GN (${customerLedger.size})", fontSize = 11.sp) })
            Tab(selected = selectedFilter == 3, onClick = { selectedFilter = 3 }, text = { Text("سوابق انضباطی (${customerBehaviorLogs.size})", fontSize = 11.sp) })
            Tab(selected = selectedFilter == 4, onClick = { selectedFilter = 4 }, text = { Text("سوابق شارژ و پرداخت آنلاین (${customerPayments.size})", fontSize = 11.sp) })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedFilter == 0 || selectedFilter == 1) {
                if (customerTransactions.isNotEmpty()) {
                    item {
                        Text(
                            text = "🎮 فاکتورها و نشست‌های بازی:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    itemsIndexed(customerTransactions, key = { idx, trans -> "trans_${trans.id}_${trans.timestamp}_$idx" }) { _, trans ->
                        val context = LocalContext.current
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "${(trans.stationName ?: "").ifBlank { "ایستگاه بازی" }} (${(trans.title ?: "").ifBlank { "نشست بازی" }})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when (trans.status) {
                                            "REVIEWED" -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                            "DEBTOR" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            else -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                        }
                                    ) {
                                        Text(
                                            text = when (trans.status) {
                                                "REVIEWED" -> "تسویه شده ✓"
                                                "DEBTOR" -> "بدهی ⚠️"
                                                else -> "در انتظار بررسی ⏳"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (trans.status) {
                                                "REVIEWED" -> Color(0xFF2E7D32)
                                                "DEBTOR" -> MaterialTheme.colorScheme.error
                                                else -> Color(0xFFFF9800)
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🎮 هزینه زمان بازی (${trans.playMinutes} دقیقه):",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${numberFormat.format(trans.gameCost)} تومان",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (!trans.buffetDetails.isNullOrBlank() || trans.foodCost > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "🍿 سفارشات بوفه و خوراکی:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                        Text(
                                            text = "${numberFormat.format(trans.foodCost)} تومان",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD97706)
                                        )
                                    }

                                    val buffetItems = (trans.buffetDetails ?: "").split(Regex("(?<=\\))\\s*,\\s*|\\n")).map { it.trim() }.filter { it.isNotBlank() }
                                    buffetItems.forEach { item ->
                                        Text(
                                            text = "• $item",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                val earnedGnPoints = trans.earnedGn
                                val earnedLpPoints = trans.earnedLp

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF3B82F6).copy(alpha = 0.12f),
                                    border = BorderStroke(0.5.dp, Color(0xFF3B82F6).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("💎", fontSize = 11.sp)
                                            Text("پاداش GN دریافتی این نشست:", fontSize = 11.sp, color = Color(0xFF3B82F6), fontWeight = FontWeight.Bold)
                                        }
                                        Text("+$earnedGnPoints GN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3B82F6))
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF42A5F5).copy(alpha = 0.10f),
                                    border = BorderStroke(0.5.dp, Color(0xFF42A5F5).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🏆 LP دریافتی از این نشست:", fontSize = 11.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                                        Text("+$earnedLpPoints LP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1565C0))
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${trans.dateStr ?: ""} | ${trans.timeStr ?: ""}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "مجموع: ${numberFormat.format(trans.amount)} تومان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val buffetListLines = if (!trans.buffetDetails.isNullOrBlank()) {
                                            trans.buffetDetails.split(Regex("(?<=\\))\\s*,\\s*|\\n")).map { "  • ${it.trim()}" }.joinToString("\n")
                                        } else {
                                            "  (سفارشی ثبت نشده)"
                                        }
                                        val receipt = buildString {
                                            appendLine("================================")
                                            appendLine("       🎮 فاکتور رسمی گیم‌نت 🎮")
                                            appendLine("================================")
                                            appendLine("مشتری: ${trans.customerName}")
                                            appendLine("تاریخ: ${trans.dateStr} | ساعت: ${trans.timeStr}")
                                            appendLine("ایستگاه: ${trans.stationName} (${trans.title})")
                                            appendLine("مدت زمان بازی: ${trans.playMinutes} دقیقه")
                                            appendLine("--------------------------------")
                                            appendLine("🎮 هزینه بازی: %,.0f تومان".format(Locale.US, trans.gameCost))
                                            appendLine("--------------------------------")
                                            appendLine("🍿 اقلام بوفه و خوراکی:")
                                            appendLine(buffetListLines)
                                            appendLine("مجموع بوفه: %,.0f تومان".format(Locale.US, trans.foodCost))
                                            appendLine("--------------------------------")
                                            appendLine("💰 جمع کل نهایی: %,.0f تومان".format(Locale.US, trans.amount))
                                            if (trans.paidAmount > 0) {
                                                appendLine("💳 مبلغ پرداختی: %,.0f تومان".format(Locale.US, trans.paidAmount))
                                            }
                                            if (trans.status == "DEBTOR") {
                                                appendLine("⚠️ وضعیت: بدهی مانده %,.0f تومان".format(Locale.US, (trans.amount - trans.paidAmount).toDouble().coerceAtLeast(0.0)))
                                            } else if (trans.status == "REVIEWED") {
                                                appendLine("✅ وضعیت: تسویه کامل")
                                            }
                                            appendLine("💎 پاداش GN کسب شده: +$earnedGnPoints GN")
                                            appendLine("🏆 پاداش LP کسب شده: +$earnedLpPoints LP")
                                            appendLine("================================")
                                            appendLine("    با تشکر از حضور گرم شما!")
                                        }

                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "فاکتور گیم‌نت - ${trans.customerName}")
                                            putExtra(Intent.EXTRA_TEXT, receipt)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "چاپ و اشتراک فاکتور"))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(32.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("مشاهده و اشتراک‌گذاری فاکتور", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (selectedFilter == 0 || selectedFilter == 2) {
                if (customerLedger.isNotEmpty()) {
                    item {
                        Text(
                            text = "🪙 گردش کیف پول GN:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    itemsIndexed(customerLedger, key = { idx, entry -> "ledger_${entry.id}_${entry.timestamp}_$idx" }) { _, entry ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text((entry.description ?: "").ifBlank { entry.transactionType ?: "" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(formatDate(entry.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val isPositive = entry.gnAmount >= 0
                                Text(
                                    text = if (isPositive) "+${numberFormat.format(entry.gnAmount)} GN" else "${numberFormat.format(entry.gnAmount)} GN",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (selectedFilter == 0 || selectedFilter == 3) {
                if (customerBehaviorLogs.isNotEmpty()) {
                    item {
                        Text(
                            text = "⭐ سوابق امتیازات و انضباطی باشگاه:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    itemsIndexed(customerBehaviorLogs, key = { idx, log -> "log_${log.id}_${log.timestamp}_$idx" }) { _, log ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text((log.reason ?: "").ifBlank { log.ruleTitle ?: "" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(formatDate(log.timestamp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val isPositive = log.lpChange >= 0
                                Text(
                                    text = if (isPositive) "+${log.lpChange.toInt()} LP" else "${log.lpChange.toInt()} LP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (selectedFilter == 0 || selectedFilter == 4) {
                if (customerPayments.isNotEmpty()) {
                    item {
                        Text(
                            text = "💳 سوابق شارژ و پرداخت آنلاین:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    itemsIndexed(customerPayments, key = { idx, pay -> "pay_${pay.id}_${pay.timestamp}_$idx" }) { _, pay ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(text = "مبلغ: ${numberFormat.format(pay.amount)} تومان", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (pay.status == "SUCCESS") Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = if (pay.status == "SUCCESS") "موفق" else "ناموفق",
                                            color = if (pay.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFF44336),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "نوع تراکنش: ${if (pay.paymentType == "CHARGE_WALLET") "شارژ کیف پول GN" else if (pay.paymentType == "PAY_DEBT") "تسویه بدهی" else (pay.paymentType ?: "")}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "کد پیگیری: ${(pay.trackingCode ?: "").ifBlank { "---" }}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "تاریخ: ${formatDate(pay.timestamp)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (customerTransactions.isEmpty() && customerLedger.isEmpty() && customerBehaviorLogs.isEmpty() && customerPayments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "هنوز سابقه‌ای برای حساب شما ثبت نشده است.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerReservationTab(viewModel: GameNetViewModel) {
    val context = LocalContext.current
    val stationStates by viewModel.stationStates.collectAsState()
    val reservations by viewModel.reservations.collectAsState()
    val currentCustomerAuth by SelfHostedManager.currentLoggedInCustomer.collectAsState()

    var selectedStationId by remember { mutableIntStateOf(0) }
    var selectedConsole by remember { mutableStateOf("") }
    var selectedDuration by remember { mutableIntStateOf(0) }
    var selectedPlayerCount by remember { mutableIntStateOf(2) }
    var selectedStartMillis by remember { mutableLongStateOf(0L) }
    var noteText by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }
    var selectedPrice by remember { mutableDoubleStateOf(0.0) }
    var selectedIsVip by remember { mutableStateOf(false) }
    var lastReservationId by remember { mutableLongStateOf(0L) }
    var showStationReservations by remember { mutableStateOf(false) }
    var stationReservationRows by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var showJalaliDatePicker by remember { mutableStateOf(false) }
    val todayJalali = remember { com.example.util.JalaliCalendarHelper.currentJalali() }
    var selectedJalali by remember { mutableStateOf(todayJalali) }
    val jalaliMonths = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    val customerTier = currentCustomerAuth?.tier?.uppercase().orEmpty()
    val directVipAllowed = customerTier == "GOLD" || customerTier == "DIAMOND"

    var lastReservationAmount by remember { mutableLongStateOf(0L) }
    var paymentMethodText by remember { mutableStateOf("") }
    var paymentTrackingCode by remember { mutableStateOf("") }
    var paymentMessage by remember { mutableStateOf("") }
    var paymentSubmitting by remember { mutableStateOf(false) }
    var serverReservations by remember { mutableStateOf<List<Reservation>>(emptyList()) }
    var customerStations by remember { mutableStateOf<org.json.JSONArray?>(null) }
    var rules by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var rulesLoading by remember { mutableStateOf(true) }
    var rulesError by remember { mutableStateOf<String?>(null) }
    val reservationScope = rememberCoroutineScope()

    LaunchedEffect(currentCustomerAuth?.id, selectedDuration) {
        serverReservations = SelfHostedManager.fetchCustomerReservations()
        customerStations = SelfHostedManager.fetchCustomerStations()
        customerStations?.let { stations ->
            if (stations.length() > 0) {
                val first = stations.optJSONObject(0)
                selectedStationId = first?.optLong("id", selectedStationId.toLong())?.toInt() ?: selectedStationId
                selectedConsole = first?.optString("console_type").orEmpty()
            }
        }
        rulesLoading = true
        rulesError = null
        rules = try {
            SelfHostedManager.fetchReservationRules(durationMinutes = selectedDuration)
        } catch (e: Exception) {
            null
        }
        if (rules == null) rulesError = "قوانین رزرو از سرور دریافت نشد."
        val configuredDurations = rules?.optJSONObject("rules")?.optJSONArray("normalDurationsMinutes")
        if (configuredDurations != null && configuredDurations.length() > 0) {
            selectedDuration = configuredDurations.optInt(0, selectedDuration)
        }
        rulesLoading = false
    }

    val rulesObject = remember(rules) { rules?.optJSONObject("rules") ?: org.json.JSONObject() }
    val normalDurations = remember(rulesObject) {
        val a = rulesObject.optJSONArray("normalDurationsMinutes") ?: org.json.JSONArray()
        buildList { for (i in 0 until a.length()) if (a.optInt(i) > 0) add(a.optInt(i)) }
    }
    val vipMinDuration = rulesObject.optInt("vipMinDurationMinutes", 180)
    val vipDurations = remember(rulesObject) {
        val a = rulesObject.optJSONArray("vipDurationsMinutes") ?: org.json.JSONArray()
        buildList { for (i in 0 until a.length()) if (a.optInt(i) >= vipMinDuration) add(a.optInt(i)) }
    }
    val durationOptions = if (selectedIsVip) vipDurations else normalDurations
    val cancellation = remember(rulesObject) { rulesObject.optJSONObject("cancellation") ?: org.json.JSONObject() }
    val messages = remember(rulesObject) { rulesObject.optJSONObject("messages") ?: org.json.JSONObject() }
    val selectedDurationLabel = remember(selectedDuration) {
        if (selectedDuration <= 0) "انتخاب نشده" else if (selectedDuration % 60 == 0) "${selectedDuration / 60} ساعت" else "$selectedDuration دقیقه"
    }
    val selectedStartLabel = remember(selectedStartMillis) {
        if (selectedStartMillis <= 0L) "زمان شروع را انتخاب کنید" else JalaliCalendarHelper.formatJalaliDateTime(selectedStartMillis)
    }

    LaunchedEffect(selectedStationId, selectedDuration, selectedPlayerCount, selectedStartMillis, selectedIsVip) {
        if (selectedDuration > 0 && selectedStartMillis > 0L && (selectedIsVip || selectedStationId > 0)) {
            val preview = SelfHostedManager.previewReservationPricing(
                com.example.data.network.PricingPreviewRequest(
                    reservationType = if (selectedIsVip) "VIP" else "NORMAL_RESERVATION",
                    stationId = if (selectedIsVip) null else selectedStationId.toLong(),
                    durationMinutes = selectedDuration,
                    reservationTimeMillis = selectedStartMillis,
                    controllersCount = selectedPlayerCount
                )
            )
            selectedPrice = preview?.finalPrice ?: 0.0
        }
    }

    val myReservations = remember(serverReservations) { serverReservations }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Salon Live Station Overview
        Text(
            text = "🎮 وضعیت لحظه‌ای جایگاه‌های سالن:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stationsList = buildList {
                val cloud = customerStations
                if (cloud != null) {
                    for (i in 0 until cloud.length()) {
                        val s = cloud.optJSONObject(i) ?: continue
                        val id = s.optLong("id", 0L).toInt()
                        if (id > 0) add(
                            Triple(
                                id,
                                s.optString("console_type").ifBlank { "" },
                                s.optBoolean("has_prior_reservation", false)
                            )
                        )
                    }
                }
            }
            if (stationsList.isEmpty()) {
                Text("هیچ جایگاه قابل رزروی از طرف مدیریت در دسترس نیست.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            stationsList.forEach { (stationId, consoleType, hasPriorReservation) ->
                val live = stationStates.firstOrNull { it.id == stationId }
                val isSelected = selectedStationId == stationId
                val isBusy = live?.status == "RUNNING"
                Surface(
                    onClick = {
                        selectedStationId = stationId
                        selectedConsole = consoleType.ifBlank { "" }
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.5.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else if (hasPriorReservation) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else Color(0xFF2E7D32).copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.width(110.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("ایستگاه $stationId", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(consoleType.ifBlank { "" }, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Badge(
                            containerColor = if (hasPriorReservation) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = if (hasPriorReservation) "!" else "سبز",
                                fontSize = 9.sp,
                                color = Color.White,
                                modifier = Modifier.padding(2.dp)
                            )
                        }
                        if (hasPriorReservation) {
                            TextButton(onClick = {
                                reservationScope.launch {
                                    stationReservationRows = SelfHostedManager.fetchCustomerStationReservations(stationId.toLong())
                                    showStationReservations = true
                                }
                            }, contentPadding = PaddingValues(0.dp)) { Text("نمایش رزروها", fontSize = 9.sp) }
                        }
                    }
                }
            }
        }

        // Reservation type is part of the same single reservation page; direct VIP is role-gated by the server too.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !selectedIsVip, onClick = { selectedIsVip = false }, label = { Text("رزرو عادی") }, modifier = Modifier.weight(1f))
            if (directVipAllowed) {
                FilterChip(selected = selectedIsVip, onClick = { selectedIsVip = true; selectedStationId = 0; if (vipDurations.isNotEmpty()) selectedDuration = vipDurations.first() }, label = { Text("VIP / Full Game") }, modifier = Modifier.weight(1f))
            }
        }
        if (!directVipAllowed) Text("درخواست مستقیم VIP فقط برای مشتریان Gold و Diamond فعال است.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Reservation Form Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "ثبت درخواست رزرو جایگاه",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Station & Console selection info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (selectedStationId > 0) "ایستگاه انتخابی: شماره $selectedStationId" else "ایستگاه انتخابی: انتخاب نشده", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("کنسول: $selectedConsole", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                // Players Count Chips
                Text("تعداد دسته / بازیکنان:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 4).forEach { count ->
                        val isSel = selectedPlayerCount == count
                        Surface(
                            onClick = { selectedPlayerCount = count },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$count نفره 🎮",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Duration options are supplied by the Manager's server-side reservation configuration.
                Text(if (selectedIsVip) "مدت VIP:" else "مدت زمان درخواستی:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (rulesLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (durationOptions.isEmpty()) {
                    Text(rulesError ?: "مدت‌های رزرو برای این مدیریت تنظیم نشده است.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durationOptions.forEach { min ->
                            val isSel = selectedDuration == min
                            Surface(
                                onClick = { selectedDuration = min },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (min % 60 == 0) "${min / 60} ساعت" else "$min دقیقه",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedPrice > 0.0) {
                    Text("مبلغ محاسبه‌شده توسط سرور: " + selectedPrice.toLong() + " طبق واحد پول مدیریت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                val policyMessages = listOf(
                    messages.optString("cancel24"),
                    messages.optString("cancel15"),
                    messages.optString("cancel5"),
                    messages.optString("cancel2"),
                    messages.optString("lateCancellation"),
                    messages.optString("noShow")
                ).filter { it.isNotBlank() }
                if (policyMessages.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            policyMessages.forEach { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                        }
                    }
                }

                val paymentMessage = messages.optString("payment").ifBlank { messages.optString("vipPaymentDeadline") }
                val arrivalMessage = messages.optString("arrival")
                if (paymentMessage.isNotBlank() || arrivalMessage.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (paymentMessage.isNotBlank()) Text(paymentMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (arrivalMessage.isNotBlank()) Text(arrivalMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }

                Text("تاریخ حضور", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("امروز" to 0, "فردا" to 1, "پس‌فردا" to 2).forEach { (label, offset) ->
                        OutlinedButton(onClick = {
                            val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")); c.add(Calendar.DAY_OF_YEAR, offset)
                            val j = JalaliCalendarHelper.formatJalaliDateTime(c.timeInMillis, false).split("/").map { it.toInt() }
                            selectedJalali = intArrayOf(j[0], j[1], j[2])
                            selectedStartMillis = JalaliCalendarHelper.jalaliToGregorianMillis(j[0], j[1], j[2], Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).get(Calendar.HOUR_OF_DAY), Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).get(Calendar.MINUTE)) ?: 0L
                        }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp)) { Text(label, fontSize = 9.sp) }
                    }
                }
                OutlinedButton(onClick = { showJalaliDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${selectedJalali[0]}/${String.format(Locale.US, "%02d", selectedJalali[1])}/${String.format(Locale.US, "%02d", selectedJalali[2])} — ${jalaliMonths[selectedJalali[1]-1]}")
                }
                if (showJalaliDatePicker) JalaliReservationDatePicker(
                    year=selectedJalali[0], month=selectedJalali[1], day=selectedJalali[2], months=jalaliMonths,
                    onSelect={y,m,d -> selectedJalali=intArrayOf(y,m,d); selectedStartMillis=JalaliCalendarHelper.jalaliToGregorianMillis(y,m,d,12,0)?:0L},
                    onDismiss={showJalaliDatePicker=false}
                )
                Text("زمان شروع: $selectedStartLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                if (selectedStartMillis > 0L && selectedDuration > 0) {
                    val endMillis = selectedStartMillis + selectedDuration * 60_000L
                    Text("زمان پایان خودکار: ${JalaliCalendarHelper.formatJalaliDateTime(endMillis)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick = {
                    val tz = TimeZone.getTimeZone("Asia/Tehran")
                    val cal = Calendar.getInstance(tz).apply { if (selectedStartMillis > 0L) timeInMillis = selectedStartMillis }
                    TimePickerDialog(context, { _, h, m ->
                        selectedStartMillis = JalaliCalendarHelper.jalaliToGregorianMillis(selectedJalali[0], selectedJalali[1], selectedJalali[2], h, m) ?: 0L
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                }, Modifier.fillMaxWidth()) { Text("انتخاب ساعت حضور: ${SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tehran") }.format(Date(selectedStartMillis.takeIf { it > 0 } ?: System.currentTimeMillis()))}") }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("توضیحات اختیاری برای مدیریت", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Button(
                    enabled = !rulesLoading && currentCustomerAuth != null && durationOptions.contains(selectedDuration) && selectedStartMillis > System.currentTimeMillis() && (selectedIsVip && directVipAllowed || !selectedIsVip && selectedStationId > 0),
                    onClick = {
                        val customer = currentCustomerAuth ?: return@Button
                        reservationScope.launch {
                            if (selectedStartMillis <= System.currentTimeMillis()) {
                                successMessage = "لطفاً زمان شروع آینده را انتخاب کنید."
                                showSuccessDialog = true
                                return@launch
                            }
                            val startMillis = selectedStartMillis
                            val key = "customer-reservation:" + java.util.UUID.randomUUID()
                            val response = SelfHostedManager.submitAtomicReservation(
                                com.example.data.network.AtomicReservationRequest(
                                    reservationType = if (selectedIsVip) "VIP" else "NORMAL_RESERVATION",
                                    stationId = if (selectedIsVip) null else selectedStationId.toLong(),
                                    durationMinutes = selectedDuration,
                                    controllersCount = selectedPlayerCount,
                                    reservationTimeMillis = startMillis,
                                    customerName = customer.fullName,
                                    customerPhone = customer.phoneNumber,
                                    idempotencyKey = key
                                )
                            )
                            successMessage = response?.message?.ifBlank { "درخواست رزرو به مدیریت ارسال شد." }
                                ?: "ثبت رزرو انجام نشد؛ لطفاً اتصال و ظرفیت را بررسی کنید."
                            lastReservationId = response?.reservationId ?: 0L
                            lastReservationAmount = selectedPrice.toLong()
                            serverReservations = SelfHostedManager.fetchCustomerReservations()
                            showSuccessDialog = true
                            if (response?.success == true) noteText = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ثبت و ارسال درخواست رزرو به مدیریت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (lastReservationId > 0L && lastReservationAmount > 0L) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("گزارش پرداخت رزرو", fontWeight = FontWeight.Bold)
                    Text("رزرو شماره " + lastReservationId + " | مبلغ: " + lastReservationAmount)
                    OutlinedTextField(
                        value = paymentMethodText,
                        onValueChange = { paymentMethodText = it },
                        label = { Text("روش پرداخت ثبت‌شده توسط مدیریت") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = paymentTrackingCode,
                        onValueChange = { paymentTrackingCode = it },
                        label = { Text("کد پیگیری / شماره رسید") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        enabled = !paymentSubmitting && paymentTrackingCode.isNotBlank(),
                        onClick = {
                            paymentSubmitting = true
                            reservationScope.launch {
                                val result = SelfHostedManager.requestReservationPaymentReview(
                                    reservationId = lastReservationId,
                                    amountToman = lastReservationAmount,
                                    paymentMethod = paymentMethodText,
                                    trackingCode = paymentTrackingCode,
                                    note = noteText
                                )
                                paymentMessage = result.fold({ it }, { it.message ?: "ثبت گزارش پرداخت ناموفق بود." })
                                paymentSubmitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (paymentSubmitting) "در حال ارسال..." else "ارسال گزارش پرداخت به مدیریت")
                    }
                    if (paymentMessage.isNotBlank()) Text(paymentMessage, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // My Reservations List
        if (myReservations.isNotEmpty()) {
            Text(
                text = "📋 درخواست‌های رزرو من:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            myReservations.forEach { res ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(res.fullName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("مدت: ${res.durationMinutes} دقیقه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val statusText = when (res.status) {
                                "CONFIRMED", "APPROVED" -> "تایید شده ✅"
                                "PENDING_APPROVAL" -> "در انتظار تایید مدیریت ⏳"
                                "VIP_PENDING_PAYMENT" -> "در انتظار پرداخت کامل VIP 💳"
                                "VIP_PAYMENT_PAID" -> "پرداخت کامل؛ منتظر تایید مدیریت ⏳"
                                "PAYMENT_PENDING" -> "در انتظار پرداخت ⏳"
                                "REJECTED" -> "رد شده ❌"
                                "CANCELLED" -> "لغو شده"
                                "EXPIRED" -> "منقضی شده"
                                else -> res.status
                            }
                            val statusColor = when (res.status) {
                                "CONFIRMED", "APPROVED" -> Color(0xFF2E7D32)
                                "VIP_PAYMENT_PAID", "PENDING_APPROVAL" -> MaterialTheme.colorScheme.primary
                                "REJECTED", "EXPIRED" -> MaterialTheme.colorScheme.error
                                else -> Color(0xFFE65100)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = statusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    reservationScope.launch {
                                        val ok = SelfHostedManager.cancelCustomerReservation(
                                            res.id,
                                            "cancel-customer:${currentCustomerAuth?.id ?: 0L}:${res.id}"
                                        )
                                        successMessage = if (ok) "درخواست لغو رزرو به سرور ارسال شد." else "لغو رزرو انجام نشد؛ قوانین لغو یا وضعیت رزرو اجازه نمی‌دهد."
                                        if (ok) serverReservations = SelfHostedManager.fetchCustomerReservations()
                                        showSuccessDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Text("لغو رزرو ❌", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStationReservations) {
        AlertDialog(
            onDismissRequest = { showStationReservations = false },
            title = { Text("رزروهای این جایگاه") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stationReservationRows.isEmpty()) Text("رزرو فعالی برای نمایش وجود ندارد.")
                    stationReservationRows.forEach { row ->
                        val start = runCatching { java.time.Instant.parse(row.optString("start_time")).toEpochMilli() }.getOrDefault(0L)
                        val end = runCatching { java.time.Instant.parse(row.optString("end_time")).toEpochMilli() }.getOrDefault(0L)
                        Text("${JalaliCalendarHelper.formatJalaliDateTime(start)} تا ${JalaliCalendarHelper.formatJalaliDateTime(end)}\nمدت: ${row.optInt("duration_minutes")} دقیقه | وضعیت: ${row.optString("status")}", fontSize = 10.sp)
                    }
                }
            },
            confirmButton = { Button(onClick = { showStationReservations = false }) { Text("بستن") } }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text(if (successMessage.contains("انجام نشد")) "ثبت رزرو" else "درخواست رزرو", fontWeight = FontWeight.Bold) },
            text = { Text(successMessage.ifBlank { "وضعیت درخواست رزرو از سرور دریافت نشد." }) },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false }) {
                    Text("متوجه شدم")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// CUSTOMER CLUB RULES TAB (GN & LP POLICIES)
// -------------------------------------------------------------
@Composable
fun CustomerClubRulesTab(viewModel: GameNetViewModel) {
    val lpTomanRate by viewModel.lpTomanRate.collectAsState()
    val minInviteSpendAmount by viewModel.minInviteSpendAmount.collectAsState()
    val scoringRules by viewModel.scoringRules.collectAsState()
    val behaviorRules by viewModel.allBehaviorRules.collectAsState()
    val clubLevels by viewModel.clubLevels.collectAsState()
    val transferFeePercent by viewModel.transferFeePercent.collectAsState()
    val transferDailyLimitGn by viewModel.transferDailyLimitGn.collectAsState()

    val decimalFormat = remember { DecimalFormat("#,###") }
    var expandedSection by remember { mutableStateOf<Int?>(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner Header
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        text = "راهنما و قوانین امتیازات گیم‌نکسا (GN & LP)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "مرجع کامل قوانین دریافت سکه GN، امتیاز LP، سطوح باشگاه و قوانین انضباطی سالن",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Section 1: قوانین و نرخ اعطای امتیاز LP وفاداری
        ExpandableRuleSection(
            title = "1. قوانین و نرخ اعطای امتیاز LP وفاداری",
            icon = Icons.Default.Stars,
            iconColor = Color(0xFFF57C00),
            isExpanded = expandedSection == 1,
            onExpand = { expandedSection = if (expandedSection == 1) null else 1 }
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFF3E0),
                border = BorderStroke(1.dp, Color(0xFFFFB74D)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "به ازای هر ${decimalFormat.format(lpTomanRate)} تومان پرداخت در گیم‌نت ⬅️ 1 امتیاز LP تعلق می‌گیرد.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    if (minInviteSpendAmount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "شرط فعال‌سازی پاداش دعوت: حداقل ${decimalFormat.format(minInviteSpendAmount)} تومان هزینه توسط دوست دعوت‌شده.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: سطوح باشگاه مشتریان بر اساس LP
        ExpandableRuleSection(
            title = "2. سطوح باشگاه مشتریان بر اساس LP",
            icon = Icons.Default.WorkspacePremium,
            iconColor = Color(0xFF1E88E5),
            isExpanded = expandedSection == 2,
            onExpand = { expandedSection = if (expandedSection == 2) null else 2 }
        ) {
            if (clubLevels.isEmpty()) {
                Text("در حال بارگذاری سطوح باشگاه...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    clubLevels.forEach { level ->
                        val badgeColor = when (level.name.lowercase()) {
                            "برنزی", "bronze" -> Color(0xFFCD7F32)
                            "نقره‌ای", "نقره ای", "silver" -> Color(0xFF9E9E9E)
                            "طلایی", "gold" -> Color(0xFFFFD700)
                            "الماسی", "diamond" -> Color(0xFF00BCD4)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "سطح ${level.name}", fontWeight = FontWeight.Bold, color = badgeColor, fontSize = 14.sp)
                                    }
                                    Text(text = "نیازمند: ${decimalFormat.format(level.requiredPoints)} LP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Text("🎯 ضریب پاداش سکه GN: ${level.gameGnPercent}x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("👥 پاداش دعوت दोस्त: ${level.inviteGnReward.toInt()} GN", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("💳 سقف پرداخت فاکتور با GN: ${level.maxGnPaymentPercent.toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: نحوه دریافت سکه‌های GN
        ExpandableRuleSection(
            title = "3. نحوه دریافت سکه‌های GN",
            icon = Icons.Default.MonetizationOn,
            iconColor = Color(0xFFFF9800),
            isExpanded = expandedSection == 3,
            onExpand = { expandedSection = if (expandedSection == 3) null else 3 }
        ) {
            if (scoringRules.isEmpty()) {
                Text("قانونی برای دریافت سکه ثبت نشده است.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scoringRules.forEach { rule ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = rule.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "+${decimalFormat.format(rule.points)} GN",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Section 4: قوانین تشویقی و انضباطی سالن گیم‌نت
        ExpandableRuleSection(
            title = "4. قوانین تشویقی و انضباطی سالن گیم‌نت",
            icon = Icons.Default.Gavel,
            iconColor = Color(0xFF9C27B0),
            isExpanded = expandedSection == 4,
            onExpand = { expandedSection = if (expandedSection == 4) null else 4 }
        ) {
            if (behaviorRules.isEmpty()) {
                Text("قوانین انضباطی ثبت نشده است.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    behaviorRules.forEach { rule ->
                        val isPositive = rule.lpChange >= 0
                        val impactColor = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (isPositive) Icons.Default.ThumbUp else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = impactColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = rule.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = impactColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "${if (isPositive) "+" else ""}${rule.lpChange} LP",
                                    fontWeight = FontWeight.Bold,
                                    color = impactColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ExpandableRuleSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isExpanded) iconColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onExpand() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    content()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
