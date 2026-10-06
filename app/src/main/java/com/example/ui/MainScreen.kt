package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.math.BigDecimal
import java.math.RoundingMode
import com.example.util.ExactBilling
import com.example.utils.NumberConverter
import androidx.compose.ui.platform.LocalFocusManager
import com.example.util.JalaliCalendarHelper
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConsoleType
import com.example.data.Product
import com.example.data.StationState
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: GameNetViewModel,
    onNavigateToSubscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val stations by viewModel.stationStates.collectAsState()
    val startingStationIds by viewModel.startingStationIds.collectAsState()
    val consoleList by viewModel.consoleTypes.collectAsState()
    val productList by viewModel.products.collectAsState()
    val ordersMap by viewModel.stationOrdersMap.collectAsState()
    val lang by viewModel.language.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val serverClockWarningVisible by viewModel.serverClockWarningVisible.collectAsState()
    val serverClockMillis by viewModel.serverClockMillis.collectAsState()

    HallWeatherOverlay(
        themeName = appTheme,
        modifier = modifier.fillMaxSize()
    ) {
        // Handle empty state
        if (stations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_no_stations_configured_1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_go_to_settings_to_configure_th_2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val isServerConnected by viewModel.isServerConnected.collectAsState()
            val coroutineScope = rememberCoroutineScope()

            var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "READY", "ACTIVE"

            val accessState by viewModel.accessState.collectAsState()
            val licenseState by viewModel.licenseState.collectAsState()
            val isAdminAuthenticated by viewModel.isAdminAuthenticated.collectAsState()
            val isTrialActive by viewModel.isTrialModeFlow.collectAsState()
            val currentRole by viewModel.currentAdminRole.collectAsState()
            val isTrialMode = isTrialActive || currentRole == "TRIAL_USER" || viewModel.isTrialUser

            val visibleStations = remember(stations, isTrialMode) {
                if (isTrialMode) stations.take(2) else stations
            }

            val totalCount by remember(visibleStations) { derivedStateOf { visibleStations.size } }
            val readyCount by remember(visibleStations) { derivedStateOf { visibleStations.count { it.status == "FREE" } } }
            val activeCount by remember(visibleStations) { derivedStateOf { visibleStations.count { it.status == "RUNNING" || it.status == "PAUSED" } } }
            
            val filteredStations by remember(selectedFilter, visibleStations) {
                derivedStateOf {
                    val filtered = when (selectedFilter) {
                        "READY" -> visibleStations.filter { it.status == "FREE" }
                        "ACTIVE" -> visibleStations.filter { it.status == "RUNNING" || it.status == "PAUSED" }
                        else -> visibleStations
                    }
                    // Backend IDs remain stable; UI numbers are derived from the full station list
                    // so filtering never renumbers a station unexpectedly.
                    filtered.sortedBy { it.id }
                }
            }
            
            // Trial Active Banner using Live Server Validation

            when (val state = accessState) {
                is AppAccessState.Checking -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "در حال بررسی اعتبار...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                is AppAccessState.Denied -> {
                    LaunchedEffect(state, licenseState, isAdminAuthenticated) {
                        // A freshly authenticated Manager can briefly carry a stale Denied
                        // access snapshot while the authoritative login state is being published.
                        // Never turn that transient UI race into an automatic logout.
                        if (!isAdminAuthenticated || licenseState !is LicenseState.Active) {
                            viewModel.setLicenseExpired(state.message)
                            viewModel.handleAccessDenied()
                        }
                    }
                }
                                is AppAccessState.Allowed -> {
                    SubscriptionWarningBanner(
                        state = state,
                        lang = lang,
                        viewModel = viewModel,
                        onNavigateToSubscription = onNavigateToSubscription
                    )
                }
            }

            // SUPER_MANAGER is lifetime-entitled, but still needs an explicit visible
            // reconnect warning when the GameNexa API itself becomes unreachable.
            if (currentRole == "SUPER_MANAGER" && !isServerConnected) {
                SuperManagerOfflineBanner(
                    lang = lang,
                    remainingSeconds = viewModel.superManagerOfflineBannerSecondsRemaining.collectAsState().value
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Station Filter Summary Row with 3 Colored Circle Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Stations (Grey circle)
                StationFilterChip(
                    label = androidx.compose.ui.res.stringResource(com.example.R.string.ext_total_3),
                    count = totalCount,
                    circleColor = Color(0xFF888888),
                    isSelected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    modifier = Modifier.weight(1f)
                )

                // Ready Stations (Green circle)
                StationFilterChip(
                    label = androidx.compose.ui.res.stringResource(com.example.R.string.ext_ready_4),
                    count = readyCount,
                    circleColor = Color(0xFF4CAF50),
                    isSelected = selectedFilter == "READY",
                    onClick = { selectedFilter = "READY" },
                    modifier = Modifier.weight(1f)
                )

                // Active Stations (Red circle)
                StationFilterChip(
                    label = androidx.compose.ui.res.stringResource(com.example.R.string.ext_active_5),
                    count = activeCount,
                    circleColor = Color(0xFFE53935),
                    isSelected = selectedFilter == "ACTIVE",
                    onClick = { selectedFilter = "ACTIVE" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredStations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_no_stations_in_this_section_6),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("stations_grid"),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalItemSpacing = 8.dp,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = filteredStations,
                        key = { _, station -> station.id },
                        contentType = { _, _ -> "station_card" }
                    ) { index, station ->
                        val displayStationNumber = visibleStations.sortedBy { it.id }.indexOfFirst { it.id == station.id } + 1
                        val orders = if (station.status == "RUNNING" || station.status == "PAUSED") ordersMap[station.id] ?: emptyList() else emptyList()
                        val hourlyRate = viewModel.getHourlyRateSync(station.consoleType, station.controllerCount)

                        StationCard(
                            station = station,
                            isStarting = station.id in startingStationIds,
                            displayStationNumber = displayStationNumber,
                            orders = orders,
                            consoleList = consoleList,
                            productList = productList,
                            viewModel = viewModel,
                            hourlyRate = hourlyRate,
                            lang = lang,
                            onStart = { payText, durationText, customerIds, customerNames, customerPrepayments ->
                                viewModel.startStation(station.id, payText, durationText, customerIds, customerNames, customerPrepayments)
                            },
                            onPause = { viewModel.pauseStation(station.id) },
                            onResume = { viewModel.resumeStation(station.id) },
                            onFinish = { viewModel.finishStation(station.id) },
                            onConsoleChange = { name -> viewModel.updateStationConsole(station.id, name) },
                            onControllersChange = { count -> viewModel.updateStationControllers(station.id, count) },
                            onAddProduct = { pName -> viewModel.addBuffetOrder(station.id, pName) },
                            onIncrementProduct = { pName -> viewModel.incrementBuffetOrder(station.id, pName) },
                            onDecrementProduct = { pName -> viewModel.decrementBuffetOrder(station.id, pName) }
                        )
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationCard(
    station: StationState,
    isStarting: Boolean = false,
    displayStationNumber: Int,
    orders: List<com.example.data.StationOrder>,
    consoleList: List<ConsoleType>,
    productList: List<Product>,
    viewModel: GameNetViewModel,
    hourlyRate: Long,
    lang: String,
    onStart: (String, String, List<Long>, List<String>, Map<Long, Long>) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onConsoleChange: (String) -> Unit,
    onControllersChange: (Int) -> Unit,
    onAddProduct: (String) -> Unit,
    onIncrementProduct: (String) -> Unit,
    onDecrementProduct: (String) -> Unit
) {
    val isRunning = station.status == "RUNNING"
    val isPaused = station.status == "PAUSED"
    val isFree = station.status == "FREE"

    // Real-time ticking flow: ONLY active when station is running!
    val tickerFlow = remember(isRunning) {
        if (isRunning) viewModel.currentTime else flowOf(0L)
    }
    val currentTime by tickerFlow.collectAsState(initial = 0L)

    // Is countdown or stopwatch?
    val isCountdown = remember(station.prepaymentAmount, station.durationLimitMinutes) { station.prepaymentAmount > 0L || station.durationLimitMinutes > 0 }
    val durationLimitMillis = remember(station.prepaymentAmount, hourlyRate, station.durationLimitMinutes) {
        exactPrepaymentDurationMillis(station.prepaymentAmount, hourlyRate, station.durationLimitMinutes)
    }

    val segmentsDurationMs = remember(station.segmentsJson) {
        station.getSegmentsList().sumOf { it.endTimeMs - it.startTimeMs }
    }
    val currentElapsedMs by remember(isRunning, isPaused, currentTime, station.elapsedPlayingTimeMillis, station.lastStateChangeTimeMillis) {
        derivedStateOf {
            when {
                isRunning -> station.elapsedPlayingTimeMillis + (currentTime - station.lastStateChangeTimeMillis)
                isPaused -> station.elapsedPlayingTimeMillis
                else -> 0L
            }
        }
    }
    val elapsedMillis by remember(segmentsDurationMs, currentElapsedMs) {
        derivedStateOf { segmentsDurationMs + currentElapsedMs }
    }

    val remainingMillis by remember(isCountdown, durationLimitMillis, elapsedMillis) {
        derivedStateOf {
            if (isCountdown) {
                (durationLimitMillis - elapsedMillis).coerceAtLeast(0L)
            } else {
                0L
            }
        }
    }

    val isTimeUp by remember(isCountdown, elapsedMillis, durationLimitMillis) {
        derivedStateOf { isCountdown && elapsedMillis > durationLimitMillis }
    }

    val overtimeMillis by remember(isTimeUp, elapsedMillis, durationLimitMillis) {
        derivedStateOf { if (isTimeUp) elapsedMillis - durationLimitMillis else 0L }
    }

    val overtimeCost = remember(isTimeUp, overtimeMillis, hourlyRate) {
        if (isTimeUp) ExactBilling.costForMillis(hourlyRate, overtimeMillis) else BigDecimal.ZERO
    }

    val colorScheme = MaterialTheme.colorScheme
    val errorColor = colorScheme.error
    val primaryColor = colorScheme.primary
    val outlineColor = colorScheme.outline
    val tertiaryColor = colorScheme.tertiary
    val onSurfaceVariantColor = colorScheme.onSurfaceVariant

    val borderColor by remember(isTimeUp, isRunning, isPaused, errorColor, primaryColor, outlineColor) {
        derivedStateOf {
            when {
                isTimeUp -> errorColor
                isRunning -> primaryColor
                isPaused -> errorColor
                else -> outlineColor
            }
        }
    }

    val overtimeDebtLabel = androidx.compose.ui.res.stringResource(com.example.R.string.ext_overtime_debt_7)
    val statusText by remember(isTimeUp, isRunning, isPaused, lang, overtimeDebtLabel) {
        derivedStateOf {
            when {
                isTimeUp -> overtimeDebtLabel
                isRunning -> Localization.get("active", lang)
                isPaused -> Localization.get("paused", lang)
                else -> Localization.get("available", lang)
            }
        }
    }

    val statusColor by remember(isTimeUp, isRunning, isPaused, errorColor, tertiaryColor, onSurfaceVariantColor) {
        derivedStateOf {
            when {
                isTimeUp -> errorColor
                isRunning -> tertiaryColor
                isPaused -> errorColor
                else -> onSurfaceVariantColor
            }
        }
    }

    val segmentsCost = remember(station.segmentsJson) {
        station.getSegmentsList().sumOf { it.cost }
    }
    val segmentsCostExact = remember(station.segmentsJson) {
        station.getSegmentsList().fold(BigDecimal.ZERO) { acc, segment -> acc.add(BigDecimal.valueOf(segment.cost)) }
    }
    val gameCostExact = remember(isCountdown, station.prepaymentAmount, overtimeCost, elapsedMillis, currentElapsedMs, hourlyRate, segmentsCostExact) {
        if (isCountdown) {
            // Prepayment is a credit, not an already-consumed cost. Consume it live by exact active time.
            ExactBilling.costForMillis(hourlyRate, elapsedMillis)
        } else {
            segmentsCostExact.add(ExactBilling.costForMillis(hourlyRate, currentElapsedMs))
        }
    }
    val gameCost = gameCostExact.toLong()

    val remainingPrepaymentExact = remember(isCountdown, isTimeUp, elapsedMillis, hourlyRate, station.prepaymentAmount) {
        if (!isCountdown || isTimeUp) BigDecimal.ZERO
        else BigDecimal.valueOf(station.prepaymentAmount).subtract(ExactBilling.costForMillis(hourlyRate, elapsedMillis)).max(BigDecimal.ZERO)
    }
    val remainingPrepayment = remainingPrepaymentExact.toLong()

    // Calculate food cost
    val foodCost by remember(orders, productList) {
        derivedStateOf {
            var cost = 0L
            for (order in orders) {
                val prod = productList.find { it.name == order.productName }
                if (prod != null) {
                    cost += prod.price * order.quantity
                }
            }
            cost
        }
    }

    // Dropdown UI states
    var consoleExpanded by remember { mutableStateOf(false) }
    var controllersExpanded by remember { mutableStateOf(false) }
    var buffetExpanded by remember { mutableStateOf(false) }
    var showBuffetDetails by remember { mutableStateOf(false) }

    // Start-time pricing inputs are independent: payment can imply a duration, while a manually entered duration can be used without payment.
    var payInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var showBehaviorDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("station_card_${station.id}")
            ,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFree) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(2.dp, if (isFree) MaterialTheme.colorScheme.outline.copy(alpha = 0.6f) else borderColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header: Minimal Station Number Badge & Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Station Number inside a small circle / neon minimal badge (e.g. "1" or "2")
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = when {
                                isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                isPaused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = 1.2.dp,
                            color = when {
                                isRunning -> MaterialTheme.colorScheme.primary
                                isPaused -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$displayStationNumber",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.primary
                            isPaused -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
            }

            // Customer Selection Row for Station
            val allStations by viewModel.stationStates.collectAsState()
            val allCustomers by viewModel.customers.collectAsState()
            val selectedCustomerNames = station.getCustomerNames()
            val selectedCustomerIds = station.getCustomerIds()
            var pendingCustomerPrepayments by remember(station.id, station.customerPrepaymentsJson) {
                mutableStateOf(station.getEffectiveCustomerPrepaymentsMap())
            }

            val occupiedCustomerStationMap = remember(allStations, station.id) {
                val map = mutableMapOf<Long, Int>()
                allStations.filter { it.id != station.id && it.status != "FREE" }.forEach { other ->
                    other.getCustomerIds().filter { it > 0 }.forEach { cid ->
                        map[cid] = other.id
                    }
                }
                map
            }

            val conflictingCustomerId = remember(selectedCustomerIds, occupiedCustomerStationMap) {
                selectedCustomerIds.firstOrNull { occupiedCustomerStationMap.containsKey(it) }
            }

            var showCustomerSelectionDialog by remember { mutableStateOf(false) }
            var showPayerAllocationDialog by remember { mutableStateOf(false) }
            var showBehaviorDialog by remember { mutableStateOf(false) }
            var showStationPauseDialog by remember { mutableStateOf(false) }
            var showMultiPrepaymentDialog by remember { mutableStateOf(false) }
            var pendingFinishAction by remember { mutableStateOf(false) }
            var isFinishingAction by remember(station.status) { mutableStateOf(false) }
            var selectedProductForBuffetCustomer by remember { mutableStateOf<String?>(null) }

            if (showCustomerSelectionDialog) {
                CustomerSelectionDialog(
                    maxControllers = station.controllerCount,
                    maxSelectableCustomers = if (viewModel.isTrialUser) 4 else null,
                    currentSelectedIds = station.getCustomerIds(),
                    allCustomers = allCustomers,
                    occupiedCustomerStationMap = occupiedCustomerStationMap,
                    onDismiss = { showCustomerSelectionDialog = false },
                    onConfirm = { ids, names ->
                        viewModel.updateStationSelectedCustomers(station.id, ids, names)
                        showCustomerSelectionDialog = false
                    }
                )
            }

            if (showBehaviorDialog) {
                val stationCustomers = station.getStationCustomers(allCustomers)
                com.example.ui.CustomerBehaviorDialog(
                    stationCustomers = stationCustomers,
                    onDismiss = { showBehaviorDialog = false },
                    onSubmit = { cid, pts, reason ->
                        viewModel.addCustomerPointsWithLog(cid, pts, reason)
                        showBehaviorDialog = false
                    }
                )
            }

            if (showMultiPrepaymentDialog) {
                val stationCustomers = selectedCustomerIds.mapIndexed { index, id ->
                    val name = selectedCustomerNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                        ?: allCustomers.find { it.id == id }?.fullName
                        ?: if (id < 0) "مهمان " + (-id) else "مشتری " + id
                    Customer(id = id, fullName = name, phoneNumber = "")
                }
                MultiCustomerPrepaymentDialog(
                    selectedCustomers = stationCustomers,
                    initialPrepayments = pendingCustomerPrepayments,
                    totalPrepayment = payInput.filter(Char::isDigit).toLongOrNull() ?: 0L,
                    onDismiss = { showMultiPrepaymentDialog = false },
                    onConfirm = { prepayMap, totalSum ->
                        showMultiPrepaymentDialog = false
                        pendingCustomerPrepayments = prepayMap
                        payInput = totalSum.toInt().toString()
                        viewModel.updateStationCustomerPrepayments(station.id, prepayMap, totalSum)
                    }
                )
            }

            if (showPayerAllocationDialog) {
                val stationCustomers = station.getStationCustomers(allCustomers)
                PayerAllocationDialog(
                    customers = stationCustomers,
                    totalCost = gameCostExact.add(BigDecimal.valueOf(foodCost)),
                    onDismiss = {
                        showPayerAllocationDialog = false
                        pendingFinishAction = false
                    },
                    onConfirm = { payerIds, payerNames ->
                        showPayerAllocationDialog = false
                        if (pendingFinishAction) {
                            pendingFinishAction = false
                            isFinishingAction = true
                            viewModel.finishStation(station.id, payerIds, payerNames)
                        }
                    }
                )
            }

            if (showStationPauseDialog) {
                val stationCustomers = station.getStationCustomers(allCustomers)
                StationPauseDialog(
                    stationId = station.id,
                    stationCustomers = stationCustomers,
                    allCustomers = allCustomers,
                    gameCost = gameCost,
                    prepaymentTotal = station.prepaymentAmount,
                    customerPrepaymentsMap = station.getEffectiveCustomerPrepaymentsMap(),
                    onDismiss = { showStationPauseDialog = false },
                    onSimplePause = {
                        showStationPauseDialog = false
                        onPause()
                    },
                    onCommitSegmentAndPause = { payerIds, payerNames ->
                        showStationPauseDialog = false
                        viewModel.commitSegmentAndPause(station.id, payerIds, payerNames)
                    },
                    onCommitSegmentAndContinue = { payerIds, payerNames ->
                        showStationPauseDialog = false
                        viewModel.commitSegmentAndContinue(station.id, payerIds, payerNames)
                    }
                )
            }

            if (selectedProductForBuffetCustomer != null) {
                val stationCustomers = station.getStationCustomers(allCustomers)
                BuffetCustomerTargetDialog(
                    productName = selectedProductForBuffetCustomer!!,
                    customers = stationCustomers,
                    onDismiss = { selectedProductForBuffetCustomer = null },
                    onConfirm = { targetId, targetName ->
                        viewModel.addBuffetOrderWithCustomer(station.id, selectedProductForBuffetCustomer!!, targetId, targetName)
                        selectedProductForBuffetCustomer = null
                    }
                )
            }

            // Customer Selector Button Row
            val context = LocalContext.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isPaused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else if (isRunning) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = if (isPaused) 1.dp else 0.dp,
                        color = if (isPaused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        if (isRunning) {
                            Toast.makeText(context, "جهت تغییر یا افزودن مشتری، ابتدا بازی را استپ (توقف) کنید.", Toast.LENGTH_SHORT).show()
                        } else {
                            showCustomerSelectionDialog = true
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (conflictingCustomerId != null) Icons.Default.Warning else if (isRunning) Icons.Default.Lock else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (conflictingCustomerId != null) MaterialTheme.colorScheme.error else if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (selectedCustomerNames.isNotEmpty()) {
                            if (lang == "fa") "مخاطبان (${selectedCustomerNames.size} نفر): ${selectedCustomerNames.joinToString(", ")}"
                            else "Contacts (${selectedCustomerNames.size}): ${selectedCustomerNames.joinToString(", ")}"
                        } else {
                            if (lang == "fa") "انتخاب مخاطب / مهمان" else "Select Contact / Guest"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (conflictingCustomerId != null) MaterialTheme.colorScheme.error else if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    if (conflictingCustomerId != null) {
                        val busyStationId = occupiedCustomerStationMap[conflictingCustomerId]
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "⛔ فعال در جایگاه $busyStationId",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isPaused) {
                        Text(
                            text = if (lang == "fa") "(تغییر - استپ شده)" else "(Edit - Paused)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isRunning) {
                        Text(
                            text = if (lang == "fa") "(قفل در حین بازی)" else "(Locked during game)",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Lock else Icons.Default.Edit,
                        contentDescription = "Select Customer",
                        modifier = Modifier.size(12.dp),
                        tint = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Configuration fields (Console Type, Controllers, Payment Field)

            if (isFree) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Console Type Dropdown
                        Box(modifier = Modifier.weight(1.3f)) {
                            OutlinedButton(
                                onClick = { consoleExpanded = true },
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = formatConsoleName(station.consoleType).ifEmpty { androidx.compose.ui.res.stringResource(com.example.R.string.ext_select_8) },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = consoleExpanded,
                                onDismissRequest = { consoleExpanded = false }
                            ) {
                                consoleList.forEach { console ->
                                    DropdownMenuItem(
                                        text = { Text(formatConsoleName(console.name), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            onConsoleChange(console.name)
                                            consoleExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Controllers count Dropdown
                        Box(modifier = Modifier.weight(1.0f)) {
                            OutlinedButton(
                                onClick = { controllersExpanded = true },
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                ),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🎮 ${station.controllerCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = controllersExpanded,
                                onDismissRequest = { controllersExpanded = false }
                            ) {
                                listOf(1, 2, 3, 4).forEach { count ->
                                    DropdownMenuItem(
                                        text = { Text("🎮 $count", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            onControllersChange(count)
                                            controllersExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Prepayment Amount Input (Full Width for elegant compact spacing!)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (hourlyRate > 0) {
                            Text(
                                text = if (lang == "fa") "نرخ: %,d تومان / ساعت".format(Locale.US, hourlyRate) else "Rate: %,d/hr".format(Locale.US, hourlyRate),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        val inputPrice = payInput.toLongOrNull() ?: 0L
                        val inputMinutes = durationInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val amountDurationMillis = ExactBilling.durationMillisForAmount(inputPrice, hourlyRate)
                        val durationCostExact = ExactBilling.costForMinutes(hourlyRate, inputMinutes)
                        if (inputPrice > 0L && hourlyRate > 0L) {
                            Text(
                                text = if (lang == "fa") "زمان معادل: ${formatTime(amountDurationMillis)}" else "Equivalent time: ${formatTime(amountDurationMillis)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                        if (inputMinutes > 0 && hourlyRate > 0L) {
                            Text(
                                text = if (lang == "fa") "هزینه این مدت: ${ExactBilling.formatToman(durationCostExact)}" else "Cost for this duration: ${ExactBilling.formatToman(durationCostExact)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = payInput,
                                onValueChange = { value ->
                                    payInput = NumberConverter.toEnglishDigits(value).filter(Char::isDigit)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f).height(32.dp).border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                                decorationBox = { innerTextField ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (payInput.isEmpty()) Text(text = Localization.get("prepay_amount", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                                        innerTextField()
                                    }
                                }
                            )
                            BasicTextField(
                                value = durationInput,
                                onValueChange = { value ->
                                    durationInput = NumberConverter.toEnglishDigits(value).filter(Char::isDigit)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(0.72f).height(32.dp).border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
                                decorationBox = { innerTextField ->
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        if (durationInput.isEmpty()) Text(text = if (lang == "fa") "مدت (دقیقه)" else "Minutes", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        if (selectedCustomerNames.size >= 2) {
                            TextButton(
                                onClick = { showMultiPrepaymentDialog = true },
                                modifier = Modifier.padding(top = 1.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تخصیص پیش‌پرداخت بین مخاطبان (${selectedCustomerNames.size} نفر)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                // When paused, allow changing console and controller. When running, lock them with notification toast.
                var activeControllerMenuExpanded by remember { mutableStateOf(false) }
                var activeConsoleMenuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isPaused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, if (isPaused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Console Type Dropdown
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (isRunning) {
                                        Toast.makeText(context, "جهت تغییر کنسول، ابتدا بازی را استپ (توقف) کنید.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        activeConsoleMenuExpanded = true
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Lock else Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = formatConsoleName(station.consoleType),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Lock else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isPaused) {
                            DropdownMenu(
                                expanded = activeConsoleMenuExpanded,
                                onDismissRequest = { activeConsoleMenuExpanded = false }
                            ) {
                                (if (viewModel.isTrialUser) listOf("PlayStation 5") else listOf("PlayStation 5", "PlayStation 4", "شبیه‌ساز رانندگی (Sim)")).forEach { cType ->
                                    DropdownMenuItem(
                                        text = { Text(formatConsoleName(cType), fontSize = 12.sp) },
                                        onClick = {
                                            onConsoleChange(cType)
                                            activeConsoleMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Controller Count Dropdown (Active ONLY when paused, locked when running)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isPaused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (isPaused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.clickable {
                                if (isRunning) {
                                    Toast.makeText(context, "جهت تغییر تعداد دسته، ابتدا بازی را استپ (توقف) کنید.", Toast.LENGTH_SHORT).show()
                                } else {
                                    activeControllerMenuExpanded = true
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🎮 ${station.controllerCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = if (isRunning) Icons.Default.Lock else Icons.Default.ArrowDropDown,
                                    contentDescription = "Change Controllers",
                                    tint = if (isRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        if (isPaused) {
                            DropdownMenu(
                                expanded = activeControllerMenuExpanded,
                                onDismissRequest = { activeControllerMenuExpanded = false }
                            ) {
                                (1..4).forEach { count ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "🎮 $count ${if (station.controllerCount == count) "✓" else ""}",
                                                fontSize = 12.sp,
                                                fontWeight = if (station.controllerCount == count) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            onControllersChange(count)
                                            activeControllerMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Compact Financial Box (ONLY DISPLAYED WHEN STATION IS ACTIVE, NOT FREE!)
            if (!isFree) {
                Spacer(modifier = Modifier.height(4.dp))
                val totalCostExact = gameCostExact.add(BigDecimal.valueOf(foodCost))
                val totalCost = totalCostExact.toLong()
                val prepayment = station.prepaymentAmount
                val hasPrepayment = prepayment > 0
                val prepaymentExact = BigDecimal.valueOf(prepayment)
                // Buffet is separate from prepaid station time. Only game time consumes the initial payment.
                val consumedGameCostExact = gameCostExact
                val isOverPrepayment = hasPrepayment && consumedGameCostExact > prepaymentExact
                val surplusAmountExact = if (isOverPrepayment) consumedGameCostExact.subtract(prepaymentExact) else BigDecimal.ZERO
                val remainingPrepayExact = if (hasPrepayment) prepaymentExact.subtract(consumedGameCostExact).max(BigDecimal.ZERO) else BigDecimal.ZERO
                val surplusAmount = surplusAmountExact.toLong()
                val remainingPrepay = remainingPrepayExact.toLong()

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isTimeUp || isOverPrepayment) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        1.dp,
                        if (isTimeUp || isOverPrepayment) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val timerText = when {
                            isTimeUp -> "+${formatTime(overtimeMillis)}"
                            isCountdown -> formatTime(remainingMillis)
                            else -> formatTime(elapsedMillis)
                        }

                        // Row 1: Live Timer & Hourly Rate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isTimeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = timerText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isTimeUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            if (hourlyRate > 0) {
                                Text(
                                    text = if (lang == "fa") "نرخ: %,d ت/س".format(Locale.US, hourlyRate) else "Rate: %,d/h".format(Locale.US, hourlyRate),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.8.dp)

                        // Row 2: هزینه بازی (Game Cost)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == "fa") "هزینه بازی:" else "Game Cost:",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (lang == "fa") ExactBilling.formatToman(gameCostExact) else ExactBilling.formatToman(gameCostExact).replace(" تومان", " T"),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Row 3: مقدار پرداختی اولیه (Initial Prepayment)
                        if (hasPrepayment) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (lang == "fa") "مقدار پرداختی اولیه:" else "Initial Payment:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (lang == "fa") "%,d تومان".format(Locale.US, prepayment) else "%,d T".format(Locale.US, prepayment),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Row 4: مقدار هزینه بوفه (Buffet Cost)
                        if (foodCost > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (lang == "fa") "مقدار هزینه بوفه:" else "Buffet Cost:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (lang == "fa") "%,d تومان".format(Locale.US, foodCost) else "%,d T".format(Locale.US, foodCost),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Row 5: هزینه کل (Total Cost)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (lang == "fa") "هزینه کل:" else "Total Cost:",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (lang == "fa") ExactBilling.formatToman(totalCostExact) else ExactBilling.formatToman(totalCostExact).replace(" تومان", " T"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Row 6: مازاد بر پرداختی اولیه یا مانده
                        if (hasPrepayment) {
                            if (isOverPrepayment) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == "fa") "مازاد بر پرداختی اولیه:" else "Over Initial Prepay:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (lang == "fa") "+%,d تومان".format(Locale.US, surplusAmount) else "+%,d T".format(Locale.US, surplusAmount),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (lang == "fa") "مانده از پرداختی اولیه:" else "Remaining Balance:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = if (lang == "fa") "%,d تومان".format(Locale.US, remainingPrepay) else "%,d T".format(Locale.US, remainingPrepay),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Controls Row (START, STOP/RESUME, FINISH, Buffet trigger)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // START / STOP / RESUME Button
                when {
                    isFree -> {
                        Button(
                            enabled = !isStarting,
                            onClick = {
                                if (isStarting) return@Button
                                if (conflictingCustomerId != null) {
                                    val busyStationId = occupiedCustomerStationMap[conflictingCustomerId]
                                    val conflictCust = allCustomers.find { it.id == conflictingCustomerId }
                                    val name = conflictCust?.fullName ?: "مشتری"
                                    Toast.makeText(
                                        context,
                                        "امکان شروع وجود ندارد؛ $name هم‌اکنون در جایگاه $busyStationId دارای نشست فعال است.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                focusManager.clearFocus()
                                onStart(
                                    payInput,
                                    durationInput,
                                    selectedCustomerIds,
                                    selectedCustomerNames,
                                    pendingCustomerPrepayments
                                )
                                viewModel.requestFirstStartServerClockWarning()
                                payInput = ""
                                durationInput = ""
                            },
                            modifier = Modifier.weight(1.2f).height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isStarting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isStarting) "در حال شروع…" else Localization.get("start", lang).uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    isRunning -> {
                        Button(
                            onClick = { 
                                showStationPauseDialog = true 
                            },
                            modifier = Modifier.weight(1.2f).height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = Localization.get("stop", lang).uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    isPaused -> {
                        Button(
                            onClick = {
                                if (conflictingCustomerId != null) {
                                    val busyStationId = occupiedCustomerStationMap[conflictingCustomerId]
                                    val conflictCust = allCustomers.find { it.id == conflictingCustomerId }
                                    val name = conflictCust?.fullName ?: "مشتری"
                                    Toast.makeText(
                                        context,
                                        "امکان ادامه وجود ندارد؛ $name هم‌اکنون در جایگاه $busyStationId دارای نشست فعال است.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                onResume()
                            },
                            modifier = Modifier.weight(1.2f).height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = Localization.get("resume", lang).uppercase(Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // FINISH Button (only visible or active when not Free)
                Button(
                    onClick = {
                        if (isFinishingAction) return@Button
                        val cIds = station.getCustomerIds()
                        if (cIds.isNotEmpty() && station.status != "PAUSED") {
                            pendingFinishAction = true
                            showPayerAllocationDialog = true
                        } else {
                            isFinishingAction = true
                            onFinish()
                        }
                    },
                    enabled = !isFree && !isFinishingAction,
                    modifier = Modifier.weight(1.1f).height(30.dp),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    if (isFinishingAction) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = if (isFinishingAction) "..." else Localization.get("finish", lang).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (!isFree && station.getCustomerIds().isNotEmpty()) {
                    Surface(
                        onClick = { showBehaviorDialog = true },
                        modifier = Modifier.size(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF673AB7),
                        contentColor = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Star, contentDescription = "ثبت رفتار", modifier = Modifier.size(13.dp), tint = Color.White)
                        }
                    }
                }
                // Small Buffet Dropdown Button
                if (!isFree) {
                    Box(modifier = Modifier.weight(1.1f)) {
                        Button(
                            onClick = { buffetExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(30.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fastfood,
                                contentDescription = "Add Buffet",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.ext_cafe_15),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        DropdownMenu(
                            expanded = buffetExpanded,
                            onDismissRequest = { buffetExpanded = false }
                        ) {
                            if (productList.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.ext_no_items_found_16), fontSize = 11.sp) },
                                    onClick = { buffetExpanded = false }
                                )
                            } else {
                                productList.forEach { prod ->
                                    DropdownMenuItem(
                                        text = { Text("${prod.name} (%,d)".format(Locale.US, prod.price), fontSize = 11.sp) },
                                        onClick = {
                                            val currentCustomerIds = station.getCustomerIds()
                                            if (currentCustomerIds.size == 1) {
                                                val cId = currentCustomerIds.first()
                                                val cName = station.selectedCustomerNamesStr.split(",").firstOrNull()?.trim() ?: "مشتری"
                                                viewModel.addBuffetOrderWithCustomer(station.id, prod.name, cId, cName)
                                            } else if (currentCustomerIds.size > 1) {
                                                selectedProductForBuffetCustomer = prod.name
                                            } else {
                                                onAddProduct(prod.name)
                                            }
                                            buffetExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Station Segments Live Report (Report #18 Requirement)
            val stationSegments = station.getSegmentsList()
            if (stationSegments.isNotEmpty() || !isFree) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.toggleStationReportExpanded(station.id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (lang == "fa") "گزارش زنده کارکرد (${stationSegments.size} بخش)" else "Live Usage Report (${stationSegments.size} segments)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (station.isReportExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Report",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                if (station.isReportExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (stationSegments.isEmpty()) {
                            Text(
                                text = if (lang == "fa") "هنوز بخشی ثبت نشده است (با زدن دکمه توقف می‌توانید رکورد فعلی را ثبت کنید)"
                                else "No segments recorded yet (pause to record current segment)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        } else {
                            stationSegments.forEach { seg ->
                                val sTime = java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(seg.startTimeMs))
                                val eTime = java.text.SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(seg.endTimeMs))
                                val defaultGuest = if (lang == "fa") "عمومی" else "Guest"
                                val payersStr = seg.payerCustomerNames.filter { it.isNotBlank() }.joinToString(", ")
                                    .ifEmpty { seg.payerCustomerName ?: seg.customerNames.joinToString().ifEmpty { defaultGuest } }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == "fa") "بخش ${seg.segmentIndex}: ${formatConsoleName(seg.consoleType)} (🎮${seg.controllerCount})"
                                                else "Segment ${seg.segmentIndex}: ${formatConsoleName(seg.consoleType)} (🎮${seg.controllerCount})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = if (lang == "fa") "زمان: $sTime تا $eTime (${seg.durationMinutes} د) | مخاطب: $payersStr"
                                                else "Time: $sTime to $eTime (${seg.durationMinutes}m) | Contact: $payersStr",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "%,d".format(Locale.US, seg.cost),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Buffet Items List (Directly inside Card, below controls)
            if (orders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Clickable header for collapsible details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable { showBuffetDetails = !showBuffetDetails }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fastfood,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (lang == "en") "Ordered buffet items (${orders.sumOf { it.quantity }})" else "اقلام سفارش داده شده بوفه (${orders.sumOf { it.quantity }})",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = if (showBuffetDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Details",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showBuffetDetails) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        orders.forEach { order ->
                            val prod = productList.find { it.name == order.productName }
                            val itemPrice = prod?.price ?: 0L
                            val totalItemPrice = itemPrice * order.quantity

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = order.productName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (lang == "fa") "%,d هرکدام | مجموع: %,d".format(Locale.US, itemPrice, totalItemPrice)
                                               else "%,d ea | Total: %,d".format(Locale.US, itemPrice, totalItemPrice),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Quantity modifiers (+ / -)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { onDecrementProduct(order.productName) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(12.dp))
                                    }
                                    Text(
                                        text = order.quantity.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { onIncrementProduct(order.productName) },
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(12.dp))
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

private fun exactPrepaymentDurationMillis(price: Long, hourlyRate: Long, fallbackMinutes: Int = 0): Long {
    if (price <= 0L || hourlyRate <= 0L) return fallbackMinutes.coerceAtLeast(0).toLong() * 60_000L
    return try {
        BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(3_600_000L))
            .divide(BigDecimal.valueOf(hourlyRate), 0, RoundingMode.DOWN)
            .longValueExact().coerceAtLeast(0L)
    } catch (_: ArithmeticException) {
        0L
    }
}

// Format milliseconds as HH:MM:SS
private fun formatTime(millis: Long): String {
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatConsoleName(name: String): String {
    val trimmed = name.trim()
    if (trimmed.contains("PlayStation 5", ignoreCase = true) || trimmed.contains("PS5", ignoreCase = true)) {
        return "PS5"
    }
    if (trimmed.contains("PlayStation 4", ignoreCase = true) || trimmed.contains("PS4", ignoreCase = true)) {
        return "PS4"
    }
    if (trimmed.contains("شبیه ساز رانندگی", ignoreCase = true) || trimmed.contains("Sim Racing", ignoreCase = true) || trimmed.contains("SimD", ignoreCase = true)) {
        return "SimD"
    }
    return trimmed
}

@Composable
fun StationFilterChip(
    label: String,
    count: Int,
    circleColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier.testTag("filter_chip_${label}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = circleColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: $count",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServerClockWarningOverlay(
    serverTimeMillis: Long,
    lang: String,
    onDismiss: () -> Unit
) {
    var liveServerTime by remember(serverTimeMillis) { mutableLongStateOf(serverTimeMillis) }
    val referenceElapsed = remember(serverTimeMillis) { SystemClock.elapsedRealtime() }

    LaunchedEffect(serverTimeMillis) {
        while (true) {
            liveServerTime = serverTimeMillis + (SystemClock.elapsedRealtime() - referenceElapsed)
            delay(1000L)
        }
    }

    val formatter = remember {
        SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(20.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (lang == "fa") "⏱️ هماهنگی ساعت با سرور" else "⏱️ Server Clock Synchronization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formatter.format(Date(liveServerTime)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == "fa")
                        "ساعت نمایش‌داده‌شده، ساعت رسمی تهران است و مستقل از ساعت گوشی به‌صورت خودکار با سرور همگام می‌شود. محاسبه نهایی هزینه بر اساس زمان معتبر ثبت‌شده در سرور انجام می‌شود."
                    else
                        "The displayed clock is official Tehran time and is synchronized with the server independently of the phone clock. Final billing is based on valid server-recorded timing.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
                Text(
                    text = if (lang == "fa") "برای بستن، فقط یک‌بار در هر قسمت از صفحه لمس کنید." else "Tap anywhere on the screen once to close.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}




@Composable
private fun SuperManagerOfflineBanner(
    lang: String,
    remainingSeconds: Long
) {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L
    val timer = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF8A1C1C)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "fa") "⚠️ اتصال سرور قطع است" else "⚠️ Server connection is offline",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB91C1C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = timer,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Text(
                text = if (lang == "fa")
                    "ارتباط اینترنتی برنامه با سرور GameNexa برقرار نیست. تا ۲۴ ساعت فرصت اتصال مجدد دارید. VPN، اینترنت موبایل، Wi‑Fi یا پروکسی را بررسی کنید."
                else
                    "GameNexa cannot reach its server. You have up to 24 hours to reconnect. Check Wi‑Fi, mobile data, VPN or proxy.",
                color = Color.White.copy(alpha = 0.96f),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun SubscriptionWarningBanner(
    state: AppAccessState.Allowed,
    lang: String,
    viewModel: GameNetViewModel,
    onNavigateToSubscription: () -> Unit
) {
    val planType = state.planType.trim()
    val expiresAt = state.expiresAt ?: Long.MAX_VALUE

    if (planType.equals("SUPER_MANAGER", ignoreCase = true) || expiresAt == Long.MAX_VALUE) {
        return
    }

    var remainingText by remember(expiresAt) { mutableStateOf("") }
    var showBanner by remember(expiresAt, planType) { mutableStateOf(false) }

    LaunchedEffect(expiresAt, planType) {
        if (expiresAt == Long.MAX_VALUE) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            val rem = expiresAt - now
            if (rem <= 0) {
                showBanner = true
                remainingText = "00:00:00:00"
                viewModel.handleAccessDenied()
                onNavigateToSubscription()
                break
            }

            val remDays = rem / 86400_000L
            val remHours = (rem % 86400_000L) / 3600_000L
            val remMins = (rem % 3600_000L) / 60_000L
            val remSecs = (rem % 60_000L) / 1000L

            val timerFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", remDays, remHours, remMins, remSecs)

            if (planType.equals("TRIAL", ignoreCase = true) || viewModel.isTrialUser) {
                showBanner = true
                remainingText = timerFormatted
            } else {
                val planUpper = planType.uppercase()
                val is1Year = planUpper.contains("1_YEAR") || planUpper.contains("YEAR1") ||
                              planUpper.contains("12_MONTH") || planUpper.contains("YEAR") ||
                              planType.contains("12 ماهه") || planType.contains("12 ماهه") ||
                              planType.contains("1 ساله") || planType.contains("1 ساله") ||
                              planUpper.contains("VIP") || planType.contains("وی ای پی")

                val is3Months = planUpper.contains("3_MONTH") || planUpper.contains("MONTH3") ||
                                planUpper.contains("90_DAYS") || planUpper.contains("QUARTER") ||
                                planType.contains("3 ماهه") || planType.contains("3 ماهه")

                val is1Month = planUpper.contains("1_MONTH") || planUpper.contains("MONTH1") ||
                               planUpper.contains("30_DAYS") || planUpper.contains("MONTH") ||
                               planType.contains("1 ماهه") || planType.contains("1 ماهه")

                val warningThresholdMs = when {
                    is1Year -> 30L * 86400_000L
                    is3Months -> 10L * 86400_000L
                    is1Month -> 5L * 86400_000L
                    else -> 5L * 86400_000L
                }

                if (rem <= warningThresholdMs) {
                    showBanner = true
                    remainingText = timerFormatted
                } else {
                    showBanner = false
                }
            }

            kotlinx.coroutines.delay(1000)
        }
    }

    if (!showBanner) return

    if (planType.equals("TRIAL", ignoreCase = true) || viewModel.isTrialUser) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.8f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (lang == "fa") "نسخه تست 24 ساعته (محدود به 2 جایگاه)" else "24h Trial Version (Limited to 2 stations)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3B82F6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = remainingText,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF991B1B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == "fa") "⚠️ هشدار اتمام اشتراک مدیریت" else "⚠️ Subscription Expiration Warning",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = remainingText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = if (lang == "fa")
                        "از اعتبار فعلی شما فقط $remainingText مانده، از اطلاعات مشتریان و تنظیمات خود بکآپ تهیه کنید و یا اشتراک خریداری کنید."
                    else
                        "Only $remainingText remaining in your subscription. Please backup customer data/settings or renew your subscription.",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    if (viewModel.serverClockWarningVisible.collectAsState().value) {
        ServerClockWarningOverlay(
            serverTimeMillis = viewModel.serverClockMillis.collectAsState().value ?: System.currentTimeMillis(),
            lang = lang,
            onDismiss = { viewModel.dismissServerClockWarning() }
        )
    }
}
