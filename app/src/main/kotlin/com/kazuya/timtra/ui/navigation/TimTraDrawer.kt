package com.kazuya.timtra.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazuya.timtra.R
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.ui.theme.TimTraColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val headerTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.M.d HH:mm")

/** 左からのメニュー。上にアプリのアイコンと版、中に画面一覧、下にワードマーク。 */
@Composable
fun TimTraDrawer(
    currentRoute: String?,
    items: List<DrawerItemSpec>,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val versionName =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
        }
    ModalDrawerSheet(
        drawerContainerColor = TimTraColors.backgroundTop,
        drawerContentColor = TimTraColors.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.width(300.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(vertical = 24.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notification),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.drawer_version, versionName),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = LocalDateTime.now(TimTraConstants.ZONE).format(headerTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TimTraColors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            items.forEach { item ->
                DrawerItem(
                    iconRes = item.iconRes,
                    label = stringResource(item.labelRes),
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                )
            }
            Spacer(Modifier.weight(1f))
            Wordmark(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

data class DrawerItemSpec(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
)

@Composable
private fun DrawerItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val decorated =
        if (selected) {
            Modifier
                .background(TimTraColors.pillFill, shape)
                .border(1.dp, TimTraColors.pillBorder, shape)
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(shape)
                .then(decorated)
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** アプリアイコンと同じ配色のワードマーク。 */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(bottom = 8.dp)) {
        Text("Tim", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = Color.White)
        Text("Tra", fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, color = TimTraColors.primary)
    }
}
