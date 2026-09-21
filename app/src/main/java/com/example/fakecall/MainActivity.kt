package com.example.fakecall

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // الواجهة عربية دائماً → اتجاه من اليمين لليسار
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val delayOptions = listOf(0 to "فوراً", 5 to "٥ ثوانٍ", 10 to "١٠ ثوانٍ", 30 to "٣٠ ثانية")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current

    var contacts by remember { mutableStateOf(Prefs.contacts(ctx)) }
    var activeId by remember { mutableStateOf(Prefs.activeContact(ctx).id) }
    var delaySec by remember { mutableIntStateOf(Prefs.delaySec(ctx)) }
    var ringtone by remember { mutableStateOf(Prefs.ringtoneUri(ctx)) }
    var vibrate by remember { mutableStateOf(Prefs.vibrate(ctx)) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    // أعد فحص الأذونات كلما رجع المستخدم من شاشة إعدادات النظام
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifOk = remember(refresh) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val fsiOk = remember(refresh) {
        Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings(ctx)
        refresh++
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            ringtone = uri?.toString()
            Prefs.setRingtoneUri(ctx, ringtone)
        }
    }

    val ringtoneTitle = remember(ringtone) {
        runCatching {
            val u = ringtone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            RingtoneManager.getRingtone(ctx, u).getTitle(ctx)
        }.getOrDefault("افتراضية")
    }

    Scaffold(topBar = { TopAppBar(title = { Text("مكالمة وهمية") }) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Button(
                onClick = {
                    if (delaySec > 0) {
                        Toast.makeText(ctx, "سيرنّ الاتصال بعد $delaySec ثانية", Toast.LENGTH_SHORT).show()
                    }
                    ctx.startActivity(Intent(ctx, TriggerActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Call, null)
                Spacer(Modifier.width(8.dp))
                Text("جرّب الآن")
            }

            // ---------------- جهات الاتصال ----------------
            Section("جهات الاتصال") {
                contacts.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            activeId = c.id
                            Prefs.setActiveId(ctx, c.id)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = c.id == activeId, onClick = {
                            activeId = c.id
                            Prefs.setActiveId(ctx, c.id)
                        })
                        ContactAvatar(c, 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { editing = c; showDialog = true }) {
                            Icon(Icons.Filled.Edit, "تعديل")
                        }
                        if (contacts.size > 1) {
                            IconButton(onClick = {
                                val rest = contacts.filter { it.id != c.id }
                                ImageStore.delete(ctx, c.photoFile)
                                Prefs.saveContacts(ctx, rest)
                                contacts = rest
                                if (activeId == c.id) {
                                    activeId = rest.first().id
                                    Prefs.setActiveId(ctx, activeId)
                                }
                            }) { Icon(Icons.Filled.Delete, "حذف") }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { editing = null; showDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("إضافة جهة اتصال")
                }
            }

            // ---------------- التأخير ----------------
            Section("التأخير قبل الرنين") {
                Text(
                    "بعد الضغط على الزر يمكنك وضع الجوال في جيبك وسيرنّ بعد المدة المختارة.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    delayOptions.forEach { (sec, label) ->
                        FilterChip(
                            selected = delaySec == sec,
                            onClick = { delaySec = sec; Prefs.setDelaySec(ctx, sec) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // ---------------- الصوت ----------------
            Section("النغمة والاهتزاز") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("النغمة")
                        Text(ringtoneTitle, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = {
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            ringtone?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        ringtoneLauncher.launch(i)
                    }) { Text("تغيير") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الاهتزاز", Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it; Prefs.setVibrate(ctx, it) })
                }
            }

            // ---------------- الأذونات ----------------
            Section("الأذونات") {
                PermissionRow("الإشعارات (لازمة للرنين المؤجَّل)", notifOk) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openNotificationSettings(ctx)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    PermissionRow("الإشعارات بملء الشاشة (لفتح المكالمة على شاشة القفل)", fsiOk) {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                }
            }

            // ---------------- زر الإعدادات السريعة ----------------
            Section("زر الإعدادات السريعة") {
                if (Build.VERSION.SDK_INT >= 33) {
                    Button(onClick = { requestAddTile(ctx) }, modifier = Modifier.fillMaxWidth()) {
                        Text("أضف الزر إلى الإعدادات السريعة")
                    }
                } else {
                    Text("اسحب لوحة الإعدادات السريعة، اضغط أيقونة التعديل، ثم أضف بلاطة «مكالمة».")
                }
            }

            Text(
                "مدة الرنين ${CallConfig.RING_MS / 1000} ثوانٍ. عند الرد تستمر المكالمة " +
                    "${CallConfig.ANSWERED_MS / 1000} ثوانٍ ثم تنتهي تلقائياً.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDialog) {
        ContactDialog(
            initial = editing,
            onDismiss = { showDialog = false },
            onSave = { c ->
                val updated =
                    if (contacts.any { it.id == c.id }) contacts.map { if (it.id == c.id) c else it }
                    else contacts + c
                Prefs.saveContacts(ctx, updated)
                contacts = updated
                showDialog = false
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        if (granted) Text("✓ مفعّل", color = MaterialTheme.colorScheme.primary)
        else FilledTonalButton(onClick = onGrant) { Text("منح") }
    }
}

@Composable
private fun ContactAvatar(contact: Contact, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(contact) {
        ImageStore.squareIcon(ImageStore.loadBitmap(ctx, contact), 160).asImageBitmap()
    }
    Image(bmp, null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
}

@Composable
private fun ContactDialog(initial: Contact?, onDismiss: () -> Unit, onSave: (Contact) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var pending by remember { mutableStateOf<String?>(null) }   // صورة جديدة لم تُحفظ بعد

    val shown = pending ?: initial?.photoFile
    val preview = remember(shown) {
        ImageStore.squareIcon(ImageStore.loadBitmap(ctx, Contact("", "", shown)), 320).asImageBitmap()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ImageStore.saveFromUri(ctx, uri)?.let { f ->
                ImageStore.delete(ctx, pending)
                pending = f
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    AlertDialog(
        onDismissRequest = { ImageStore.delete(ctx, pending); onDismiss() },
        title = { Text(if (initial == null) "جهة اتصال جديدة" else "تعديل جهة الاتصال") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    preview, null,
                    Modifier.size(120.dp).clip(CircleShape).clickable { pick() },
                    contentScale = ContentScale.Crop
                )
                TextButton(onClick = { pick() }) { Text("اختيار صورة") }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المتصل") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pending != null) ImageStore.delete(ctx, initial?.photoFile)
                onSave(
                    Contact(
                        id = initial?.id ?: Prefs.newId(),
                        name = name.trim().ifEmpty { "مجهول" },
                        photoFile = pending ?: initial?.photoFile
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = { ImageStore.delete(ctx, pending); onDismiss() }) { Text("إلغاء") }
        }
    )
}

private fun openNotificationSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    )
}

@RequiresApi(33)
private fun requestAddTile(ctx: Context) {
    val sbm = ctx.getSystemService(StatusBarManager::class.java)
    sbm.requestAddTileService(
        ComponentName(ctx, FakeCallTile::class.java),
        ctx.getString(R.string.tile_label),
        AndroidIcon.createWithResource(ctx, R.drawable.ic_tile_call),
        ctx.mainExecutor
    ) { _ -> }
}
