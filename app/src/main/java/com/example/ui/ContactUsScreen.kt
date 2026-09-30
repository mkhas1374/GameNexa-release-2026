package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.Localization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactUsScreen(viewModel: GameNetViewModel) {
    val lang by viewModel.language.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isRtl = lang == "fa"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Localization.get("contact_us", lang),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                ),
                modifier = Modifier.testTag("contact_us_top_bar")
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Large visual Icon at top
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactSupport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                }

                val versionName = remember(context) {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        pInfo.versionName ?: "1.0.2"
                    } catch (e: Exception) {
                        "1.0.2"
                    }
                }

                Text(
                    text = if (lang == "fa") "نسخه $versionName" else "Version $versionName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Product by MimKhas studio ❤️",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = Localization.get("app_name", lang),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                // Info Cards block
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_info_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ContactDetailRow(
                            icon = Icons.Default.Send,
                            label = if (isRtl) "ارتباط با «تلگرام» ادمین" else "Contact Telegram Admin",
                            value = "@real_mimkhas",
                            isRtl = isRtl,
                            isClickable = true,
                            onClick = { openTelegramAdmin(context) }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ContactDetailRow(
                            icon = Icons.Default.Chat,
                            label = if (isRtl) "ارتباط با «بله» ادمین" else "Contact Bale Admin",
                            value = "@Real_MimKhas",
                            isRtl = isRtl,
                            isClickable = true,
                            onClick = { openBaleAdmin(context) }
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ContactDetailRow(
                            icon = Icons.Default.Person,
                            label = Localization.get("developer", lang),
                            value = "MimKhas",
                            isRtl = isRtl
                        )

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        ContactDetailRow(
                            icon = Icons.Default.Email,
                            label = Localization.get("email_gateway", lang),
                            value = "mkhas1374m@gmail.com",
                            isRtl = isRtl,
                            isClickable = true,
                            onClick = { sendEmail(context) }
                        )
                    }
                }



                // Description Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RateReview,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )

                        Text(
                            text = Localization.get("contact_desc", lang),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 24.sp,
                                letterSpacing = 0.15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Thanks / Appreciation footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = Localization.get("thanks_for_choosing", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ContactDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isRtl: Boolean,
    isClickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    val rowModifier = if (isClickable) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isRtl) Arrangement.End else Arrangement.Start
    ) {
        if (!isRtl) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            if (isClickable) {
                Icon(
                    imageVector = Icons.Default.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            if (isClickable) {
                Icon(
                    imageVector = Icons.Default.ArrowBackIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isClickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun openTelegramAdmin(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/real_mimkhas"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "آیدی ادمین در تلگرام: @real_mimkhas", Toast.LENGTH_LONG).show()
    }
}

private fun openBaleAdmin(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ble.ir/Real_MimKhas"))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "آیدی ادمین در پیام‌رسان بله: @Real_MimKhas", Toast.LENGTH_LONG).show()
    }
}

private fun sendEmail(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:mkhas1374m@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, "GameNet Manager Feedback")
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Send Email..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No email app found to send email", Toast.LENGTH_SHORT).show()
    }
}
