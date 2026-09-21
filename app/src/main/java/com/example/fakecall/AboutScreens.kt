package com.example.fakecall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** بيانات التواصل مع المطوّر. */
object DevInfo {
    const val EMAIL = "hh.programming.services@gmail.com"
    const val FB_PAGE = "https://www.facebook.com/share/18wHhTEVHB/"
    const val FB_HASSAN = "https://www.facebook.com/HBH690"
    const val FB_HAZEM = "https://www.facebook.com/profile.php?id=100013074136300"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

// =====================================================================================
//  حول التطبيق
// =====================================================================================

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenPrivacy: () -> Unit) {
    val ctx = LocalContext.current
    var showFeedback by remember { mutableStateOf(false) }

    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    // إن أضفت صورة باسم hi.png إلى res/drawable-nodpi فستظهر هنا تلقائياً
    val heroRes = remember { ctx.resources.getIdentifier("hi", "drawable", ctx.packageName) }

    SubScreen(stringResource(R.string.about_title), onBack) {

        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (heroRes != 0) {
                Image(painterResource(heroRes), null, Modifier.size(180.dp), contentScale = ContentScale.Fit)
            } else {
                Box(
                    Modifier.size(112.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Call, null,
                        Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.version_fmt, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.developed_by),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ملاحظة / اقتراح
        Card(onClick = { showFeedback = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.feedback_button),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // التواصل
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.contact_facebook), style = MaterialTheme.typography.titleMedium)
                }
                LinkButton(stringResource(R.string.fb_official), DevInfo.FB_PAGE)
                LinkButton(stringResource(R.string.fb_hassan), DevInfo.FB_HASSAN)
                LinkButton(stringResource(R.string.fb_hazem), DevInfo.FB_HAZEM)
            }
        }

        // سياسة الخصوصية
        Card(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PrivacyTip, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.privacy_policy_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showFeedback) {
        FeedbackDialog(onDismiss = { showFeedback = false })
    }
}

@Composable
private fun LinkButton(label: String, url: String) {
    val ctx = LocalContext.current
    FilledTonalButton(onClick = { openUrl(ctx, url) }, modifier = Modifier.fillMaxWidth()) {
        Text(label, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feedback_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.feedback_hint)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { if (sendFeedback(ctx, text.trim())) onDismiss() }
            ) { Text(stringResource(R.string.send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** يفتح تطبيق البريد برسالة جاهزة؛ لا يُرسل شيء إلا إذا ضغط المستخدم «إرسال» داخل تطبيق البريد. */
private fun sendFeedback(ctx: Context, body: String): Boolean = try {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(DevInfo.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, ctx.getString(R.string.feedback_subject))
        putExtra(Intent.EXTRA_TEXT, body)
    }
    ctx.startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Toast.makeText(ctx, ctx.getString(R.string.no_email_app), Toast.LENGTH_LONG).show()
    false
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.link_error), Toast.LENGTH_LONG).show()
    }
}

// =====================================================================================
//  سياسة الخصوصية
// =====================================================================================

private val privacySections = listOf(
    R.string.priv_1_title to R.string.priv_1_body,
    R.string.priv_2_title to R.string.priv_2_body,
    R.string.priv_3_title to R.string.priv_3_body,
    R.string.priv_4_title to R.string.priv_4_body,
    R.string.priv_5_title to R.string.priv_5_body,
    R.string.priv_6_title to R.string.priv_6_body,
    R.string.priv_7_title to R.string.priv_7_body,
    R.string.priv_8_title to R.string.priv_8_body,
    R.string.priv_9_title to R.string.priv_9_body,
    R.string.priv_10_title to R.string.priv_10_body,
    R.string.priv_11_title to R.string.priv_11_body
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SubScreen(stringResource(R.string.privacy_policy), onBack) {
        Text(
            stringResource(R.string.privacy_updated),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        privacySections.forEach { (title, body) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
