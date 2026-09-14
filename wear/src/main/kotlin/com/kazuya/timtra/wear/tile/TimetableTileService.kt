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
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import com.kazuya.timtra.wear.ui.countdownUnit
import com.kazuya.timtra.wear.ui.countdownValue
import com.kazuya.timtra.wear.ui.directionRes
import com.kazuya.timtra.wear.ui.hhmm
import com.kazuya.timtra.wear.ui.nameRes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * 時刻表タイル: いまいる場所（または手動で選んだ場所）の次の発車と、その後の便。
 *
 * 現在地が取れればその最寄りの停留所・駅を自動で選ぶ。取れなければ時計アプリで選んだ地点、
 * それも無ければ時刻帯から決めた出発地を出す。タップで時計アプリの発車標を開き、地点を選び直せる。
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
                .build()
        }

    private fun layout(snapshot: BoardSnapshot): LayoutElementBuilders.LayoutElement {
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
                        ).setClickable(openBoardClickable())
                        .build(),
                )
        column
            .addContent(text(getString(snapshot.place.nameRes()), SMALL_SP, WHITE, bold = true))
            .addContent(text(getString(snapshot.place.directionRes()), SMALL_SP, GREY))
        val next = snapshot.next
        if (next == null) {
            column.addContent(spacer(SPACER_DP)).addContent(text(getString(R.string.board_empty), SMALL_SP, WHITE))
        } else {
            column
                .addContent(text(getString(R.string.board_next_in), SMALL_SP, GREY))
                .addContent(text(countdownValue(snapshot.now, next.at) + countdownUnit(snapshot.now, next.at), LARGE_SP, ACCENT, true))
                .addContent(text(getString(R.string.board_depart_at, next.at.hhmm()), MEDIUM_SP, WHITE, bold = true))
                .addContent(text(getString(R.string.board_headsign_line, next.headsign, next.line), SMALL_SP, GREY))
                .addContent(spacer(SPACER_DP))
                .addContent(text(laterLine(snapshot.later), SMALL_SP, GREY))
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

    /** その後の便を 1 行にまとめる。入らない分は省く（タイルは 2 行まで）。 */
    private fun laterLine(later: List<Departure>): String =
        if (later.isEmpty()) {
            getString(R.string.board_no_more_today)
        } else {
            getString(R.string.board_later_label) + " " + later.joinToString("  ") { it.at.hhmm() }
        }

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
        const val CLICK_OPEN_BOARD = "open_board"

        /** 次の 1 本 + その後 3 本。タイルに収まる量。 */
        const val LIMIT = 4
        const val PADDING_DP = 12f
        const val SPACER_DP = 4f
        const val LARGE_SP = 34f
        const val MEDIUM_SP = 16f
        const val SMALL_SP = 12f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val ACCENT = 0xFF78C5FA.toInt()
        const val GREY = 0xFFB0BEC5.toInt()
    }
}
