package com.kazuya.timtra.wear.tile

import android.content.ComponentName
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.AngularLayoutConstraint
import androidx.wear.protolayout.DimensionBuilders.DegreesProp
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.ARC_ANCHOR_START
import androidx.wear.protolayout.LayoutElementBuilders.CONTENT_SCALE_MODE_FILL_BOUNDS
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_START
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders.StringLayoutConstraint
import androidx.wear.protolayout.TypeBuilders.StringProp
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInt32
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.board.Countdown
import com.kazuya.timtra.core.board.CountdownGauge
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.core.board.DepartureMode
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import com.kazuya.timtra.wear.ui.directionRes
import com.kazuya.timtra.wear.ui.hhmm
import com.kazuya.timtra.wear.ui.nameRes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * タイル: 時計アプリのホーム（`ui/WearBoardScreen`）とまったく同じ見た目。
 * 📍地点名 → 行き先 → 左に「分:秒」の残り時間・右にリングで囲んだバス / 電車アイコン →
 * 画面下いっぱいのグローに乗せた発時刻。タップでアプリのホームを開く。
 *
 * タイルは 1 分に 1 回しか描き直せないので、残り時間とリングは
 * ProtoLayout の動的な値（`DynamicInstant.platformTimeWithSecondsPrecision`）で持たせ、
 * 描き直しなしで 1 秒ごとに進むようにしてある。古い描画機では静的な値（描いた時点の残り）に落ちる。
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
                .addIdToImageMapping(ID_GLOW, image(R.drawable.bg_departure_glow))
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

    /** 下のグローを先に敷き、その上に中央の列を重ねる（アプリのホームと同じ重ね方）。 */
    private fun layout(snapshot: BoardSnapshot): LayoutElementBuilders.LayoutElement {
        val root =
            LayoutElementBuilders.Box
                .Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setClickable(openBoardClickable())
                        .build(),
                )
        val next = snapshot.next
        if (next != null) root.addContent(glowLayer(next))
        root.addContent(centerLayer(snapshot, next))
        return root.build()
    }

    /** 中央: 地点 → 行き先 → 残り時間とリング。グローに掛からないよう少し持ち上げる。 */
    private fun centerLayer(
        snapshot: BoardSnapshot,
        next: Departure?,
    ): LayoutElementBuilders.Box {
        val column =
            LayoutElementBuilders.Column
                .Builder()
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setPadding(
                            ModifiersBuilders.Padding
                                .Builder()
                                .setStart(dp(PADDING_DP))
                                .setEnd(dp(PADDING_DP))
                                .setBottom(dp(CENTER_LIFT_DP))
                                .build(),
                        ).build(),
                )
        column
            .addContent(placeRow(getString(snapshot.place.nameRes())))
            .addContent(text(getString(snapshot.place.directionRes()), DIRECTION_SP, SUBTLE))
            .addContent(spacer(SPACER_DP))
        if (next == null) {
            column.addContent(text(getString(R.string.board_empty), HEADSIGN_SP, WHITE, bold = true))
        } else {
            column.addContent(countdownRow(snapshot, next))
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

    /** 左に「次の便まで」と 分:秒、右にリングで囲んだアイコン（アプリのホームと同じ並び）。 */
    private fun countdownRow(
        snapshot: BoardSnapshot,
        next: Departure,
    ): LayoutElementBuilders.Row {
        val left = secondsLeft(next)
        val label =
            LayoutElementBuilders.Column
                .Builder()
                .setHorizontalAlignment(HORIZONTAL_ALIGN_START)
                .addContent(text(getString(R.string.board_next_in), CAPTION_SP, SUBTLE))
                .addContent(countdownText(snapshot, next, left))
                .build()
        return LayoutElementBuilders.Row
            .Builder()
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(label)
            .addContent(hSpacer(GAP_DP))
            .addContent(ring(snapshot, next, left))
            .build()
    }

    /**
     * 残り秒数。発車を過ぎたら 0、4 桁に収まらない先の便は 99:59 で頭打ち（core の [Countdown] と同じ）。
     * 1 秒ごとに進む値なので、タイルを描き直さなくても数字とリングが動く。
     */
    private fun secondsLeft(next: Departure): DynamicInt32 {
        val target = DynamicInstant.withSecondsPrecision(next.at.atZone(TimTraConstants.ZONE).toInstant())
        val raw = DynamicInstant.platformTimeWithSecondsPrecision().durationUntil(target).toIntSeconds()
        val capped =
            DynamicInt32
                .onCondition(raw.lt(MAX_SECONDS))
                .use(raw)
                .elseUse(DynamicInt32.constant(MAX_SECONDS))
        return DynamicInt32
            .onCondition(raw.lt(0))
            .use(DynamicInt32.constant(0))
            .elseUse(capped)
    }

    /** 「分:秒」の 4 桁。アプリのホームと同じ形（[Countdown.clock]）。 */
    private fun countdownText(
        snapshot: BoardSnapshot,
        next: Departure,
        left: DynamicInt32,
    ): LayoutElementBuilders.Text {
        val twoDigits =
            DynamicInt32.IntFormatter
                .Builder()
                .setMinIntegerDigits(2)
                .build()
        val value =
            left
                .div(SECONDS_PER_MINUTE)
                .format(twoDigits)
                .concat(DynamicString.constant(":"))
                .concat(left.rem(SECONDS_PER_MINUTE).format(twoDigits))
        return LayoutElementBuilders.Text
            .Builder()
            .setText(
                StringProp
                    .Builder(Countdown.clock(snapshot.now, next.at))
                    .setDynamicValue(value)
                    .build(),
            ).setLayoutConstraintsForDynamicText(StringLayoutConstraint.Builder(COUNTDOWN_PATTERN).build())
            .setMaxLines(1)
            .setFontStyle(fontStyle(COUNTDOWN_SP, WHITE, bold = true))
            .build()
    }

    /**
     * 乗り物アイコンを囲むリング。砂時計と同じで、普段は満タン、
     * 発車 15 分前から減りはじめて発車時刻で 0 になる（core の [CountdownGauge]）。
     */
    private fun ring(
        snapshot: BoardSnapshot,
        next: Departure,
        left: DynamicInt32,
    ): LayoutElementBuilders.Box {
        val iconId = if (next.mode == DepartureMode.BUS) ID_BUS else ID_TRAIN
        val within =
            DynamicInt32
                .onCondition(left.lt(WINDOW_SECONDS))
                .use(left)
                .elseUse(DynamicInt32.constant(WINDOW_SECONDS))
        val window = WINDOW_SECONDS.toFloat()
        val sweep =
            within
                .asFloat()
                .div(window)
                .times(FULL_TURN)
        val gauge = CountdownGauge.level(snapshot.now, next.at)
        return LayoutElementBuilders.Box
            .Builder()
            .setWidth(dp(RING_DP))
            .setHeight(dp(RING_DP))
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .addContent(arc(DegreesProp.Builder(FULL_TURN).build(), TRACK, dynamic = false))
            .addContent(
                arc(
                    DegreesProp.Builder(FULL_TURN * gauge).setDynamicValue(sweep).build(),
                    ACCENT,
                    dynamic = true,
                ),
            ).addContent(icon(iconId, RING_ICON_DP, WHITE))
            .build()
    }

    /** 画面下いっぱいのグローに乗せた発時刻（行き先 + H:MM）。角丸カードは丸い文字盤で隅が切れるので敷かない。 */
    private fun glowLayer(next: Departure): LayoutElementBuilders.Box {
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
                .setModifiers(
                    ModifiersBuilders.Modifiers
                        .Builder()
                        .setPadding(
                            ModifiersBuilders.Padding
                                .Builder()
                                .setBottom(dp(GLOW_BOTTOM_DP))
                                .build(),
                        ).build(),
                ).addContent(headsign)
                .addContent(text(next.at.hhmm(), DEPART_SP, WHITE, bold = true))
                .build()
        val band =
            LayoutElementBuilders.Box
                .Builder()
                .setWidth(expand())
                .setHeight(dp(GLOW_DP))
                .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
                .addContent(
                    LayoutElementBuilders.Image
                        .Builder()
                        .setResourceId(ID_GLOW)
                        .setWidth(expand())
                        .setHeight(dp(GLOW_DP))
                        .setContentScaleMode(CONTENT_SCALE_MODE_FILL_BOUNDS)
                        .build(),
                ).addContent(inner)
                .build()
        return LayoutElementBuilders.Box
            .Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(VERTICAL_ALIGN_BOTTOM)
            .addContent(band)
            .build()
    }

    private fun arc(
        length: DegreesProp,
        color: Int,
        dynamic: Boolean,
    ): LayoutElementBuilders.Arc {
        val line =
            LayoutElementBuilders.ArcLine
                .Builder()
                .setLength(length)
                .setThickness(dp(RING_STROKE_DP))
                .setColor(argb(color))
        if (dynamic) line.setLayoutConstraintsForDynamicLength(AngularLayoutConstraint.Builder(FULL_TURN).build())
        return LayoutElementBuilders.Arc
            .Builder()
            .setAnchorAngle(degrees(0f))
            .setAnchorType(ARC_ANCHOR_START)
            .addContent(line.build())
            .build()
    }

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
        const val RESOURCES_VERSION = "3"
        const val FRESHNESS_MILLIS = 60_000L
        const val CLICK_OPEN_BOARD = "open_board"
        const val ID_PLACE = "place"
        const val ID_BUS = "bus"
        const val ID_TRAIN = "train"
        const val ID_GLOW = "glow"

        /** ホームと同じく次の 1 本だけ使う。終電後の判定のため少しだけ多めに引く。 */
        const val LIMIT = 2
        const val FULL_TURN = 360f

        /** 残り時間の刻みと上限（99:59）。リングが減りはじめる 15 分も秒で持つ。 */
        const val SECONDS_PER_MINUTE = 60
        const val MAX_SECONDS = 99 * 60 + 59
        const val WINDOW_SECONDS = 15 * 60

        /** 動く文字の幅取りに使う見本。 */
        const val COUNTDOWN_PATTERN = "00:00"

        // 寸法はアプリのホーム（WearBoardScreen）と同じ比率
        const val PADDING_DP = 12f
        const val SPACER_DP = 6f
        const val GAP_DP = 4f
        const val PLACE_ICON_DP = 16f
        const val RING_DP = 62f
        const val RING_STROKE_DP = 5f
        const val RING_ICON_DP = 28f
        const val PLACE_SP = 17f
        const val DIRECTION_SP = 10f
        const val CAPTION_SP = 10f
        const val COUNTDOWN_SP = 26f
        const val HEADSIGN_SP = 13f
        const val DEPART_SP = 21f

        /** 下のグローの高さと、その中の文字の底からの余白。 */
        const val GLOW_DP = 78f
        const val GLOW_BOTTOM_DP = 14f

        /** 中央の列をグローの上へ持ち上げる量。 */
        const val CENTER_LIFT_DP = 24f

        const val WHITE = 0xFFFFFFFF.toInt()
        const val ACCENT = 0xFF78C5FA.toInt()
        const val SUBTLE = 0xFFCBDAF2.toInt()
        const val TRACK = 0xFF39445A.toInt()
    }
}
