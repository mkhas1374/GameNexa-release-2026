package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@Composable
fun GameNexaWelcomeScreen(
    viewModel: GameNetViewModel,
    onContinueToSubscription: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsState()
    val isServerConnected by viewModel.isServerConnected.collectAsState()
    val language by viewModel.language.collectAsState()
    val isFa = language == "fa"
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Register, 1: Login

    // Register Form Fields
    var regUsername by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }

    // Login Form Fields
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // UI Feedback State
    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    var localSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isActivatingTrial by remember { mutableStateOf(false) }

    val isLoading = authState is AuthState.Authenticating

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("gamenexa_welcome_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // App Brand Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "GameNexa",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )

                    Text(
                        text = "GameNexa",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "به GameNexa خوش آمدید",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "برای شروع استفاده از GameNexa، ابتدا حساب کاربری خود را ایجاد کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }



            // Content logic based on AuthState
            when (val currentAuth = authState) {
                is AuthState.Authenticated -> {
                    // USER IS AUTHENTICATED
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth().testTag("authenticated_welcome_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF00C853).copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "حساب کاربری فعال شد",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00C853)
                                    )
                                }
                            }

                            Text(
                                text = "خوش آمدید، ${currentAuth.username}!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (currentAuth.phone.isNotBlank()) {
                                Text(
                                    text = "شماره همراه: ${currentAuth.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                text = "شناسه کاربر: ${currentAuth.userId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "برای شروع استفاده از برنامه، می‌توانید نسخه رایگان را فعال کنید یا پلن‌های اشتراک را مشاهده نمایید:",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )

                            // Button 1: Free Usage
                            Button(
                                onClick = {
                                    if (isActivatingTrial) return@Button
                                    isActivatingTrial = true
                                    viewModel.activateFreeTrial { success, message ->
                                        isActivatingTrial = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                                enabled = !isActivatingTrial,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_free_usage_button")
                            ) {
                                if (isActivatingTrial) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("در حال فعال‌سازی...", color = Color.White)
                                } else {
                                    Icon(
                                        Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "1. استفاده رایگان",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            // Button 2: Buy Subscription / View Plans
                            OutlinedButton(
                                onClick = {
                                    onContinueToSubscription?.invoke()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("welcome_buy_subscription_button")
                            ) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "2. خرید اشتراک",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                else -> {
                    // USER IS NOT AUTHENTICATED -> SHOW REGISTER / LOGIN FORM
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("gamenexa_auth_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Tab Selector
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = {
                                        selectedTab = 0
                                        localErrorMessage = null
                                        localSuccessMessage = null
                                    },
                                    text = {
                                        Text(
                                            text = "ثبت‌نام جدید",
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    modifier = Modifier.testTag("gamenexa_register_tab")
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = {
                                        selectedTab = 1
                                        localErrorMessage = null
                                        localSuccessMessage = null
                                    },
                                    text = {
                                        Text(
                                            text = "ورود قبلی",
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    modifier = Modifier.testTag("gamenexa_login_tab")
                                )
                            }

                            // Error Display
                            val displayError = localErrorMessage ?: (currentAuth as? AuthState.AuthenticationError)?.message
                            AnimatedVisibility(visible = !displayError.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = displayError ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            // Success Display
                            AnimatedVisibility(visible = !localSuccessMessage.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFF00C853).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00C853),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = localSuccessMessage ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF00C853),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (selectedTab == 0) {
                                // REGISTER FORM NOTICE BANNER
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "تمام فیلدها الزامی هستند. در ثبت شماره همراه و ایمیل دقت کنید زیرا لایسنس و خریدهای شما بر اساس این اطلاعات صادر می‌شود.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = regUsername,
                                    onValueChange = {
                                        regUsername = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("نام کاربری (الزامی - حداقل 3 حرف)") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_register_username_input")
                                )

                                OutlinedTextField(
                                    value = regPhone,
                                    onValueChange = {
                                        regPhone = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("شماره موبایل (الزامی - جهت لایسنس)") },
                                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_register_phone_input")
                                )

                                OutlinedTextField(
                                    value = regEmail,
                                    onValueChange = {
                                        regEmail = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("ایمیل (الزامی - جهت صدور فاکتور)") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_register_email_input")
                                )

                                OutlinedTextField(
                                    value = regPassword,
                                    onValueChange = {
                                        regPassword = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("رمز عبور (الزامی - حداقل 4 حرف)") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                            Icon(
                                                imageVector = if (regPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password"
                                            )
                                        }
                                    },
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_register_password_input")
                                )

                                OutlinedTextField(
                                    value = regConfirmPassword,
                                    onValueChange = {
                                        regConfirmPassword = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("تکرار رمز عبور (الزامی)") },
                                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
                                    visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_register_confirm_password_input")
                                )

                                Button(
                                    onClick = {
                                        if (regUsername.trim().length < 3) {
                                            localErrorMessage = "نام کاربری باید حداقل 3 کاراکتر باشد."
                                            return@Button
                                        }
                                        if (regPhone.trim().isBlank() || regPhone.trim().length < 10) {
                                            localErrorMessage = "لطفاً شماره موبایل معتبر (مثال: 09123456789) وارد کنید."
                                            return@Button
                                        }
                                        if (regEmail.trim().isBlank() || !regEmail.contains("@") || !regEmail.contains(".")) {
                                            localErrorMessage = "لطفاً آدرس ایمیل معتبر (مثال: name@domain.com) وارد کنید."
                                            return@Button
                                        }
                                        if (regPassword.trim().length < 8) {
                                            localErrorMessage = "رمز عبور باید حداقل 8 کاراکتر باشد."
                                            return@Button
                                        }
                                        if (regPassword != regConfirmPassword) {
                                            localErrorMessage = "رمز عبور و تکرار آن یکسان نیستند."
                                            return@Button
                                        }

                                        localErrorMessage = null
                                        localSuccessMessage = null
                                        viewModel.registerUser(
                                            username = regUsername,
                                            password = regPassword,
                                            phone = regPhone.trim(),
                                            email = regEmail.trim()
                                        ) { success, msg ->
                                            if (success) {
                                                localSuccessMessage = msg
                                            } else {
                                                localErrorMessage = msg
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("gamenexa_register_submit_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("در حال ایجاد حساب...")
                                    } else {
                                        Icon(
                                            Icons.Default.PersonAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "ثبت‌نام و ورود به GameNexa",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            } else {
                                // LOGIN FORM
                                OutlinedTextField(
                                    value = loginUsername,
                                    onValueChange = {
                                        loginUsername = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("نام کاربری یا شماره موبایل") },
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_login_username_input")
                                )

                                OutlinedTextField(
                                    value = loginPassword,
                                    onValueChange = {
                                        loginPassword = it
                                        localErrorMessage = null
                                    },
                                    label = { Text("رمز عبور") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                            Icon(
                                                imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle password"
                                            )
                                        }
                                    },
                                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth().testTag("gamenexa_login_password_input")
                                )

                                Button(
                                    onClick = {
                                        if (loginUsername.isBlank() || loginPassword.isBlank()) {
                                            localErrorMessage = "لطفاً نام کاربری و رمز عبور را وارد کنید."
                                            return@Button
                                        }
                                        localErrorMessage = null
                                        localSuccessMessage = null
                                        viewModel.loginUser(loginUsername, loginPassword) { success, msg ->
                                            if (success) {
                                                localSuccessMessage = msg
                                            } else {
                                                localErrorMessage = msg
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("gamenexa_login_submit_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("در حال ورود...")
                                    } else {
                                        Icon(
                                            Icons.Default.Login,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "ورود به حساب",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
