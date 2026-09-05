package com.kazuya.timtra.ui.home

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.notify.PermissionStatus
import com.kazuya.timtra.ui.theme.StatusColors

/**
 * 初回起動時の導線（CLAUDE.md 8 注意点）: 通知許可・正確なアラーム・バッテリー最適化の除外。
 * すべて設定済みになるまでホームの上部に表示する。
 */
@Composable
fun PermissionsCard(
    status: PermissionStatus,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) context.startActivitySafely(PermissionStatus.notificationSettingsIntent(context))
            onChanged()
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.perm_note), style = MaterialTheme.typography.bodySmall)
            PermissionRow(stringResource(R.string.perm_notifications), status.notificationsAllowed) {
                if (PermissionStatus.needsRuntimeNotificationPermission) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivitySafely(PermissionStatus.notificationSettingsIntent(context))
                }
            }
            PermissionRow(stringResource(R.string.perm_exact_alarm), status.exactAlarmAllowed) {
                PermissionStatus.exactAlarmSettingsIntent(context)?.let { context.startActivitySafely(it) }
            }
            PermissionRow(stringResource(R.string.perm_battery), status.batteryOptimizationIgnored) {
                context.startActivitySafely(PermissionStatus.batteryOptimizationIntent(context))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_warning),
            contentDescription = null,
            tint = if (granted) StatusColors.ok else StatusColors.risk,
        )
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (granted) {
            Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.labelMedium)
        } else {
            TextButton(onClick = onRequest) { Text(stringResource(R.string.perm_action)) }
        }
    }
}

private fun android.content.Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
