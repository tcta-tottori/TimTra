package com.kazuya.timtra.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.kazuya.timtra.ui.theme.TimTraColors

/**
 * 左からのメニュー。黒地のまま、色数を絞って静かに見せる。
 *
 * 選択中の項目は塗りつぶしのピルではなく、左の細いアクセント棒と水色の文字で示す。
 * 上にアプリのアイコンとワードマークと版、下に出典への入口（「このアプリについて」）が並ぶ。
 */
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
        drawerContainerColor = Color.Transparent,
        drawerContentColor = TimTraColors.onSurface,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        modifier = Modifier.width(292.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(TimTraColors.drawerGradient)
                    .padding(vertical = 28.dp),
        ) {
            Header(versionName)
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = TimTraColors.outline, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(14.dp))
            items.forEach { item ->
                DrawerItem(
                    iconRes = item.iconRes,
                    label = stringResource(item.labelRes),
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.drawer_footer),
                style = MaterialTheme.typography.labelSmall,
                color = TimTraColors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}

data class DrawerItemSpec(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
)

/** アイコン + ワードマーク + 版。 */
@Composable
private fun Header(versionName: String) {
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Wordmark()
            Text(
                text = stringResource(R.string.drawer_version, versionName),
                style = MaterialTheme.typography.labelMedium,
                color = TimTraColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawerItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) TimTraColors.accentLight else TimTraColors.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 選択中の目印。塗りつぶさず、左端の細い棒だけで示す
        Box(
            modifier =
                Modifier
                    .padding(vertical = 10.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) TimTraColors.accentLight else Color.Transparent),
        )
        Spacer(Modifier.width(21.dp))
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(21.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TimTraColors.onSurface else TimTraColors.onSurfaceVariant,
        )
    }
}

/** アプリアイコンと同じ配色のワードマーク。 */
@Composable
private fun Wordmark() {
    Row {
        Text("Tim", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
        Text("Tra", fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = TimTraColors.accentLight)
    }
}
