package com.kazuya.timtra.wear.tile

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.ARC_ANCHOR_START
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.core.board.DepartureMode
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import com.kazuya.timtra.wear.ui.countdownProgress
import com.kazuya.timtra.wear.ui.countdownUnit
import com.kazuya.timtra.wear.ui.countdownValue
import com.kazuya.timtra.wear.ui.directionRes
import com.kazuya.timtra.wear.ui.hhmm
import com.kazuya.timtra.wear.ui.nameRes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * タイル: 時計アプリのホームとまったく同じ見た目。
 * 📍地点名 → 行き先 → リングに包まれたバス / 電車アイコンと「次の便まで NN 分」→ 発時刻カード。
 * タップでアプリのホームを開く。
 *
 * 地点の決め方もホームと同じ（固定した地点 → 現在地の最寄り → 時刻帯）。
 * タイルはバックグラウンドで描かれて位置が取れないことがあるので、
 * よく使う地点はアプリのメニューで固定しておくと安定する。
 */
@AndroidEntryPoint
class TimetableTileService : TileService() {
    @Inject
    lateinit var provider: WearBoardProvider

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.IO) {
            val snapshot = provider.snapshot(limit = LIMIT, locationTimeoutMillis = WearLocationProvider.TILE_TIMEOUT_MILLIS)
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
                .addIdToImageMapping(ID_PLACE, image(R.drawable.ic_place))
                .addIdToImageMapping(ID_BUS, image(R.drawable.ic_bus))
                .addIdToImageMapping(ID_TRAIN, image(R.drawable.ic_train))
                .build()
        }

    private fun image(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource
            .Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId
                    .Builder()
                    .setResourceId(resId)
                    .build(),
            ).build()

    private fun layout(snapshot: BoardSnapshot): LayoutElementBuilders.LayoutElement {
        val column =
            LayoutElementBuilders.Column
                .Builder()
                .setWidth(expand())
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setPadding(
                            ModifiersBuilders.Padding
                                .Builder()
                                .setAll(dp(PADDING_DP))
                                .build(),
                        ).setClickable(openBoardClickable())
                        .build(),
                )
        column
            .addContent(placeRow(getString(snapshot.place.nameRes())))
            .addContent(text(getString(snapshot.place.directionRes()), DIRECTION_SP, SUBTLE))
        val next = snapshot.next
        if (next == null) {
            column
                .addContent(spacer(SPACER_DP))
                .addContent(text(getString(R.string.board_empty), HEADSIGN_SP, WHITE, bold = true))
        } else {
            column
                .addContent(spacer(SPACER_DP))
                .addContent(countdownRow(snapshot, next))
                .addContent(spacer(SPACER_DP))
                .addContent(departureCard(next))
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

    /** 📍 + 地点名。 */
    private fun placeRow(name: String): LayoutElementBuilders.Row =
        LayoutElementBuilders.Row
            .Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(icon(ID_PLACE, PLACE_ICON_DP, ACCENT))
            .addContent(hSpacer(GAP_DP))
            .addContent(text(name, PLACE_SP, WHITE, bold = true))
            .build()

    /** リングに包まれたアイコンと、右に「次の便まで NN 分」。 */
    private fun countdownRow(
        snapshot: BoardSnapshot,
        next: Departure,
    ): LayoutElementBuilders.Row {
        val iconId = if (next.mode == DepartureMode.BUS) ID_BUS else ID_TRAIN
        val ring =
            LayoutElementBuilders.Box
                .Builder()
                .setWidth(dp(RING_DP))
                .setHeight(dp(RING_DP))
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
                .addContent(arc(FULL_TURN, TRACK))
        val sweep = FULL_TURN * countdownProgress(snapshot.now, next.at)
        if (sweep > 0f) ring.addContent(arc(sweep, ACCENT))
        ring.addContent(icon(iconId, RING_ICON_DP, WHITE))

        val value =
            LayoutElementBuilders.Row
                .Builder()
                .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                .addContent(text(countdownValue(snapshot.now, next.at), COUNTDOWN_SP, WHITE, bold = true))
                .addContent(text(countdownUnit(snapshot.now, next.at), UNIT_SP, ACCENT, bold = true))
                .build()
        val label =
            LayoutElementBuilders.Column
                .Builder()
                .addContent(text(getString(R.string.board_next_in), CAPTION_SP, SUBTLE))
                .addContent(value)
                .build()
        return LayoutElementBuilders.Row
            .Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(ring.build())
            .addContent(hSpacer(GAP_DP))
            .addContent(label)
            .build()
    }

    /** 下部の発時刻カード（行き先 + H:MM）。 */
    private fun departureCard(next: Departure): LayoutElementBuilders.Box {
        val headsign =
            LayoutElementBuilders.Row
                .Builder()
                .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                .addContent(text(next.headsign, HEADSIGN_SP, WHITE, bold = true))
                .addContent(hSpacer(GAP_DP))
                .addContent(text(getString(R.string.board_bound_for), CAPTION_SP, SUBTLE))
                .build()
        val inner =
            LayoutElementBuilders.Column
                .Builder()
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .addContent(headsign)
                .addContent(text(next.at.hhmm(), DEPART_SP, WHITE, bold = true))
                .build()
        return LayoutElementBuilders.Box
            .Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers
                    .Builder()
                    .setBackground(
                        ModifiersBuilders.Background
                            .Builder()
                            .setColor(argb(FOOTER))
                            .setCorner(
                                ModifiersBuilders.Corner
                                    .Builder()
                                    .setRadius(dp(FOOTER_RADIUS_DP))
                                    .build(),
                            ).build(),
                    ).setPadding(
                        ModifiersBuilders.Padding
                            .Builder()
                            .setStart(dp(CARD_PADDING_DP))
                            .setEnd(dp(CARD_PADDING_DP))
                            .setTop(dp(SPACER_DP))
                            .setBottom(dp(SPACER_DP))
                            .build(),
                    ).build(),
            ).addContent(inner)
            .build()
    }

    private fun arc(
        lengthDegrees: Float,
        color: Int,
    ): LayoutElementBuilders.Arc =
        LayoutElementBuilders.Arc
            .Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(ARC_ANCHOR_START)
            .addContent(
                LayoutElementBuilders.ArcLine
                    .Builder()
                    .setLength(degrees(lengthDegrees))
                    .setThickness(dp(RING_STROKE_DP))
                    .setColor(argb(color))
                    .build(),
            ).build()

    private fun icon(
        id: String,
        sizeDp: Float,
        color: Int,
    ): LayoutElementBuilders.Image =
        LayoutElementBuilders.Image
            .Builder()
            .setResourceId(id)
            .setWidth(dp(sizeDp))
            .setHeight(dp(sizeDp))
            .setColorFilter(
                LayoutElementBuilders.ColorFilter
                    .Builder()
                    .setTint(argb(color))
                    .build(),
            ).build()

    private fun openBoardClickable(): ModifiersBuilders.Clickable {
        val component = ComponentName(this, MainActivity::class.java)
        return ModifiersBuilders.Clickable
            .Builder()
            .setId(CLICK_OPEN_BOARD)
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
            .setMaxLines(1)
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

    private fun hSpacer(widthDp: Float): LayoutElementBuilders.Spacer =
        LayoutElementBuilders.Spacer
            .Builder()
            .setWidth(dp(widthDp))
            .build()

    private companion object {
        const val RESOURCES_VERSION = "2"
        const val FRESHNESS_MILLIS = 60_000L
        const val CLICK_OPEN_BOARD = "open_board"
        const val ID_PLACE = "place"
        const val ID_BUS = "bus"
        const val ID_TRAIN = "train"

        /** ホームと同じく次の 1 本だけ使う。終電後の判定のため少しだけ多めに引く。 */
        const val LIMIT = 2
        const val FULL_TURN = 360f

        // 寸法はアプリのホーム（WearBoardScreen）と同じ比率
        const val PADDING_DP = 8f
        const val SPACER_DP = 4f
        const val GAP_DP = 4f
        const val PLACE_ICON_DP = 16f
        const val RING_DP = 62f
        const val RING_STROKE_DP = 5f
        const val RING_ICON_DP = 28f
        const val FOOTER_RADIUS_DP = 18f
        const val CARD_PADDING_DP = 10f
        const val PLACE_SP = 17f
        const val DIRECTION_SP = 10f
        const val CAPTION_SP = 10f
        const val COUNTDOWN_SP = 34f
        const val UNIT_SP = 17f
        const val HEADSIGN_SP = 13f
        const val DEPART_SP = 21f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ACCENT = 0xFF2E8BF5.toInt()
        const val SUBTLE = 0xFFCBDAF2.toInt()
        const val TRACK = 0xFF39445A.toInt()
        const val FOOTER = 0xFF16305C.toInt()
    }
}
