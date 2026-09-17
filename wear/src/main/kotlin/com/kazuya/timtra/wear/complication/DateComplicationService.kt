package com.kazuya.timtra.wear.complication

/**
 * 日付（曜日あり・日本語）。「9/ 金 15」。
 * クラス名は文字盤に設定済みのコンプリケーションを壊さないために変えていない。
 */
class DateComplicationService : DateImageComplicationService(DateStyle.WEEKDAY_JP)
