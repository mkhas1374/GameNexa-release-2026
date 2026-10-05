@file:OptIn(ExperimentalFoundationApi::class)

package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.example.data.Customer
import com.example.data.CustomerTransaction
import com.example.data.Reservation
import com.example.data.network.SelfHostedManager
import com.example.util.JalaliCalendarHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersReservationsScreen(viewModel: GameNetViewModel) {
    val lang by viewModel.language.collectAsState()
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    val isTrialActive by viewModel.isTrialModeFlow.collectAsState()
    
    val customers by viewModel.customers.collectAsState()
    val reservations by viewModel.reservations.collectAsState()
    val customerTransactions by viewModel.customerTransactions.collectAsState()

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Customers, 1: Reservations

    var showCustomerDialog by remember { mutableStateOf(false) }
    var showReservationDialog by remember { mutableStateOf(false) }
    var managerStations by remember { mutableStateOf<List<Pair<Long,String>>>(emptyList()) }
    var showTrialLimitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showReservationDialog) {
        if (showReservationDialog) {
            val arr = SelfHostedManager.fetchManagerStations()
            managerStations = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val s = arr.optJSONObject(i) ?: continue
                        val id = s.optLong("id", 0L)
                        if (id > 0L && s.optBoolean("active", true) && s.optBoolean("reservable", true)) {
                            add(id to s.optString("name").ifBlank { "ایستگاه $id" })
                        }
                    }
                }
            }
        }
    }

    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var balanceConfirmData by remember { mutableStateOf<Triple<String, Double, Double>?>(null) }
    var showSettlementDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showTrialLimitDialog) {
        AlertDialog(
            onDismissRequest = { showTrialLimitDialog = false },
            title = {
                Text(
                    text = if (lang == "fa") "محدودیت نسخه آزمایشی 24 ساعته" else "24H Trial Limitation",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (lang == "fa")
                        "در نسخه تست 24 ساعته، امکانات مدیریت مخاطبین و رزروها محدود به 4 مخاطب لوکال تستی است و امکان تغییر یا ثبت داده جدید وجود ندارد. لطفاً جهت استفاده نامحدود از تمام امکانات، اشتراک تهیه نمایید."
                    else
                        "In 24-hour trial mode, customer management and reservations are restricted to 4 local test contacts. Please purchase a subscription for full unlimited access."
                )
            },
            confirmButton = {
                Button(onClick = { showTrialLimitDialog = false }) {
                    Text(if (lang == "fa") "متوجه شدم" else "Understood")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (lang == "fa")
                            "نسخه تست 24 ساعته: دسترسی فقط محدود به 4 مخاطب لوکال تستی جهت بررسی سیستم می‌باشد."
                        else
                            "24H Trial Mode: Access is restricted to 4 local test contacts for evaluation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        // Top Banner / Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Localization.get("customers_and_reservations", lang),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == "fa") "مدیریت حساب مشتریان و نوبت‌های رزرو" else "Manage customer balances & upcoming slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedSubTab == 0 && currentAdminRole != "VIEWER") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                                showTrialLimitDialog = true
                            } else {
                                editingCustomer = null
                                showCustomerDialog = true
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (lang == "fa") "مشتری" else "Customer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { showSettlementDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (lang == "fa") "تسویه سالن" else "Hall Settlement", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sub Tab Selector
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = Localization.get("customers_tab", lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                modifier = Modifier.testTag("tab_customers")
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            text = Localization.get("reservations_tab", lang),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                modifier = Modifier.testTag("tab_reservations")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedSubTab == 0) {
                // Customers List Pane
                CustomersTabContent(
                    customers = customers,
                    archivedCustomers = viewModel.archivedCustomers.collectAsState().value,
                    transactions = customerTransactions,
                    lang = lang,
                    canDelete = (currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER") && !isTrialActive && currentAdminRole != "TRIAL_USER",
                    onEditCustomer = {
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            editingCustomer = it
                            showCustomerDialog = true
                        }
                    },
                    onDeleteCustomer = {
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            viewModel.deleteCustomer(it)
                        }
                    },
                    onRestoreArchivedCustomer = { customer ->
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            viewModel.restoreArchivedCustomer(customer)
                        }
                    },
                    onPurgeArchivedCustomer = { customer ->
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            viewModel.purgeArchivedCustomer(customer)
                        }
                    },
                    onAddClick = {
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            editingCustomer = null
                            showCustomerDialog = true
                        }
                    },
                    onUpdateTransactionStatus = { trans, status ->
                        viewModel.updateCustomerTransactionStatus(trans, status)
                    },
                    onUpdateTransactionPayment = { trans, paidAmount, status ->
                        viewModel.updateCustomerTransactionPayment(trans, paidAmount.toLong(), status)
                    },
                    onDeleteTransaction = { trans ->
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            viewModel.deleteCustomerTransaction(trans)
                        }
                    },
                    viewModel = viewModel
                )
            } else {
                // Reservations List Pane
                ReservationsTabContent(
                    reservations = reservations,
                    lang = lang,
                    canDelete = (currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER") && !isTrialActive && currentAdminRole != "TRIAL_USER",
                    onDeleteReservation = {
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            viewModel.deleteReservation(it)
                        }
                    },
                    onAddClick = {
                        if (isTrialActive || currentAdminRole == "TRIAL_USER") {
                            showTrialLimitDialog = true
                        } else {
                            showReservationDialog = true
                        }
                    }
                )
            }
            }
        }

    // Customer Add/Edit Dialog
    


    if (showCustomerDialog) {
        CustomerFormDialog(
            customer = editingCustomer,
            lang = lang,
            viewModel = viewModel,
            onDismiss = { showCustomerDialog = false },
            onSave = { name, phone, debt, credit, desc, invitedByCode, manualPoints, password ->
                if (editingCustomer != null) {
                    // Update preserving the original ID
                    viewModel.addCustomer(name, phone, debt.toLong(), credit.toLong(), desc, invitedByCode, editingCustomer!!.id, manualPoints.toLong(), password)
                } else {
                    // Create
                    viewModel.addCustomer(name, phone, debt.toLong(), credit.toLong(), desc, invitedByCode, 0L, manualPoints.toLong(), password)
                }
                showCustomerDialog = false
                Toast.makeText(context, Localization.get("customer_saved", lang), Toast.LENGTH_SHORT).show()
                if (debt > 0 || credit > 0) {
                    balanceConfirmData = Triple(name, debt, credit)
                }
            }
        )
    }

    // Reservation Dialog
    if (showReservationDialog) {
        ReservationFormDialog(
            customers = customers,
            stations = managerStations,
            lang = lang,
            onDismiss = { showReservationDialog = false },
            onSave = { name, phone, timestamp, duration, stationId, isVip, paidAmount ->
                viewModel.addReservation(name, phone, timestamp, duration, stationId, isVip, paidAmount)
                showReservationDialog = false
                Toast.makeText(context, Localization.get("reservation_saved", lang), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSettlementDialog) {
        SalonSettlementDialog(
            transactions = customerTransactions,
            lang = lang,
            onDismiss = { showSettlementDialog = false },
            onSettleAll = {
                viewModel.archiveAllReviewedTransactions()
                showSettlementDialog = false
                Toast.makeText(context, if (lang == "fa") "تسویه سالن با موفقیت ثبت و گزارش لاگ ایجاد شد." else "Hall settlement completed and log recorded.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Customer Balance Confirmation Dialog
    balanceConfirmData?.let { data ->
        val (name, debt, credit) = data
        AlertDialog(
            onDismissRequest = { balanceConfirmData = null },
            title = {
                Text(
                    text = if (lang == "fa") "خلاصه وضعیت مالی مشتری" else "Customer Balance Summary",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (lang == "fa") "حساب مشتری با جزئیات مالی زیر با موفقیت ثبت شد:" else "Customer account registered successfully with following balance:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (lang == "fa") "نام مشتری:" else "Customer:", fontWeight = FontWeight.Bold)
                                Text(name, fontWeight = FontWeight.Bold)
                            }
                            
                            if (debt > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == "fa") "میزان بدهکاری:" else "Debt Amount:",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%,d", debt)} " + (if (lang == "fa") "تومان" else "Toman"),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                            
                            if (credit > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == "fa") "میزان طلبکاری:" else "Credit Amount:",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%,d", credit)} " + (if (lang == "fa") "تومان" else "Toman"),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { balanceConfirmData = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (lang == "fa") "متوجه شدم و تایید" else "Got it, OK")
                }
            }
        )
    }
}

@Composable
fun CustomersTabContent(
    customers: List<Customer>,
    archivedCustomers: List<Customer>,
    transactions: List<CustomerTransaction>,
    lang: String,
    canDelete: Boolean,
    onEditCustomer: (Customer) -> Unit,
    onDeleteCustomer: (Customer) -> Unit,
    onRestoreArchivedCustomer: (Customer) -> Unit,
    onPurgeArchivedCustomer: (Customer) -> Unit,
    onAddClick: () -> Unit,
    onUpdateTransactionStatus: (CustomerTransaction, String) -> Unit,
    onUpdateTransactionPayment: (CustomerTransaction, Double, String) -> Unit,
    onDeleteTransaction: (CustomerTransaction) -> Unit,
    viewModel: GameNetViewModel
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Customers, 1: Unreviewed, 2: Reviewed, 3: Debtors
    var searchQuery by remember { mutableStateOf("") }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedCustomerIds = remember { mutableStateListOf<Long>() }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val unreviewedTrans by viewModel.unreviewedTransactions.collectAsState()
    val reviewedTrans by viewModel.reviewedTransactions.collectAsState()
    val debtorTrans by viewModel.debtorTransactions.collectAsState()
    val debtorCustomerCount by remember(debtorTrans) { derivedStateOf { debtorTrans.map { it.customerId }.distinct().size } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section Filter Chips
        ScrollableTabRow(
            selectedTabIndex = selectedSection,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text(if (lang == "fa") "📋 مخاطبان (${customers.size})" else "📋 Contacts (${customers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text(if (lang == "fa") "⌛ بررسی نشده (${unreviewedTrans.size})" else "⌛ Pending (${unreviewedTrans.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSection == 2,
                onClick = { selectedSection = 2 },
                text = { Text(if (lang == "fa") "✅ تسویه شده (${reviewedTrans.size})" else "✅ Settled (${reviewedTrans.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSection == 3,
                onClick = { selectedSection = 3 },
                text = { Text(if (lang == "fa") "🚨 بدهکاران ($debtorCustomerCount)" else "🚨 Debtors ($debtorCustomerCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
        }

        // Search Bar for active section
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(if (lang == "fa") "جستجو..." else "Search...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        when (selectedSection) {
            0 -> {
                // CUSTOMERS SECTION
                val filteredCustomers = remember(customers, searchQuery) {
                    if (searchQuery.isBlank()) customers
                    else customers.filter {
                        it.fullName.contains(searchQuery, ignoreCase = true) ||
                                it.phoneNumber.contains(searchQuery)
                    }
                }

                // Top action bar for multi-selection or toggle button
                if (isMultiSelectMode) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        isMultiSelectMode = false
                                        selectedCustomerIds.clear()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close multi-select", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Text(
                                    text = if (lang == "fa") "${selectedCustomerIds.size} مخاطب انتخاب شده" else "${selectedCustomerIds.size} contacts selected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val allSelected = filteredCustomers.isNotEmpty() && selectedCustomerIds.size == filteredCustomers.size
                                TextButton(
                                    onClick = {
                                        if (allSelected) {
                                            selectedCustomerIds.clear()
                                        } else {
                                            selectedCustomerIds.clear()
                                            selectedCustomerIds.addAll(filteredCustomers.map { it.id })
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (allSelected) (if (lang == "fa") "لغو همه" else "Deselect All") else (if (lang == "fa") "انتخاب همه" else "Select All"),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                
                                if (selectedCustomerIds.size == 1) {
                                    IconButton(
                                        onClick = {
                                            val idToEdit = selectedCustomerIds.first()
                                            val cToEdit = filteredCustomers.find { it.id == idToEdit }
                                            if (cToEdit != null) {
                                                onEditCustomer(cToEdit)
                                                isMultiSelectMode = false
                                                selectedCustomerIds.clear()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
IconButton(
                                    onClick = {
                                        if (selectedCustomerIds.isNotEmpty()) {
                                            showBatchDeleteConfirm = true
                                        }
                                    },
                                    enabled = selectedCustomerIds.isNotEmpty(),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Batch Delete",
                                        tint = if (selectedCustomerIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                } else if (canDelete && filteredCustomers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == "fa") "فهرست مخاطبان (${filteredCustomers.size} نفر)" else "Contacts List (${filteredCustomers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                isMultiSelectMode = true
                            },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (lang == "fa") "انتخاب گروهی" else "Multi Select", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCustomers, key = { it.id }) { customer ->
                        val customerTrans = remember(transactions, customer.id) {
                            transactions.filter { it.customerId == customer.id }
                        }
                        CustomerCard(
                            customer = customer,
                            lang = lang,
                            customerTransactions = customerTrans,
                            onEdit = { 
                                onEditCustomer(customer)
                                
                            },
                            onDelete = { onDeleteCustomer(customer) },
                            viewModel = viewModel,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedCustomerIds.contains(customer.id),
                            onSelectToggle = {
                                if (selectedCustomerIds.contains(customer.id)) {
                                    selectedCustomerIds.remove(customer.id)
                                } else {
                                    selectedCustomerIds.add(customer.id)
                                }
                            },
                            onLongClick = {
                                isMultiSelectMode = true
                                selectedCustomerIds.add(customer.id)
                            }
                        )
                    }
                    if (archivedCustomers.isNotEmpty()) {
                        item {
                            Text(
                                text = if (lang == "fa") "مخاطبان آرشیو شده (" + archivedCustomers.size + ")" else "Archived contacts (" + archivedCustomers.size + ")",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(archivedCustomers, key = { "archived_" + it.id }) { customer ->
                            val customerTrans = remember(transactions, customer.id) {
                                transactions.filter { it.customerId == customer.id }
                            }
                            var showPurgeConfirm by remember(customer.id) { mutableStateOf(false) }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(customer.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                customer.phoneNumber + " • " + customerTrans.size + " " + if (lang == "fa") "فاکتور" else "invoices",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            IconButton(
                                                onClick = { onRestoreArchivedCustomer(customer) },
                                                enabled = canDelete,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Text("⤴️", fontSize = 16.sp)
                                            }
                                            IconButton(
                                                onClick = { showPurgeConfirm = true },
                                                enabled = canDelete,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = if (lang == "fa") "حذف کامل" else "Purge", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                                            }
                                        }
                                    }
                                    Text(
                                        if (lang == "fa") "آرشیو شده — حذف کامل غیرقابل‌بازگشت است" else "Archived — permanent deletion is irreversible",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (showPurgeConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showPurgeConfirm = false },
                                    title = { Text(if (lang == "fa") "حذف کامل مشتری" else "Permanently delete customer") },
                                    text = {
                                        Text(
                                            if (lang == "fa")
                                                "این عملیات مشتری و تمام سوابق مرتبط او را از سرور و دستگاه حذف می‌کند و قابل بازگشت نیست. ادامه می‌دهید؟"
                                            else
                                                "This permanently removes the customer and all related records from the server and device. This cannot be undone. Continue?"
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showPurgeConfirm = false
                                                onPurgeArchivedCustomer(customer)
                                            }
                                        ) {
                                            Text(if (lang == "fa") "بله، حذف کامل" else "Permanently delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showPurgeConfirm = false }) {
                                            Text(if (lang == "fa") "انصراف" else "Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (showBatchDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showBatchDeleteConfirm = false },
                        title = { Text(if (lang == "fa") "حذف جمعی مخاطبین" else "Batch Delete") },
                        text = { Text(if (lang == "fa") "آیا از حذف ${selectedCustomerIds.size} مخاطب انتخاب شده اطمینان دارید؟" else "Are you sure you want to delete ${selectedCustomerIds.size} selected customers?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val customersToDelete = filteredCustomers.filter { selectedCustomerIds.contains(it.id) }
                                    viewModel.deleteCustomersBatch(customersToDelete)
                                    selectedCustomerIds.clear()
                                    isMultiSelectMode = false
                                    showBatchDeleteConfirm = false
                                }
                            ) {
                                Text(Localization.get("delete", lang), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBatchDeleteConfirm = false }) {
                                Text(Localization.get("cancel", lang))
                            }
                        }
                    )
                }
            }
            1 -> {
                // UNREVIEWED SECTION (Flat List)
                val filteredTrans = remember(unreviewedTrans, searchQuery) {
                    if (searchQuery.isBlank()) unreviewedTrans
                    else unreviewedTrans.filter {
                        it.customerName.contains(searchQuery, ignoreCase = true) ||
                                it.stationName.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (filteredTrans.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "fa") "صورت‌حسابی در این بخش وجود ندارد" else "No invoices in this section",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredTrans, key = { index, trans -> "unreviewed_${trans.id}_${trans.timestamp}_$index" }) { _, trans ->
                            CustomerTransactionCard(
                                transaction = trans,
                                lang = lang,
                                isAlwaysExpanded = true,
                                onUpdateStatus = { st -> onUpdateTransactionStatus(trans, st) },
                                onUpdatePayment = { pAmount, st -> onUpdateTransactionPayment(trans, pAmount, st) },
                                onDelete = { onDeleteTransaction(trans) },
                                canDelete = canDelete,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
            2 -> {
                // REVIEWED SECTION: group by customer; newest invoice first inside each customer.
                val groupedReviewed = remember(reviewedTrans) {
                    reviewedTrans.groupBy { it.customerId }
                        .mapValues { (_, transactions) -> transactions.sortedByDescending { it.timestamp } }
                }
                val sortedReviewedGroups = remember(groupedReviewed) {
                    groupedReviewed.toList().sortedByDescending { (_, transactions) ->
                        transactions.firstOrNull()?.timestamp ?: 0L
                    }
                }
                val filteredReviewedGroups = remember(sortedReviewedGroups, searchQuery) {
                    if (searchQuery.isBlank()) sortedReviewedGroups
                    else sortedReviewedGroups.filter { (_, transactions) ->
                        transactions.any {
                            it.customerName.contains(searchQuery, ignoreCase = true) ||
                            it.stationName.contains(searchQuery, ignoreCase = true) ||
                            it.title.contains(searchQuery, ignoreCase = true)
                        }
                    }
                }

                if (filteredReviewedGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "fa") "صورت‌حسابی در این بخش وجود ندارد" else "No invoices in this section",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredReviewedGroups, key = { _, group -> "reviewed_customer_${group.first}" }) { _, (_, transactions) ->
                            val customerName = transactions.firstOrNull()?.customerName ?: ""
                            val totalPaid = transactions.sumOf { it.paidAmount }
                            ReviewedGroupCard(
                                customerName = customerName,
                                totalPaid = totalPaid.toDouble(),
                                transactions = transactions,
                                lang = lang,
                                onUpdateStatus = { trans, st -> onUpdateTransactionStatus(trans, st) },
                                onUpdatePayment = { trans, pAmount, st -> onUpdateTransactionPayment(trans, pAmount, st) },
                                onDelete = { trans -> onDeleteTransaction(trans) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
            3 -> {
                // DEBTORS SECTION (GROUPED)
                val groupedDebtors = remember(debtorTrans) {
                    debtorTrans.groupBy { it.customerId }
                }
                val sortedDebtors = remember(groupedDebtors) {
                    groupedDebtors.toList().sortedByDescending { (_, transList) ->
                        transList.sumOf { it.amount - it.paidAmount }
                    }
                }
                val filteredDebtors = remember(sortedDebtors, searchQuery) {
                    if (searchQuery.isBlank()) sortedDebtors
                    else sortedDebtors.filter { (_, transList) ->
                        transList.any { it.customerName.contains(searchQuery, ignoreCase = true) }
                    }
                }

                if (filteredDebtors.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lang == "fa") "بدهکاری وجود ندارد" else "No debtors found",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(filteredDebtors, key = { _, group -> "debtor_${group.first}" }) { _, (_, transList) ->
                            val customerName = transList.first().customerName
                            val sortedCustomerTransactions = transList.sortedByDescending { it.timestamp }
                            val totalDebt = sortedCustomerTransactions.sumOf { it.amount - it.paidAmount }
                            DebtorGroupCard(
                                customerName = customerName,
                                totalDebt = totalDebt,
                                transactions = sortedCustomerTransactions,
                                lang = lang,
                                onUpdateStatus = { trans, st -> onUpdateTransactionStatus(trans, st) },
                                onUpdatePayment = { trans, pAmount, st -> onUpdateTransactionPayment(trans, pAmount, st) },
                                onDelete = { trans -> onDeleteTransaction(trans) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerTransactionCard(
    transaction: CustomerTransaction,
    lang: String,
    isAlwaysExpanded: Boolean = false,
    onUpdateStatus: (String) -> Unit,
    onUpdatePayment: (Double, String) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
    viewModel: GameNetViewModel
) {
    
    var expanded by remember { mutableStateOf(isAlwaysExpanded) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPrepaymentDialog by remember { mutableStateOf(false) }
    var settlementReview by remember { mutableStateOf<com.example.data.network.SettlementReview?>(null) }
    var prepaymentAllocations by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var refundMethods by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var reviewStatuses by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var refundRequested by remember { mutableStateOf(false) }
    var isSubmitting by remember(transaction) { mutableStateOf(false) }
    var isPayFieldFocused by remember { mutableStateOf(false) }
    val payFieldBringIntoViewRequester = remember { BringIntoViewRequester() }

    val customers by viewModel.customers.collectAsState()
    val clubLevels by viewModel.clubLevels.collectAsState()
    val gameRewardRate by viewModel.gameRewardRate.collectAsState()
    val buffetRewardRate by viewModel.buffetRewardRate.collectAsState()
    val lpTomanRate by viewModel.lpTomanRate.collectAsState()

    val customer = remember(customers, transaction.customerId) {
        customers.find { it.id == transaction.customerId }
    }
    val custPoints = customer?.points ?: 0L
    val sortedLevels = remember(clubLevels) { clubLevels.sortedByDescending { it.requiredPoints } }
    val activeLevel = remember(sortedLevels, custPoints) {
        sortedLevels.find { custPoints >= it.requiredPoints } ?: clubLevels.firstOrNull()
    }

    LaunchedEffect(isPayFieldFocused) {
        if (isPayFieldFocused) {
            kotlinx.coroutines.delay(180)
            payFieldBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(showPrepaymentDialog, transaction.sessionId) {
        if (showPrepaymentDialog && transaction.sessionId.isNotBlank()) {
            settlementReview = viewModel.fetchSettlementReview(transaction.sessionId)
            prepaymentAllocations = emptyMap()
        }
    }

    LaunchedEffect(refundRequested) {
        if (refundRequested) {
            refundRequested = false
            val review = settlementReview
            if (review != null) {
                val decisions = org.json.JSONArray()
                var invalid = false
                review.payers.forEach { payer ->
                    val refund = prepaymentAllocations[payer.customerId].orEmpty().filter { it.isDigit() }.toLongOrNull() ?: 0L
                    val method = refundMethods[payer.customerId].orEmpty().ifBlank { if (refund > 0L) { if (payer.customerId == 0L) "CASH" else "WALLET" } else "NONE" }
                    val status = reviewStatuses[payer.customerId].orEmpty().ifBlank { "REVIEWED" }
                    if (refund > payer.unusedPrepayment) invalid = true
                    if (refund > 0L && method !in setOf("WALLET", "CASH")) invalid = true
                    decisions.put(org.json.JSONObject().apply {
                        put("customerId", payer.customerId)
                        put("refundAmount", refund)
                        put("refundMethod", method)
                        put("status", status)
                        put("paidAmount", if (status == "REVIEWED") payer.invoiceTotal else 0L)
                    })
                }
                if (invalid) {
                    android.widget.Toast.makeText(viewModel.getApplication(), "مقدار بازگشت یا روش پرداخت برای یکی از مشتریان معتبر نیست.", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    val ok = viewModel.finalizeSettlementReview(transaction.sessionId, (0 until decisions.length()).map { decisions.getJSONObject(it) })
                    if (ok) {
                        settlementReview = viewModel.fetchSettlementReview(transaction.sessionId)
                        prepaymentAllocations = emptyMap()
                        refundMethods = emptyMap()
                        reviewStatuses = emptyMap()
                        showPrepaymentDialog = false
                        android.widget.Toast.makeText(viewModel.getApplication(), "تعیین تکلیف نشست با موفقیت ثبت شد؛ GN و LP قطعی شدند.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(viewModel.getApplication(), "تعیین تکلیف با سرور ثبت نشد؛ اتصال را بررسی و دوباره تلاش کنید.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val gameDiscPct: Double = (activeLevel?.gameDiscountPercent ?: 0L).toDouble()
    val buffetDiscPct: Double = (activeLevel?.buffetDiscountPercent ?: 0L).toDouble()
    val fixedDiscTom: Double = (activeLevel?.fixedDiscountToman ?: 0L).toDouble()

    val origGameCost: Double = if (transaction.gameCost > 0L) transaction.gameCost.toDouble() else (transaction.amount - transaction.foodCost).toDouble()
    val origFoodCost: Double = transaction.foodCost.toDouble()
    val origTotal: Double = if (transaction.amount > 0L) transaction.amount.toDouble() else (origGameCost + origFoodCost)
    val previewGn = ((origGameCost.toLong() / 100_000L) * gameRewardRate + (origFoodCost.toLong() / 100_000L) * buffetRewardRate).coerceAtLeast(0L)
    val previewLp = if (lpTomanRate > 0L) (origTotal.toLong() / lpTomanRate).coerceAtLeast(0L) else 0L

    val gameDiscount: Double = origGameCost * (gameDiscPct / 100.0)
    val discountedGameCost: Double = (origGameCost - gameDiscount).coerceAtLeast(0.0)

    val foodDiscount: Double = origFoodCost * (buffetDiscPct / 100.0)
    val discountedFoodCost: Double = (origFoodCost - foodDiscount).coerceAtLeast(0.0)

    val finalAmount: Double = ((discountedGameCost + discountedFoodCost) - fixedDiscTom).coerceAtLeast(0.0)
    val hasDiscount: Boolean = gameDiscPct > 0.0 || buffetDiscPct > 0.0 || fixedDiscTom > 0.0

    var payInput by remember { mutableStateOf(transaction.paidAmount.takeIf { it > 0 }?.toInt()?.toString() ?: "") }
    var showSaveGuestDialog by remember { mutableStateOf(false) }
    val isGuest = remember(transaction.customerName, transaction.customerId) {
        transaction.customerName.contains("مهمان", ignoreCase = true) || transaction.customerId <= 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isAlwaysExpanded) Modifier.clickable { expanded = !expanded }
                else Modifier
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (transaction.status) {
                "UNREVIEWED" -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                "DEBTOR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            when (transaction.status) {
                "UNREVIEWED" -> MaterialTheme.colorScheme.tertiary
                "DEBTOR" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        text = transaction.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (isGuest) {
                        Surface(
                            onClick = { showSaveGuestDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "ثبت مخاطب +",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isAlwaysExpanded) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (hasDiscount) {
                                Text(
                                    text = "%,.0f".format(Locale.US, origTotal),
                                    style = TextStyle(textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = "%,.0f تومان".format(Locale.US, if (hasDiscount) finalAmount else origTotal),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (transaction.status == "DEBTOR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "%,.0f تومان".format(Locale.US, if (hasDiscount) finalAmount else origTotal),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (transaction.status == "DEBTOR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${transaction.dateStr} | ${transaction.timeStr}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded || isAlwaysExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    val conciseStation = formatConciseTitle(transaction.stationName)
                    val conciseTitle = formatConciseTitle(transaction.title)
                    val cleanStation = conciseStation.replace("🛋️", "").replace("🎮", "").replace("ایستگاه", "", ignoreCase = true).replace("جایگاه", "", ignoreCase = true).trim()
                    val cleanTitle = conciseTitle.replace("🛋️", "").replace("🎮", "").trim()
                    val stationDisplay = if (cleanStation.isNotBlank()) "🛋️ $cleanStation" else "🛋️ دستگاه"
                    val controllerDisplay = if (cleanTitle.isNotBlank()) "🎮 $cleanTitle" else "🎮 دسته"
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Header: Game cost
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\u200F$stationDisplay | $controllerDisplay", style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "%,.0f تومان".format(java.util.Locale.US, origGameCost),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Body: Buffet items
                            val buffetItems = transaction.buffetDetails.split(Regex("(?<=\\))\\s*,\\s*|\\n")).map { it.trim() }.filter { it.isNotBlank() }
                            if (buffetItems.isNotEmpty() || origFoodCost > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                buffetItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "\u200F• $item", style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                                            fontSize = 11.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "جمع بوفه:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "%,.0f تومان".format(java.util.Locale.US, origFoodCost),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            // Discounts (if any)
                            if (hasDiscount) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                if (gameDiscPct > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("✨ تخفیف بازی (%$gameDiscPct):", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        Text("- %,.0f تومان".format(java.util.Locale.US, gameDiscount), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (buffetDiscPct > 0 && origFoodCost > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("✨ تخفیف بوفه (%$buffetDiscPct):", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        Text("- %,.0f تومان".format(java.util.Locale.US, foodDiscount), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                                if (fixedDiscTom > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("✨ تخفیف ثابت:", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                        Text("- %,.0f تومان".format(java.util.Locale.US, fixedDiscTom), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                }
                            }

                            // Footer: Date & Status & Total
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${transaction.dateStr} - ${transaction.timeStr}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (transaction.status == "REVIEWED") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.2f),
                                            border = BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color(0xFF10B981))
                                        ) {
                                            Text(
                                                text = "تسویه شده ✅",
                                                fontSize = 9.sp,
                                                color = androidx.compose.ui.graphics.Color(0xFF047857),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "جمع کل:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "%,.0f تومان".format(java.util.Locale.US, if (hasDiscount) finalAmount else origTotal),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val activeTotal = if (hasDiscount) finalAmount else origTotal
            val remainingDebt = (activeTotal - transaction.paidAmount.toDouble()).coerceAtLeast(0.0)
            if (transaction.status == "DEBTOR") {
                Text(
                    text = "بدهی مانده: %,.0f تومان".format(Locale.US, remainingDebt),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val earnedGnPoints = transaction.earnedGn
            val earnedLpPoints = transaction.earnedLp

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
                                Text(if (transaction.status == "UNREVIEWED") "GN قابل دریافت این نشست:" else "پاداش GN دریافتی این نشست:", fontSize = 11.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                            }
                            Text("+${if (transaction.status == "UNREVIEWED") previewGn else earnedGnPoints} GN", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D4ED8))
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🏆", fontSize = 11.sp)
                                Text(if (transaction.status == "UNREVIEWED") "LP قابل دریافت این نشست:" else "امتیاز LP دریافتی این نشست:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("+${if (transaction.status == "UNREVIEWED") previewLp else earnedLpPoints} LP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    if (transaction.status == "UNREVIEWED" && transaction.sessionId.isNotBlank()) {
                        OutlinedButton(
                            onClick = { showPrepaymentDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💳 تعیین تکلیف و بازگشت مانده پرداخت اولیه")
                        }
                    }

                    if (showPrepaymentDialog) {
                        AlertDialog(
                            onDismissRequest = { showPrepaymentDialog = false },
                            title = { Text("تعیین تکلیف فاکتور و پرداخت اولیه نشست") },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val review = settlementReview
                                    if (review == null) {
                                        Text("در حال دریافت اطلاعات نشست از سرور...")
                                    } else {
                                        Text("پرداخت اولیه کل: ${String.format(Locale.US, "%,d", review.totalPrepayment)} تومان", fontWeight = FontWeight.Bold)
                                        Text("هزینه بازی این نشست: ${String.format(Locale.US, "%,d", review.gameCost)} تومان")
                                        Text("هزینه بوفه این نشست: ${String.format(Locale.US, "%,d", review.buffetCost)} تومان")
                                        HorizontalDivider()
                                        review.payers.forEach { payer ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(payer.name.ifBlank { "مشتری ${payer.customerId}" }, fontWeight = FontWeight.Bold)
                                                    Text("پرداخت اولیه: ${String.format(Locale.US, "%,d", payer.prepaymentAmount)} تومان")
                                                    Text("هزینه بازی: ${String.format(Locale.US, "%,d", payer.gameCost)} تومان")
                                                    Text("هزینه بوفه: ${String.format(Locale.US, "%,d", payer.buffetCost)} تومان")
                                                    Text("جمع فاکتور: ${String.format(Locale.US, "%,d", payer.invoiceTotal)} تومان", fontWeight = FontWeight.Bold)
                                                    Text("مانده قابل بازگشت: ${String.format(Locale.US, "%,d", payer.unusedPrepayment)} تومان", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    OutlinedTextField(
                                                        value = prepaymentAllocations[payer.customerId].orEmpty(),
                                                        onValueChange = { value -> prepaymentAllocations = prepaymentAllocations.toMutableMap().apply { put(payer.customerId, value.filter { it.isDigit() }) } },
                                                        label = { Text("مقدار بازگشت") },
                                                        singleLine = true,
                                                        supportingText = { Text("حداکثر: ${String.format(Locale.US, "%,d", payer.unusedPrepayment)} تومان") },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                        if (payer.customerId > 0L) FilterChip(
                                                            selected = refundMethods[payer.customerId] == "WALLET",
                                                            onClick = { refundMethods = refundMethods.toMutableMap().apply { put(payer.customerId, "WALLET") } },
                                                            label = { Text("ذخیره در کیف پول") },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        FilterChip(
                                                            selected = refundMethods[payer.customerId] == "CASH",
                                                            onClick = { refundMethods = refundMethods.toMutableMap().apply { put(payer.customerId, "CASH") } },
                                                            label = { Text("پرداخت نقدی به مشتری") },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                                        FilterChip(
                                                            selected = reviewStatuses[payer.customerId] != "DEBTOR",
                                                            onClick = { reviewStatuses = reviewStatuses.toMutableMap().apply { put(payer.customerId, "REVIEWED") } },
                                                            label = { Text("تسویه کامل") },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        FilterChip(
                                                            selected = reviewStatuses[payer.customerId] == "DEBTOR",
                                                            onClick = { reviewStatuses = reviewStatuses.toMutableMap().apply { put(payer.customerId, "DEBTOR") } },
                                                            label = { Text("بدهکار") },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Text("پس از انجام، فاکتور تعیین‌تکلیف می‌شود و GN/LP فقط همین مرحله قطعی خواهد شد.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { refundRequested = true }, enabled = settlementReview != null && !isSubmitting) { Text("انجام شد") }
                            },
                            dismissButton = { TextButton(onClick = { showPrepaymentDialog = false }) { Text("لغو") } }
                        )
                    }

                    // Share / Print Receipt Action Button
                    val context = LocalContext.current
                                            val shareAction = {
                                val receipt = buildString {
                                    appendLine("================================")
                                    appendLine("         گیم‌نکسا - فاکتور مشتری         ")
                                    appendLine("================================")
                                    appendLine("👤 مشتری: ${transaction.customerName}")
                                    appendLine("📅 تاریخ: ${transaction.dateStr} - ${transaction.timeStr}")
                                    appendLine("--------------------------------")
                                    appendLine("🎮 دستگاه: ${transaction.stationName}")
                                    appendLine("⏱ زمان بازی: ${transaction.title}")
                                    appendLine("💵 هزینه بازی: %,.0f تومان".format(java.util.Locale.US, origGameCost))
                                    appendLine("--------------------------------")
                                    if (origFoodCost > 0) {
                                        val items = transaction.buffetDetails.split(Regex("(?<=\\))\\s*,\\s*|\\n")).map { it.trim() }.filter { it.isNotBlank() }
                                        if (items.isNotEmpty()) {
                                            appendLine("🍔 سفارشات بوفه:")
                                            items.forEach { appendLine("   • $it") }
                                        }
                                        appendLine("💵 جمع بوفه: %,.0f تومان".format(java.util.Locale.US, origFoodCost))
                                        appendLine("--------------------------------")
                                    }
                                    if (hasDiscount) {
                                        appendLine("✨ تخفیف باشگاه: %,.0f تومان".format(java.util.Locale.US, (origTotal - finalAmount)))
                                        appendLine("--------------------------------")
                                    }
                                    appendLine("💰 جمع کل نهایی: %,.0f تومان".format(java.util.Locale.US, if (hasDiscount) finalAmount else origTotal))
                                    if (transaction.paidAmount > 0) {
                                        appendLine("💳 مبلغ پرداختی: %,d تومان".format(java.util.Locale.US, transaction.paidAmount))
                                    }
                                    if (remainingDebt > 0) {
                                        appendLine("⚠️ مانده بدهی: %,.0f تومان".format(java.util.Locale.US, remainingDebt))
                                    } else {
                                        appendLine("✅ وضعیت: تسویه کامل")
                                    }
                                    appendLine("💎 پاداش GN کسب شده: +$earnedGnPoints GN")
                                    appendLine("🏆 امتیاز LP کسب شده: +$earnedLpPoints LP")
                                    appendLine("================================")
                                    appendLine("    با تشکر از انتخاب و حضور شما!")
                                }
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "فاکتور گیم‌نت - ${transaction.customerName}")
                                    putExtra(android.content.Intent.EXTRA_TEXT, receipt)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "چاپ و اشتراک‌گذاری فاکتور"))
                            }

                        if (transaction.status == "UNREVIEWED" || transaction.status == "DEBTOR") {
                            val payableAmount = if (hasDiscount) finalAmount else origTotal
                            val parsedInput = payInput.toDoubleOrNull()
                            val isInputValid = parsedInput != null && parsedInput > 0

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isInputValid) {
                                    Button(
                                        onClick = {
                                            val paid = parsedInput!!
                                            if (paid >= payableAmount) {
                                                if (!isSubmitting) { isSubmitting = true; onUpdatePayment(payableAmount, "REVIEWED") }
                                            } else {
                                                if (!isSubmitting) { isSubmitting = true; onUpdatePayment(paid, "DEBTOR") }
                                            }
                                        },
                                        enabled = !isSubmitting,
                                        modifier = Modifier.height(38.dp).weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ثبت پرداختی", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { if (!isSubmitting) { isSubmitting = true; onUpdatePayment(0.0, "DEBTOR") } },
                                        enabled = !isSubmitting,
                                        modifier = Modifier.height(38.dp).weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("بدهکار", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { if (!isSubmitting) { isSubmitting = true; onUpdatePayment(payableAmount, "REVIEWED") } },
                                        enabled = !isSubmitting,
                                        modifier = Modifier.height(38.dp).weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("تسویه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                FilledIconButton(
                                    onClick = shareAction,
                                    modifier = Modifier.size(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }

                                if (canDelete) {
                                    FilledIconButton(
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = payInput,
                                onValueChange = { payInput = it },
                                label = { Text("پرداخت بخشی از مبلغ (تومان)", fontSize = 10.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .bringIntoViewRequester(payFieldBringIntoViewRequester)
                                    .onFocusChanged { isPayFieldFocused = it.isFocused },
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                singleLine = true
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledIconButton(
                                    onClick = shareAction,
                                    modifier = Modifier.size(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                if (canDelete) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(
                                        onClick = { showDeleteConfirm = true }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("حذف فاکتور", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

    if (showSaveGuestDialog) {
        SaveGuestAsCustomerDialog(
            initialGuestName = transaction.customerName,
            initialDebt = if (transaction.status == "DEBTOR") (transaction.amount - transaction.paidAmount).toDouble() else 0.0,
            onDismiss = { showSaveGuestDialog = false },
            onSave = { name, phone, d, c, desc ->
                viewModel.convertGuestToCustomer(
                    guestName = transaction.customerName,
                    fullName = name,
                    phoneNumber = phone,
                    debt = d.toLong(),
                    credit = c.toLong(),
                    description = desc
                )
                showSaveGuestDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف صورت‌حساب") },
            text = { Text("آیا از حذف این صورت‌حساب اطمینان دارید؟") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomerCard(
    customer: Customer,
    lang: String,
    customerTransactions: List<CustomerTransaction> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: GameNetViewModel,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    val permCustomerPasswords by viewModel.permCustomerPasswords.collectAsState()
    val canManagePasswords = currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER" || permCustomerPasswords
    val canDelete = currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER"

    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPointHistoryDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle()
                    } else {
                        expanded = !expanded
                    }
                },
                onLongClick = {
                    if (!isMultiSelectMode) {
                        onLongClick()
                    }
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectToggle() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = customer.fullName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { showPointHistoryDialog = true },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "تاریخچه مخاطب",
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    var showOverflowMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "گزینه‌های مخاطب",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("ویرایش مخاطب", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("تاریخچه فعالیت", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showPointHistoryDialog = true
                                }
                            )
                            if (canDelete) {
                                DropdownMenuItem(
                                    text = { Text("حذف مخاطب", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (customer.phoneNumber.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = customer.phoneNumber,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (canManagePasswords) {
                        Text(
                            text = "رمز عبور مشتری در مدیریت نمایش یا کپی نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val cleanInviteCode = customer.inviteCode.trim()
                    if (cleanInviteCode.isNotBlank() && !cleanInviteCode.equals("null", ignoreCase = true)) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                text = "🎟 کد دعوت: $cleanInviteCode",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    if (customer.description.isNotBlank()) {
                        Text(
                            text = customer.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    // Compact Debt, Credit, Points Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Points Info
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "🏆 امتیاز",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%,d", customer.points),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // LP Loyalty Balance — server-authoritative and visible directly on every manager customer card.
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = if (customer.lp > 0) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (customer.lp > 0) Color(0xFF42A5F5).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("LP", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                                Text(String.format(Locale.US, "%,d", customer.lp), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1565C0))
                            }
                        }

                        // Debt Info
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (customer.debt > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (customer.debt > 0) Color(0xFFEF5350).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = Localization.get("debt", lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.debt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%,d", customer.debt),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (customer.debt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Credit Info
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (customer.credit > 0) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (customer.credit > 0) Color(0xFF66BB6A).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = Localization.get("credit", lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.credit > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%,d", customer.credit),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (customer.credit > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Invited Customers List
                    val allCustomersList by viewModel.customers.collectAsState()
                    val invitedPeople = remember(allCustomersList, customer.inviteCode) {
                        if (customer.inviteCode.isBlank() || customer.inviteCode.equals("null", ignoreCase = true)) emptyList()
                        else allCustomersList.filter { normalizeInviteCode(it.invitedByCode) == normalizeInviteCode(customer.inviteCode) }
                    }

                    if (invitedPeople.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text(
                            text = "👥 افراد دعوت‌شده (${invitedPeople.size} نفر):",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            invitedPeople.forEach { invited ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = invited.fullName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val statusText = if (invited.invitePointsAwarded) "✅ پاداش ثبت شد" else "⏳ در انتظار شرط"
                                            Text(
                                                text = statusText,
                                                fontSize = 9.sp,
                                                color = if (invited.invitePointsAwarded) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(text = "بدهی: ${String.format(java.util.Locale.US, "%,d", invited.debt)}", fontSize = 9.sp, color = Color(0xFFC62828))
                                            Text(text = "اعتبار: ${String.format(java.util.Locale.US, "%,d", invited.credit)}", fontSize = 9.sp, color = Color(0xFF2E7D32))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (customer.description.isNotBlank()) {
                        Text(
                            text = customer.description,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                            Text(
                                text = "🏆 امتیاز کل: ${String.format(Locale.US, "%,d", customer.points)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }

                    // Debt & Credit Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Debt Info
                        Card(
                            modifier = Modifier.weight(1f).wrapContentHeight(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (customer.debt > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (customer.debt > 0) Color(0xFFEF5350).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = Localization.get("debt", lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.debt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%,d", customer.debt),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (customer.debt > 0) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Credit Info
                        Card(
                            modifier = Modifier.weight(1f).wrapContentHeight(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (customer.credit > 0) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (customer.credit > 0) Color(0xFF66BB6A).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = Localization.get("credit", lang),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.credit > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%,d", customer.credit),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (customer.credit > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Station Transactions Breakdown
                    if (customerTransactions.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text(
                            text = "📋 ریز صورت‌حساب‌ها:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            customerTransactions.take(10).forEach { trans ->
                                InvoiceCard(trans = trans)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (lang == "fa") "حذف مشتری" else "Delete Customer") },
            text = { Text(Localization.get("delete_confirm", lang)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(Localization.get("delete", lang), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(Localization.get("cancel", lang))
                }
            }
        )
    }

    if (showPointHistoryDialog) {
        PointHistoryDialog(
            customer = customer,
            viewModel = viewModel,
            onDismiss = { showPointHistoryDialog = false }
        )
    }
}

@Composable
fun PointHistoryDialog(
    customer: Customer,
    viewModel: GameNetViewModel,
    onDismiss: () -> Unit
) {
    val pointLogs by viewModel.getPointLogs(customer.id).collectAsState(initial = emptyList())
    val gnLedgerEntries by viewModel.allGnLedgerEntries.collectAsState()
    val customerTransactions by viewModel.customerTransactions.collectAsState()
    val customerGnLedger = remember(gnLedgerEntries, customer.id) {
        gnLedgerEntries.filter { it.customerId == customer.id }.sortedByDescending { it.timestamp }
    }
    val unsettledTransactions = remember(customerTransactions, customer.id) {
        customerTransactions.filter { it.customerId == customer.id && (it.status == "DEBTOR" || it.status == "UNREVIEWED") }.sortedByDescending { it.timestamp }
    }
    var purchasePointsInput by remember { mutableStateOf("") }
    var purchaseTitleInput by remember { mutableStateOf("خرید امتیاز از گیم‌نت") }
    var managerActivity by remember { mutableStateOf<com.example.data.network.ManagerCustomerActivity?>(null) }

    LaunchedEffect(customer.id) {
        managerActivity = viewModel.fetchManagerCustomerActivity(customer.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "🔍 تاریخچه امتیازهای ${customer.fullName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "واحد امتیازها: GN | مجموع: ${String.format(Locale.US, "%,d", customer.points)} GN",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (customerGnLedger.isNotEmpty() || unsettledTransactions.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("💎 وضعیت GN و تسویه", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            customerGnLedger.forEach { entry ->
                                val statusLabel = when (entry.status.uppercase()) {
                                    "PENDING" -> "در انتظار تسویه"
                                    "AVAILABLE" -> "تسویه شده"
                                    "REVERSED" -> "برگشت‌خورده"
                                    else -> entry.status
                                }
                                val sign = if (entry.gnAmount >= 0) "+" else ""
                                Text(
                                    text = "$sign${String.format(Locale.US, "%,d", entry.gnAmount)} GN • $statusLabel • ${entry.description.ifBlank { entry.transactionType }}",
                                    fontSize = 10.sp,
                                    color = if (entry.status == "PENDING") Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            unsettledTransactions.forEach { tx ->
                                val remaining = (tx.amount - tx.paidAmount).coerceAtLeast(0L)
                                val label = if (tx.status == "DEBTOR") "تسویه نشده" else "بررسی نشده"
                                Text(
                                    text = "${tx.title.ifBlank { "فاکتور" }} • مانده ${String.format(Locale.US, "%,d", remaining)} تومان • $label",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                managerActivity?.let { activity ->
                    if (activity.transactions.isNotEmpty() || activity.gnLedger.isNotEmpty() || activity.lpLedger.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("📚 جزئیات دریافت GN و LP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                activity.transactions.forEach { tx ->
                                    val sourceText = when {
                                        tx.gameCost > 0L && tx.foodCost > 0L -> "بازی + بوفه"
                                        tx.gameCost > 0L -> "بازی"
                                        tx.foodCost > 0L -> "بوفه"
                                        else -> "سایر"
                                    }
                                    Text(
                                        text = sourceText + " • GN: +" + String.format(Locale.US, "%,d", tx.earnedGn) + " • LP: +" + String.format(Locale.US, "%,d", tx.earnedLp) + " • " + tx.status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                activity.gnLedger.forEach { gn ->
                                    val source = when (gn.transactionType.uppercase()) {
                                        "GAME_REWARD" -> "بابت بازی"
                                        "BUFFET_REWARD" -> "بابت بوفه"
                                        "GAME_AND_BUFFET_REWARD" -> "بابت بازی + بوفه"
                                        "BEHAVIOR_REWARD" -> "تشویقی مدیر / رفتار"
                                        "ADMIN_ADJUSTMENT" -> "اصلاح یا تشویق مدیر"
                                        "REFERRAL" -> "معرفی / دعوت"
                                        else -> gn.description.ifBlank { gn.transactionType.ifBlank { "سایر" } }
                                    }
                                    Text(
                                        text = "GN +" + String.format(Locale.US, "%,d", gn.gnAmount) + " • " + source + " • مرجع: " + gn.referenceId,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                activity.lpLedger.forEach { lp ->
                                    Text(
                                        text = "LP " + (if (lp.type == "CREDIT") "+" else "-") + String.format(Locale.US, "%,d", lp.amount) + " • " + lp.referenceType.ifBlank { "تراکنش" } + " • " + lp.referenceId,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Purchase / Add GN Points section
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🛒 خرید / افزودن امتیاز GN از صاحب گیم‌نت:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = purchasePointsInput,
                                onValueChange = { purchasePointsInput = it },
                                label = { Text("مقدار GN", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp)
                            )
                            OutlinedTextField(
                                value = purchaseTitleInput,
                                onValueChange = { purchaseTitleInput = it },
                                label = { Text("عنوان / موضوع", fontSize = 10.sp) },
                                modifier = Modifier.weight(1.5f),
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp)
                            )
                        }
                        Button(
                            onClick = {
                                val pts = purchasePointsInput.toDoubleOrNull() ?: 0.0
                                if (pts > 0) {
                                    viewModel.addCustomerPointsWithLog(
                                        customer.id,
                                        pts.toLong(),
                                        purchaseTitleInput.ifBlank { "خرید امتیاز از گیم‌نت" }
                                    )
                                    purchasePointsInput = ""
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("ثبت و افزودن امتیاز GN", fontSize = 10.sp)
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "📋 لیست تراکنش‌ها و امتیازهای دریافت‌شده:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (pointLogs.isEmpty()) {
                    Text(
                        text = "هنوز امتیازی ثبت نشده است.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(pointLogs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val dateStr = android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", log.timestamp).toString()
                                        Text(
                                            text = dateStr,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${if (log.points >= 0) "+" else ""}${String.format(Locale.US, "%,d", log.points)} GN",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        color = if (log.points >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@Composable
fun ReservationsTabContent(
    reservations: List<Reservation>,
    lang: String,
    canDelete: Boolean,
    onDeleteReservation: (Reservation) -> Unit,
    onAddClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (reservations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Text(
                        text = Localization.get("no_reservations", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reservations, key = { it.id }) { reservation ->
                    ReservationCard(
                        reservation = reservation,
                        lang = lang,
                        canDelete = canDelete,
                        onDelete = { onDeleteReservation(reservation) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_add_reservation"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = Localization.get("add_reservation", lang), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ReservationCard(
    reservation: Reservation,
    lang: String,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val formattedTime = remember(reservation.reservationTimeMillis) {
        val sdf = SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US)
        sdf.format(Date(reservation.reservationTimeMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reservation.fullName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = reservation.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (canDelete) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = Localization.get("reservation_time", lang),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Localization.get("reservation_duration", lang),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${reservation.durationMinutes} " + (if (lang == "fa") "دقیقه" else "min"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Alert Notification Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (lang == "fa") "هشدارهای نیم ساعت، 15 دقیقه و 5 دقیقه قبل فعال است" else "30m, 15m, and 5m alarms are scheduled",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (lang == "fa") "حذف نوبت رزرو" else "Delete Reservation", fontWeight = FontWeight.Bold) },
            text = { Text(if (lang == "fa") "آیا از حذف رزرو ${reservation.fullName} اطمینان دارید؟" else "Are you sure you want to delete reservation for ${reservation.fullName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Localization.get("delete", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(Localization.get("cancel", lang))
                }
            }
        )
    }
}

@Composable
fun CustomerFormDialog(
    customer: Customer?,
    lang: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, debt: Double, credit: Double, desc: String, invitedByCode: String, manualPoints: Double, password: String) -> Unit,
    viewModel: GameNetViewModel
) {
    val context = LocalContext.current
    
    val currentAdminRole by viewModel.currentAdminRole.collectAsState()
    val permCustomerPasswords by viewModel.permCustomerPasswords.collectAsState()
    val canManagePasswords = currentAdminRole == "SUPER_MANAGER" || currentAdminRole == "GAMENET_MANAGER" || currentAdminRole == "MANAGER" || permCustomerPasswords

    var fullName by remember(customer) { mutableStateOf(customer?.fullName ?: "") }
    var phoneNumber by remember(customer) { mutableStateOf(customer?.phoneNumber ?: "") }
    var debtInput by remember(customer) { mutableStateOf(if (customer != null && customer.debt > 0) String.format(Locale.US, "%,d", customer.debt) else "") }
    var creditInput by remember(customer) { mutableStateOf(if (customer != null && customer.credit > 0) String.format(Locale.US, "%,d", customer.credit) else "") }
    var description by remember(customer) { mutableStateOf(customer?.description ?: "") }
    var invitedByCode by remember(customer) { mutableStateOf(if (customer?.invitedByCode.equals("null", ignoreCase = true)) "" else (customer?.invitedByCode ?: "")) }
    var manualPointsInput by remember(customer) { mutableStateOf("") }
    var password by remember(customer) { mutableStateOf("") }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val details = getContactDetails(context, contactUri)
            if (details != null) {
                if (fullName.isBlank()) fullName = details.first
                phoneNumber = details.second.replace(" ", "").replace("-", "")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(context, Localization.get("contact_permission_needed", lang), Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .wrapContentHeight()
            .imePadding(),
        title = {
            Text(
                text = if (customer != null) (if (lang == "fa") "ویرایش اطلاعات مشتری" else "Edit Customer") else Localization.get("add_customer", lang),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Full Name Input
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text(Localization.get("full_name", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                item {
                    // Phone Number Input with Contact Directory Option
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text(Localization.get("phone_number", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        pickContactLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContactPhone,
                                    contentDescription = "Pick Contact",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Account Creation Card for GameNexa Customers
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "ساخت حساب کاربری برای این مشتری",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (canManagePasswords) {
                                Text(
                                    text = "نام کاربری مشتری جهت ورود: ${if (phoneNumber.isNotBlank()) phoneNumber.trim() else "(شماره همراه مشتری)"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = viewModel.toEnglishDigits(it) },
                                    label = { Text("رمز عبور اختصاصی مشتری", fontSize = 11.sp) },
                                    placeholder = { Text("مثلاً 123456 یا دکمه تولید خودکار", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        Row(
                                            modifier = Modifier.padding(end = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (password.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText(
                                                            "GameNexa Customer Account",
                                                            "نام کاربری: ${phoneNumber.trim()}\nورود به اپلیکیشن مشتریان GameNexa"
                                                        )
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "نام کاربری کپی شد؛ رمز عبور را جداگانه منتقل کنید.", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    password = viewModel.generateCustomerPassword()
                                                }
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "تولید خودکار", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            password = viewModel.generateCustomerPassword()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تولید رمز خودکار", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (password.isNotBlank()) {
                                        FilledTonalButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText(
                                                    "GameNexa Customer Account",
                                                    "نام کاربری: ${phoneNumber.trim()}\nورود به اپلیکیشن مشتریان GameNexa"
                                                )
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "نام کاربری کپی شد؛ رمز عبور در کلیپ‌بورد قرار نگرفت.", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "کپی", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("کپی مشخصات ورود", fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "ساخت و مشاهده رمز عبور تنها با دسترسی مدیر ارشد امکان‌پذیر است.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    // Invited By Code Input
                    OutlinedTextField(
                        value = invitedByCode,
                        onValueChange = { invitedByCode = it },
                        label = { Text("کد دعوت معرف (اختیاری)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                item {
                    // Debt Input
                    OutlinedTextField(
                        value = debtInput,
                        onValueChange = { debtInput = it },
                        label = { Text(Localization.get("debt", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0", fontSize = 11.sp) }
                    )
                }

                item {
                    // Credit Input
                    OutlinedTextField(
                        value = creditInput,
                        onValueChange = { creditInput = it },
                        label = { Text(Localization.get("credit", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0", fontSize = 11.sp) }
                    )
                }

                item {
                    // Manual GN / Points Input
                    OutlinedTextField(
                        value = manualPointsInput,
                        onValueChange = { manualPointsInput = it },
                        label = { Text("افزایش / کاهش دستی GN (امتیاز)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("مثلا 50 یا -20", fontSize = 11.sp) }
                    )
                }

                item {
                    // Description Input
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(Localization.get("description", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isNotBlank()) {
                        val debt = debtInput.toDoubleOrNull() ?: 0.0
                        val credit = creditInput.toDoubleOrNull() ?: 0.0
                        val manualPoints = manualPointsInput.toDoubleOrNull() ?: 0.0
                        onSave(fullName, phoneNumber, debt, credit, description, invitedByCode, manualPoints, password)
                    }
                },
                enabled = fullName.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(Localization.get("save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", lang))
            }
        }
    )
}

@Composable
fun ReservationFormDialog(
    customers: List<Customer>,
    stations: List<Pair<Long,String>>,
    lang: String,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, timestamp: Long, duration: Int, stationId: Long, isVip: Boolean, paidAmount: Long) -> Unit
) {
    val context = LocalContext.current

    var selectedCustomerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var selectedStationId by remember { mutableLongStateOf(0L) }
    var stationDropdownExpanded by remember { mutableStateOf(false) }
    var normalDurationOptions by remember { mutableStateOf<List<Int>>(emptyList()) }
    var vipDurationOptions by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isVipReservation by remember { mutableStateOf(false) }

    LaunchedEffect(stations) {
        if (selectedStationId == 0L) selectedStationId = stations.firstOrNull()?.first ?: 0L
        if (durationInput.isBlank()) {
            val rules = SelfHostedManager.fetchReservationRules()
            val root = rules?.optJSONObject("rules")
            val arr = root?.optJSONArray("normalDurationsMinutes")
            val vipArr = root?.optJSONArray("vipDurationsMinutes")
            normalDurationOptions = if (arr == null) emptyList() else buildList {
                for (i in 0 until arr.length()) if (arr.optInt(i) > 0) add(arr.optInt(i))
            }
            vipDurationOptions = if (vipArr == null) emptyList() else buildList {
                for (i in 0 until vipArr.length()) if (vipArr.optInt(i) > 0) add(vipArr.optInt(i))
            }
            if (durationInput.isBlank() && normalDurationOptions.isNotEmpty()) durationInput = normalDurationOptions.first().toString()
        }
    }

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            val details = getContactDetails(context, contactUri)
            if (details != null) {
                if (selectedCustomerName.isBlank()) selectedCustomerName = details.first
                phoneNumber = details.second.replace(" ", "").replace("-", "")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            Toast.makeText(context, Localization.get("contact_permission_needed", lang), Toast.LENGTH_SHORT).show()
        }
    }

    // All reservation dates are entered/displayed in the Iranian Solar Hijri calendar.
    val jalaliToday = remember { JalaliCalendarHelper.currentJalali() }
    var selectedJalaliYear by remember { mutableIntStateOf(jalaliToday[0]) }
    var selectedJalaliMonth by remember { mutableIntStateOf(jalaliToday[1]) }
    var selectedJalaliDay by remember { mutableIntStateOf(jalaliToday[2]) }
    var showJalaliDatePicker by remember { mutableStateOf(false) }
    var paymentInput by remember { mutableStateOf("") }
    var hourInput by remember { mutableStateOf(String.format(Locale.US, "%02d", Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).get(Calendar.HOUR_OF_DAY))) }
    var minuteInput by remember { mutableStateOf(String.format(Locale.US, "%02d", Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).get(Calendar.MINUTE))) }

    val jalaliMonths = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Localization.get("add_reservation", lang),
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Autocomplete or manually type customer name
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomerName,
                            onValueChange = {
                                selectedCustomerName = it
                                expandedDropdown = true
                            },
                            label = { Text(Localization.get("full_name", lang), fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = {
                                IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                    Icon(
                                        imageVector = if (expandedDropdown) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            }
                        )

                        // Autocomplete list
                        DropdownMenu(
                            expanded = expandedDropdown && customers.isNotEmpty(),
                            onDismissRequest = { expandedDropdown = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            val filtered = customers.filter {
                                it.fullName.contains(selectedCustomerName, ignoreCase = true)
                            }
                            filtered.forEach { cust ->
                                DropdownMenuItem(
                                    text = { Text(cust.fullName) },
                                    onClick = {
                                        selectedCustomerName = cust.fullName
                                        phoneNumber = cust.phoneNumber
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    // Phone Number Input
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text(Localization.get("phone_number", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.READ_CONTACTS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        pickContactLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContactPhone,
                                    contentDescription = "Pick Contact",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isVipReservation,
                            onCheckedChange = {
                                isVipReservation = it
                                val options = if (it) vipDurationOptions else normalDurationOptions
                                if (options.isNotEmpty() && durationInput.toIntOrNull() !in options) durationInput = options.first().toString()
                                if (it) selectedStationId = 0L
                                else if (selectedStationId == 0L) selectedStationId = stations.firstOrNull()?.first ?: 0L
                            }
                        )
                        Text(if (lang == "fa") "رزرو VIP / اختصاصی" else "VIP / Exclusive reservation")
                    }
                }

                if (!isVipReservation) {
                    item {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { stationDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stations.firstOrNull { it.first == selectedStationId }?.second
                                        ?: if (lang == "fa") "انتخاب ایستگاه" else "Select station"
                                )
                            }
                            DropdownMenu(
                                expanded = stationDropdownExpanded,
                                onDismissRequest = { stationDropdownExpanded = false }
                            ) {
                                stations.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedStationId = id
                                            stationDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item { Text(if (lang == "fa") "رزرو کامل سالن" else "Full hall reservation", fontWeight = FontWeight.Bold) }
                }

                item {
                    Text("تاریخ حضور", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("امروز" to 0, "فردا" to 1, "پس‌فردا" to 2).forEach { (label, offset) ->
                            OutlinedButton(
                                onClick = {
                                    val base = JalaliCalendarHelper.jalaliToGregorianMillis(jalaliToday[0], jalaliToday[1], jalaliToday[2]) ?: System.currentTimeMillis()
                                    val c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")); c.timeInMillis = base + offset * 86_400_000L
                                    val j = JalaliCalendarHelper.currentJalali().let { now ->
                                        val cc = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")); cc.timeInMillis = c.timeInMillis
                                        JalaliCalendarHelper.formatJalaliDateTime(c.timeInMillis, false).split("/").map { it.toInt() }.toIntArray()
                                    }
                                    selectedJalaliYear = j[0]; selectedJalaliMonth = j[1]; selectedJalaliDay = j[2]
                                },
                                modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text(label, fontSize = 11.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { showJalaliDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${selectedJalaliYear}/${String.format(Locale.US, "%02d", selectedJalaliMonth)}/${String.format(Locale.US, "%02d", selectedJalaliDay)} — ${jalaliMonths[selectedJalaliMonth - 1]}")
                    }
                    if (showJalaliDatePicker) {
                        JalaliReservationDatePicker(
                            year = selectedJalaliYear,
                            month = selectedJalaliMonth,
                            day = selectedJalaliDay,
                            months = jalaliMonths,
                            onSelect = { y, m, d -> selectedJalaliYear = y; selectedJalaliMonth = m; selectedJalaliDay = d; showJalaliDatePicker = false },
                            onDismiss = { showJalaliDatePicker = false }
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = paymentInput,
                        onValueChange = { paymentInput = it.filter(Char::isDigit) },
                        label = { Text("مقدار پرداختی (تومان)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                item {
                    // Time Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hourInput,
                            onValueChange = { hourInput = it },
                            label = { Text(if (lang == "fa") "ساعت" else "HH", fontSize = 8.sp) },
                                modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(
                            value = minuteInput,
                            onValueChange = { minuteInput = it },
                            label = { Text(if (lang == "fa") "دقیقه" else "mm", fontSize = 8.sp) },
                                modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                item {
                    // Duration Input
                    OutlinedTextField(
                        value = durationInput,
                        onValueChange = { durationInput = it },
                        label = { Text(Localization.get("reservation_duration", lang), fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                item {
                    // Fast preset chips for duration selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (if (isVipReservation) vipDurationOptions else normalDurationOptions).forEach { mins ->
                            val chipLabel = if (mins >= 60) "${mins / 60} " + (if (lang == "fa") "ساعت" else "hr") else "$mins " + (if (lang == "fa") "دقیقه" else "min")
                            SuggestionChip(
                                onClick = { durationInput = mins.toString() },
                                label = { Text(chipLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedCustomerName.isNotBlank() && durationInput.toIntOrNull() != null) {
                        // Construct Timestamp safely
                        try {
                            val timestamp = JalaliCalendarHelper.jalaliToGregorianMillis(
                                selectedJalaliYear, selectedJalaliMonth, selectedJalaliDay,
                                hourInput.toInt(), minuteInput.toInt()
                            ) ?: throw IllegalArgumentException("Invalid Solar Hijri date")

                            onSave(
                                selectedCustomerName,
                                phoneNumber,
                                timestamp,
                                durationInput.toInt(),
                                selectedStationId,
                                isVipReservation,
                                paymentInput.toLongOrNull() ?: 0L
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, if (lang == "fa") "فرمت تاریخ یا زمان نامعتبر است" else "Invalid date/time format", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = selectedCustomerName.isNotBlank() && durationInput.toIntOrNull() != null && (isVipReservation || selectedStationId > 0L),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(Localization.get("save", lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", lang))
            }
        }
    )
}

@Composable
private fun JalaliReservationDatePicker(
    year: Int,
    month: Int,
    day: Int,
    months: List<String>,
    onSelect: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var shownYear by remember(year) { mutableIntStateOf(year) }
    var shownMonth by remember(month) { mutableIntStateOf(month) }
    val firstMillis = JalaliCalendarHelper.jalaliToGregorianMillis(shownYear, shownMonth, 1) ?: return
    val firstCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")); firstCal.timeInMillis = firstMillis
    val firstWeekday = firstCal.get(Calendar.DAY_OF_WEEK) % 7 // Saturday=0, Sunday=1, ... Friday=6
    val days = JalaliCalendarHelper.jalaliMonthDays(shownYear, shownMonth)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { if (shownMonth == 1) { shownMonth = 12; shownYear-- } else shownMonth-- }) { Text("‹") }
                Text("${months[shownMonth - 1]} $shownYear", fontWeight = FontWeight.Bold)
                TextButton(onClick = { if (shownMonth == 12) { shownMonth = 1; shownYear++ } else shownMonth++ }) { Text("›") }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("ش","ی","د","س","چ","پ","ج").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                for (week in 0..5) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val number = week * 7 + col - firstWeekday + 1
                            Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                                if (number in 1..days) {
                                    val selected = number == day && shownYear == year && shownMonth == month
                                    TextButton(onClick = { onSelect(shownYear, shownMonth, number) }, modifier = Modifier.size(42.dp), contentPadding = PaddingValues(0.dp)) {
                                        Text(number.toString(), fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
fun ReviewedGroupCard(
    customerName: String,
    totalPaid: Double,
    transactions: List<CustomerTransaction>,
    lang: String,
    onUpdateStatus: (CustomerTransaction, String) -> Unit,
    onUpdatePayment: (CustomerTransaction, Double, String) -> Unit,
    onDelete: (CustomerTransaction) -> Unit,
    viewModel: GameNetViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = customerName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%,d تومان".format(Locale.US, totalPaid.toLong()),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { transactions.forEach(onDelete) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف فاکتورهای این مشتری",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = "تعداد صورت‌حساب‌های تسویه شده: ${transactions.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    transactions.forEach { trans ->
                        InvoiceCard(trans = trans)
                    }
                }
            }
        }
    }
}

@Composable
fun DebtorGroupCard(
    customerName: String,
    totalDebt: Long,
    transactions: List<CustomerTransaction>,
    lang: String,
    onUpdateStatus: (CustomerTransaction, String) -> Unit,
    onUpdatePayment: (CustomerTransaction, Double, String) -> Unit,
    onDelete: (CustomerTransaction) -> Unit,
    viewModel: GameNetViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var showSaveGuestDialog by remember { mutableStateOf(false) }
    val isGuest = remember(customerName, transactions) {
        customerName.contains("مهمان", ignoreCase = true) || transactions.any { it.customerId <= 0 }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = customerName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isGuest) {
                        Surface(
                            onClick = { showSaveGuestDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Text(
                                    text = "ثبت مخاطب +",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isEditing = !isEditing
                            expanded = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ثبت پرداختی یا تسویه",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "%,d تومان".format(Locale.US, totalDebt),
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            AnimatedVisibility(visible = expanded || isEditing) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    if (isEditing) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "دریافت وجه از $customerName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = inputAmount,
                                    onValueChange = { inputAmount = it },
                                    label = { Text("مبلغ دریافتی (تومان)", fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 13.sp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val amt = inputAmount.toDoubleOrNull() ?: 0.0
                                    if (amt > 0.0) {
                                        Button(
                                            onClick = {
                                                var remainingPay = amt
                                                transactions.forEach { trans ->
                                                    if (remainingPay > 0.0 && trans.status == "DEBTOR") {
                                                        val transDebt = (trans.amount - trans.paidAmount).toDouble()
                                                        val payForThis = remainingPay.coerceAtMost(transDebt)
                                                        val newPaid = trans.paidAmount.toDouble() + payForThis
                                                        val newStatus = if (newPaid >= trans.amount.toDouble()) "REVIEWED" else "DEBTOR"
                                                        onUpdatePayment(trans, newPaid, newStatus)
                                                        remainingPay -= payForThis
                                                    }
                                                }
                                                inputAmount = ""
                                                isEditing = false
                                            },
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("پرداختی امروز", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            transactions.forEach { trans ->
                                                if (trans.status == "DEBTOR") {
                                                    onUpdatePayment(trans, trans.amount.toDouble(), "REVIEWED")
                                                }
                                            }
                                            inputAmount = ""
                                            isEditing = false
                                        },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("تسویه شده", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    transactions.forEach { trans ->
                        InvoiceCard(trans = trans)
                    }
                }
            }
        }
    }

    if (showSaveGuestDialog) {
        SaveGuestAsCustomerDialog(
            initialGuestName = customerName,
            initialDebt = totalDebt.toDouble(),
            onDismiss = { showSaveGuestDialog = false },
            onSave = { name, phone, d, c, desc ->
                viewModel.convertGuestToCustomer(
                    guestName = customerName,
                    fullName = name,
                    phoneNumber = phone,
                    debt = d.toLong(),
                    credit = c.toLong(),
                    description = desc
                )
                showSaveGuestDialog = false
            }
        )
    }
}
private fun getContactDetails(context: Context, contactUri: Uri): Pair<String, String>? {
    var name = ""
    var phoneNumber = ""
    try {
        val contentResolver = context.contentResolver
        contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex) ?: ""
                }

                val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val hasPhone = if (hasPhoneIndex >= 0) cursor.getInt(hasPhoneIndex) else 0

                if (hasPhone > 0) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIndex >= 0) {
                        val contactId = cursor.getString(idIndex)
                        contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            arrayOf(contactId),
                            null
                        )?.use { phoneCursor ->
                            if (phoneCursor.moveToFirst()) {
                                val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                if (numberIndex >= 0) {
                                    phoneNumber = phoneCursor.getString(numberIndex) ?: ""
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return if (name.isNotEmpty() || phoneNumber.isNotEmpty()) Pair(name, phoneNumber) else null
}

private fun formatConciseTitle(rawText: String): String {
    var s = rawText
    s = s.replace("PlayStation 5", "PS5", ignoreCase = true)
        .replace("PlayStation5", "PS5", ignoreCase = true)
        .replace("PlayStation 4", "PS4", ignoreCase = true)
        .replace("PlayStation4", "PS4", ignoreCase = true)
        .replace("بازی", "", ignoreCase = true)
        .replace("دسته", "", ignoreCase = true)
        .replace("ایستگاه شماره", "ایستگاه", ignoreCase = true)
    s = s.replace(Regex("\\s+"), " ").trim()
    return s
}

@Composable
fun SalonSettlementDialog(
    transactions: List<com.example.data.CustomerTransaction>,
    lang: String = "fa",
    onDismiss: () -> Unit,
    onSettleAll: () -> Unit
) {
    val totalUnreviewed = transactions.filter { it.status == "UNREVIEWED" }.sumOf { it.amount - it.paidAmount }
    val totalDebt = transactions.filter { it.status == "DEBTOR" }.sumOf { it.amount - it.paidAmount }
    val totalPaid = transactions.filter { it.status == "REVIEWED" }.sumOf { it.paidAmount }
    val currUnit = if (lang == "fa") "تومان" else "Toman"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lang == "fa") "تسویه حساب سالن (شیفت)" else "Hall Shift Settlement", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (lang == "fa") "خلاصه وضعیت مالی صورت‌حساب‌های فعلی:" else "Financial summary of current invoices:", fontSize = 13.sp)
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "fa") "جمع دریافتی (تسویه شده):" else "Total Received (Settled):", fontSize = 12.sp)
                    Text("%,d $currUnit".format(java.util.Locale.US, totalPaid), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "fa") "بدهی‌های مانده:" else "Remaining Debts:", fontSize = 12.sp)
                    Text("%,d $currUnit".format(java.util.Locale.US, totalDebt), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (lang == "fa") "صورت‌حساب‌های بررسی نشده:" else "Unreviewed Invoices:", fontSize = 12.sp)
                    Text("%,d $currUnit".format(java.util.Locale.US, totalUnreviewed), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (lang == "fa") "با تایید تسویه، تمامی صورت‌حساب‌های «تسویه شده» و «بررسی نشده» آرشیو شده و لاگ تسویه برای مدیر ثبت می‌گردد (بدهی‌ها باقی می‌مانند)."
                    else "Upon settlement, all 'Settled' and 'Pending' invoices are archived and a shift log is created for the manager (debts remain active).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSettleAll(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Text(if (lang == "fa") "ثبت تسویه و آرشیو" else "Submit Settlement & Archive", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (lang == "fa") "بستن" else "Close") }
        }
    )
}
