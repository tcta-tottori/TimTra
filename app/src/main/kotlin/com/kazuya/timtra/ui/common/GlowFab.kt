package com.kazuya.timtra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.theme.TimTraColors

/*
 * 画面の右下に置く丸ボタン。ホームの「+」、展開したときの各項目、
 * 他の画面の「戻る」を、すべて同じ大きさ・同じ見た目・同じ位置にそろえるための部品。
 */

/** ボタンの直径と、その外側に敷く光の幅。囲み全体は [FAB_OUTER_DP]。 */
internal const val FAB_SIZE_DP = 60f
internal const val FAB_HALO_DP = 12f
internal const val FAB_OUTER_DP = FAB_SIZE_DP + 2 * FAB_HALO_DP

/** 中のアイコン、白い細縁、影。 */
internal const val FAB_ICON_DP = 28f
internal const val FAB_RING_DP = 1f
internal const val FAB_ELEVATION_DP = 10f

/** 画面の隅からボタンの縁までの余白。光の分だけ内側に寄せて置く。 */
internal const val FAB_EDGE_DP = 20f

/** 右下に置くための余白。どの画面でも同じ場所に来るように、これだけを使う。 */
internal fun Modifier.fabInset(): Modifier =
    this
        .navigationBarsPadding()
        .padding(end = (FAB_EDGE_DP - FAB_HALO_DP).dp, bottom = (FAB_EDGE_DP - FAB_HALO_DP).dp)

/**
 * 丸ボタンの見た目だけ（押す仕掛けは持たない）。
 * 外側に淡い光を敷き、丸の中は青のグラデーションに白い細縁と影。
 *
 * [muted] を 1 に近づけるとグレーに変わる（「+」を開いているあいだに使う）。
 */
@Composable
fun GlowFabFace(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    muted: Float = 0f,
) {
    Box(modifier = modifier.size(FAB_OUTER_DP.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - muted }
                    .background(TimTraColors.fabHalo, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(FAB_SIZE_DP.dp)
                    .shadow(FAB_ELEVATION_DP.dp, CircleShape, spotColor = TimTraColors.gradientStart)
                    .background(TimTraColors.fabGradient, CircleShape)
                    .border(FAB_RING_DP.dp, TimTraColors.fabRing, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (muted > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = muted }
                            .background(TimTraColors.fabMutedGradient, CircleShape),
                )
            }
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(FAB_ICON_DP.dp).rotate(iconRotation),
            )
        }
    }
}

/** 押せる丸ボタン。波紋は出さず、見た目は [GlowFabFace] に任せる。 */
@Composable
fun GlowFab(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRotation: Float = 0f,
    muted: Float = 0f,
) {
    GlowFabFace(
        iconRes = iconRes,
        contentDescription = contentDescription,
        modifier =
            modifier
                .size(FAB_OUTER_DP.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        iconRotation = iconRotation,
        muted = muted,
    )
}

/**
 * 目立たせない丸ボタン。光も影も付けず、グレーの地に薄い縁だけ。
 * 位置と大きさは [GlowFab] と同じにして、他の画面の「戻る」と並びをそろえる。
 */
@Composable
fun QuietFab(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(FAB_OUTER_DP.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(FAB_SIZE_DP.dp)
                    .background(TimTraColors.quietFab, CircleShape)
                    .border(FAB_RING_DP.dp, TimTraColors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = TimTraColors.onSurfaceVariant,
                modifier = Modifier.size(FAB_ICON_DP.dp),
            )
        }
    }
}

/**
 * 画面の右下に置く「戻る」。ホームの「+」とまったく同じ位置・大きさになるよう、
 * Scaffold の floatingActionButton ではなく画面いっぱいの囲みに重ねて置く。
 */
@Composable
fun BackFabOverlay(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        GlowFab(
            iconRes = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomEnd).fabInset(),
        )
    }
}
