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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedEntryScreen(
    viewModel: GameNetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Customer Login, 1: Manager/Deputy Login

    // Customer Login Fields
    var custPhone by remember { mutableStateOf("") }
    var custPassword by remember { mutableStateOf("") }
    var custPasswordVisible by remember { mutableStateOf(false) }

    // Admin/Deputy Login Fields
    var adminUser by remember { mutableStateOf("") }
    var adminPass by remember { mutableStateOf("") }
    var adminPassVisible by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var frontMode by remember { mutableIntStateOf(0) }
    val isTrialUsed by viewModel.isTrialUsed.collectAsState()
    val licenseState by viewModel.licenseState.collectAsState()
    var showTrialMessage by remember { mutableStateOf("") }
    
    val isTrialActive = licenseState is LicenseState.Active && (licenseState as LicenseState.Active).planType == "TRIAL"
    var remainingTrialText by remember { mutableStateOf("") }
    
    LaunchedEffect(isTrialActive, licenseState) {
        if (isTrialActive) {
            val expiresAt = (licenseState as LicenseState.Active).expiresAt
            while(true) {
                val rem = expiresAt - System.currentTimeMillis()
                if (rem > 0L) {
                    if (expiresAt >= Long.MAX_VALUE - (365L * 86400_000L) || rem > 10L * 365L * 86400_000L) {
                        remainingTrialText = "نامحدود"
                    } else {
                        val d = rem / 86400_000L
                        val h = (rem % 86400_000L) / 3_600_000L
                        val m = (rem % 3_600_000L) / 60_000L
                        val s = (rem % 60_000L) / 1000L
                        remainingTrialText = String.format(java.util.Locale.US, "%02d:%02d:%02d:%02d", d, h, m, s)
                    }
                } else {
                    remainingTrialText = "منقضی شده"
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    if (frontMode == 2) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1E293B),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { frontMode = 0 }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "بازگشت به صفحه خوشامدگویی",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "خرید و تمدید اشتراک گیم‌نکسا",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SubscriptionActivationScreen(viewModel = viewModel, isEmbedded = false)
            }
        }
    } else {
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E17),
                        Color(0xFF111827),
                        Color(0xFF030712)
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1F2937).copy(alpha = 0.95f)
            ),
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFF3B82F6),
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6)
                    )
                )
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (frontMode == 0) {
                    // Header Logo
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "GameNexa Logo",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Text(
                        text = "سامانه جامع GameNexa",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            if (isTrialActive) {
                                viewModel.activateFreeTrial { success, msg -> showTrialMessage = msg }
                            } else if (isTrialUsed) {
                                showTrialMessage = "شما قبلاً از تست 24 ساعته استفاده کرده‌اید."
                            } else {
                                viewModel.activateFreeTrial { success, msg ->
                                    showTrialMessage = msg
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTrialActive) Color(0xFF3B82F6) else if (isTrialUsed) Color.Gray else Color(0xFF10B981)
                        )
                    ) {
                        Text(
                            text = if (isTrialActive) "ورود به تست ($remainingTrialText باقیمانده)" else if (isTrialUsed) "تست 24 ساعته (استفاده شده)" else "اجرای تست 24 ساعته",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (showTrialMessage.isNotBlank()) {
                        Text(showTrialMessage, color = if (isTrialUsed) Color.Red else Color.Green, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    
                    Button(
                        onClick = { frontMode = 1 },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("ورود به حساب کاربری", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { frontMode = 2 },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Text("خرید اشتراک", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        IconButton(onClick = { frontMode = 0 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    }

                // Header Logo
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "GameNexa Logo",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = "سامانه جامع GameNexa",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "ورود اختصاصی مشتریان، معاونین و مدیریت سالن",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center
                )

                // Tab Selector (Customer vs Manager/Deputy)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF111827),
                    contentColor = Color(0xFF60A5FA),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            errorMessage = null
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("ورود مشتری", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        modifier = Modifier.testTag("entry_tab_customer")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            errorMessage = null
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("مدیریت / معاون", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        modifier = Modifier.testTag("entry_tab_admin")
                    )
                }

                // Error Message Display
                AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF7F1D1D).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = errorMessage ?: "",
                                color = Color(0xFFFCA5A5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // CUSTOMER LOGIN FORM
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "نام کاربری همان شماره تلفن شما است که توسط مدیریت مجموعه ثبت شده است.",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Phone Number Field (Username or Phone or Name)
                    OutlinedTextField(
                        value = custPhone,
                        onValueChange = {
                            custPhone = it
                            errorMessage = null
                        },
                        label = { Text("شماره همراه یا نام کاربری", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                        placeholder = { Text("مثلاً 09123456789 یا نام ثبت شده", color = Color(0xFF9CA3AF), fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF60A5FA))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cust_login_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF4B5563)
                        )
                    )

                    // Password Field
                    OutlinedTextField(
                        value = custPassword,
                        onValueChange = {
                            custPassword = it
                            errorMessage = null
                        },
                        label = { Text("رمز عبور اختصاصی", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA))
                        },
                        trailingIcon = {
                            IconButton(onClick = { custPasswordVisible = !custPasswordVisible }) {
                                Icon(
                                    imageVector = if (custPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "تغییر دید رمز",
                                    tint = Color(0xFF9CA3AF)
                                )
                            }
                        },
                        visualTransformation = if (custPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cust_login_pass_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF4B5563)
                        )
                    )

                    Button(
                        onClick = {
                            if (custPhone.trim().isBlank() || custPassword.trim().isBlank()) {
                                errorMessage = "لطفاً شماره همراه و رمز عبور را وارد کنید."
                                return@Button
                            }
                            isLoggingIn = true
                            viewModel.loginCustomer(custPhone.trim(), custPassword.trim()) { success, msg ->
                                isLoggingIn = false
                                if (!success) {
                                    errorMessage = msg ?: "خطا در ورود به حساب"
                                } else {
                                    Toast.makeText(context, "خوش آمدید!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isLoggingIn,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("cust_login_submit_btn")
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ورود به حساب مشتری", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                } else {
                    // MANAGER / DEPUTY LOGIN FORM
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF111827),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ورود با شناسه مدیریت اصلی (Super Manager) یا مدیر اجرایی (معاون)",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = adminUser,
                        onValueChange = {
                            adminUser = it
                            errorMessage = null
                        },
                        label = { Text("نام کاربری یا شناسه مدیریت", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF60A5FA))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_user_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF4B5563)
                        )
                    )

                    OutlinedTextField(
                        value = adminPass,
                        onValueChange = {
                            adminPass = it
                            errorMessage = null
                        },
                        label = { Text("رمز عبور", color = Color(0xFFD1D5DB), fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF60A5FA))
                        },
                        trailingIcon = {
                            IconButton(onClick = { adminPassVisible = !adminPassVisible }) {
                                Icon(
                                    imageVector = if (adminPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF)
                                )
                            }
                        },
                        visualTransformation = if (adminPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_login_pass_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF4B5563)
                        )
                    )

                    Button(
                        onClick = {
                            if (adminUser.trim().isBlank() || adminPass.trim().isBlank()) {
                                errorMessage = "لطفاً نام کاربری و رمز عبور را وارد کنید."
                                return@Button
                            }
                            viewModel.authenticateAdmin(adminUser.trim(), adminPass.trim()) { ok, msg ->
                                if (!ok) {
                                    errorMessage = msg ?: "اطلاعات ورود مدیریت یا معاون اشتباه است."
                                } else {
                                    Toast.makeText(context, "ورود موفق مدیریت", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("admin_login_submit_btn")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ورود به پنل مدیریت", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                } // End of frontMode else
            }
        }
    }
    }
}
