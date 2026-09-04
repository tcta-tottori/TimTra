package com.kazuya.timtra.wear.tile

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.WearJourneyProvider
import com.kazuya.timtra.wear.WearSnapshot
import com.kazuya.timtra.wear.ui.StatusColors
import com.kazuya.timtra.wear.ui.countdownLabel
import com.kazuya.timtra.wear.ui.hhmm
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import javax.inject.Inject

/**
 * タイル: 家を出る時刻 + 残り時間 + ステータス色（CLAUDE.md 7-4）。タップでアプリ（バス/JR の詳細）を開く。
 * 残り時間は ProtoLayout の動的式でレンダラー側が毎分更新する。対応しないレンダラーには静的文字列を出す。
 */
@AndroidEntryPoint
class CommuteTileService : TileService() {
    @Inject
    lateinit var provider: WearJourneyProvider

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            val snapshot = provider.snapshot()
            TileBuilders.Tile
                .Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(snapshot)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            ResourceBuilders.Resources
                .Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        }

    private fun layout(snapshot: WearSnapshot): LayoutElementBuilders.LayoutElement {
        val column =
            LayoutElementBuilders.Column
                .Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setPadding(
                            ModifiersBuilders.Padding
                                .Builder()
                                .setAll(dp(PADDING_DP))
                                .build(),
                        ).setClickable(openAppClickable())
                        .build(),
                )
        val journey = snapshot.journey
        if (journey == null) {
            column.addContent(text(getString(if (snapshot.dayOff) R.string.day_off else R.string.no_journey), SMALL_SP, WHITE))
        } else {
            val leaveLabel = getString(if (journey.bound == Bound.OUTBOUND) R.string.leave_home else R.string.leave_work)
            column
                .addContent(text(leaveLabel, SMALL_SP, GREY))
                .addContent(text(journey.leaveAt.hhmm(), LARGE_SP, WHITE, bold = true))
                .addContent(countdownText(snapshot, journey))
                .addContent(spacer(SPACER_DP))
                .addContent(statusChip(journey))
                .addContent(spacer(SPACER_DP))
                .addContent(text(legsLine(journey), SMALL_SP, GREY))
        }
        return LayoutElementBuilders.Box
            .Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(column.build())
            .build()
    }

    /** 残り時間。出発前は動的式（毎分更新）、出発後は静的文字列。 */
    private fun countdownText(
        snapshot: WearSnapshot,
        journey: Journey,
    ): LayoutElementBuilders.LayoutElement {
        val static = countdownLabel(snapshot.now, journey.leaveAt)
        val remaining = Duration.between(snapshot.now, journey.leaveAt)
        if (remaining.isNegative || remaining.isZero) return text(static, MEDIUM_SP, WHITE)

        val target = journey.leaveAt.atZone(TimTraConstants.ZONE).toInstant()
        val minutes =
            DynamicBuilders.DynamicInstant
                .platformTimeWithSecondsPrecision()
                .durationUntil(DynamicBuilders.DynamicInstant.withSecondsPrecision(target))
                .toIntMinutes()
        val dynamic =
            DynamicBuilders.DynamicString
                .constant(getString(R.string.countdown_dynamic_prefix))
                .concat(minutes.format())
                .concat(DynamicBuilders.DynamicString.constant(getString(R.string.countdown_dynamic_suffix)))
        return LayoutElementBuilders.Text
            .Builder()
            .setText(
                TypeBuilders.StringProp
                    .Builder(static)
                    .setDynamicValue(dynamic)
                    .build(),
            ).setLayoutConstraintsForDynamicText(TypeBuilders.StringLayoutConstraint.Builder(DYNAMIC_TEXT_PATTERN).build())
            .setFontStyle(fontStyle(MEDIUM_SP, WHITE, bold = false))
            .build()
    }

    private fun statusChip(journey: Journey): LayoutElementBuilders.LayoutElement {
        val label =
            getString(
                when (journey.status) {
                    JourneyStatus.OK -> R.string.status_ok
                    JourneyStatus.TIGHT -> R.string.status_tight
                    JourneyStatus.RISK -> R.string.status_risk
                    JourneyStatus.MISSED -> R.string.status_missed
                },
            )
        val margin = journey.transferMargin.toMinutes()
        val marginText =
            if (margin >= 0) getString(R.string.transfer_margin, margin) else getString(R.string.transfer_margin_negative, -margin)
        return LayoutElementBuilders.Box
            .Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers
                    .Builder()
                    .setBackground(
                        ModifiersBuilders.Background
                            .Builder()
                            .setColor(argb(StatusColors.argb(journey.status)))
                            .setCorner(
                                ModifiersBuilders.Corner
                                    .Builder()
                                    .setRadius(dp(CHIP_RADIUS_DP))
                                    .build(),
                            ).build(),
                    ).setPadding(
                        ModifiersBuilders.Padding
                            .Builder()
                            .setStart(dp(CHIP_PADDING_DP))
                            .setEnd(dp(CHIP_PADDING_DP))
                            .setTop(dp(CHIP_PADDING_V_DP))
                            .setBottom(dp(CHIP_PADDING_V_DP))
                            .build(),
                    ).build(),
            ).addContent(text("$label・$marginText", SMALL_SP, WHITE, bold = true))
            .build()
    }

    private fun legsLine(journey: Journey): String =
        when (journey.bound) {
            Bound.OUTBOUND ->
                getString(R.string.bus_line, journey.bus.departureAt.hhmm(), journey.bus.arrivalAt.hhmm()) + "  " +
                    getString(R.string.jr_line, journey.train.departureAt.hhmm(), journey.train.arrivalAt.hhmm())
            Bound.INBOUND ->
                getString(R.string.jr_line, journey.train.departureAt.hhmm(), journey.train.arrivalAt.hhmm()) + "  " +
                    getString(R.string.bus_line, journey.bus.departureAt.hhmm(), journey.bus.arrivalAt.hhmm())
        }

    private fun openAppClickable(): ModifiersBuilders.Clickable {
        val component = ComponentName(this, MainActivity::class.java)
        return ModifiersBuilders.Clickable
            .Builder()
            .setId(CLICK_OPEN_APP)
            .setOnClick(
                ActionBuilders.LaunchAction
                    .Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity
                            .Builder()
                            .setPackageName(component.packageName)
                            .setClassName(component.className)
                            .build(),
                    ).build(),
            ).build()
    }

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text
            .Builder()
            .setText(value)
            .setMaxLines(2)
            .setFontStyle(fontStyle(sizeSp, color, bold))
            .build()

    private fun fontStyle(
        sizeSp: Float,
        color: Int,
        bold: Boolean,
    ): LayoutElementBuilders.FontStyle {
        val builder =
            LayoutElementBuilders.FontStyle
                .Builder()
                .setSize(sp(sizeSp))
                .setColor(argb(color))
        if (bold) builder.setWeight(FONT_WEIGHT_BOLD)
        return builder.build()
    }

    private fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer
            .Builder()
            .setHeight(dp(heightDp))
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val FRESHNESS_MILLIS = 60_000L
        const val CLICK_OPEN_APP = "open_app"
        const val DYNAMIC_TEXT_PATTERN = "あと 000 分"
        const val PADDING_DP = 12f
        const val SPACER_DP = 6f
        const val CHIP_RADIUS_DP = 12f
        const val CHIP_PADDING_DP = 10f
        const val CHIP_PADDING_V_DP = 4f
        const val LARGE_SP = 40f
        const val MEDIUM_SP = 18f
        const val SMALL_SP = 12f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GREY = 0xFFB0BEC5.toInt()
    }
}
