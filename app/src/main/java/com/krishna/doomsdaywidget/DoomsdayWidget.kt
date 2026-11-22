package com.krishna.doomsdaywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class DoomsdayWidget : AppWidgetProvider() {

    companion object {
        // Target: 17 Dec 2026, 00:00 (phone local time). Change here if needed.
        private val TARGET: LocalDateTime = LocalDateTime.of(2026, 12, 17, 0, 0, 0)
        private const val ACTION_TICK = "com.krishna.doomsdaywidget.TICK"
        private const val HOUR_MS = 3_600_000L
        const val FRAMES = 12
        private val frameIds = intArrayOf(
            R.id.f0, R.id.f1, R.id.f2, R.id.f3, R.id.f4, R.id.f5,
            R.id.f6, R.id.f7, R.id.f8, R.id.f9, R.id.f10, R.id.f11
        )

        private fun pad(n: Long) = n.toString().padStart(2, '0')

        // Millis until the next top-of-hour boundary (counted back from TARGET).
        private fun toBoundary(now: LocalDateTime): Long {
            val remainingMs = Duration.between(now, TARGET).toMillis()
            val m = remainingMs % HOUR_MS
            return if (m == 0L) HOUR_MS - 1 else m
        }

        private fun build(ctx: Context, small: Boolean, now: LocalDateTime): RemoteViews {
            val rv = RemoteViews(
                ctx.packageName,
                if (small) R.layout.widget_doomsday_small else R.layout.widget_doomsday
            )
            rv.setChronometerCountDown(R.id.chrono, true)

            // Custom background photo chosen in the app (if any)
            val f = File(ctx.filesDir, if (small) "bg_small.jpg" else "bg_large.jpg")
            if (f.exists()) {
                BitmapFactory.decodeFile(f.path)?.let { rv.setImageViewBitmap(R.id.bg, it) }
            }

            // Custom 2-sec video loop (frames saved by the app), if any
            val tag = if (small) "s" else "l"
            val files = (0 until FRAMES).map { File(ctx.filesDir, "f_${tag}_$it.jpg") }
            if (files.all { it.exists() }) {
                rv.setViewVisibility(R.id.flipper, View.VISIBLE)
                files.forEachIndexed { i, file ->
                    BitmapFactory.decodeFile(file.path)?.let { rv.setImageViewBitmap(frameIds[i], it) }
                }
            }

            if (!now.isBefore(TARGET)) {
                rv.setTextViewText(R.id.title, "DOOMSDAY IS HERE")
                if (!small) rv.setTextViewText(R.id.mo, "00")
                rv.setTextViewText(R.id.d, "00")
                rv.setTextViewText(R.id.h, "00")
                rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime(), null, false)
                return rv
            }

            rv.setTextViewText(R.id.title, "DOOMSDAY IS COMING")
            if (small) {
                // 2x2: total days + hours
                val days = ChronoUnit.DAYS.between(now, TARGET)
                val hours = ChronoUnit.HOURS.between(now.plusDays(days), TARGET)
                rv.setTextViewText(R.id.d, pad(days))
                rv.setTextViewText(R.id.h, pad(hours))
            } else {
                val months = ChronoUnit.MONTHS.between(now, TARGET)
                var cur = now.plusMonths(months)
                val days = ChronoUnit.DAYS.between(cur, TARGET)
                cur = cur.plusDays(days)
                val hours = ChronoUnit.HOURS.between(cur, TARGET)
                rv.setTextViewText(R.id.mo, pad(months))
                rv.setTextViewText(R.id.d, pad(days))
                rv.setTextViewText(R.id.h, pad(hours))
            }
            // Chronometer ticks minutes:seconds by itself, no app work needed.
            rv.setChronometer(
                R.id.chrono, SystemClock.elapsedRealtime() + toBoundary(now), null, true
            )
            return rv
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DoomsdayWidget::class.java))
            if (ids.isEmpty()) return

            val now = LocalDateTime.now()
            val large = build(ctx, false, now)
            val rv = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val small = build(ctx, true, now)
                RemoteViews(mapOf(SizeF(100f, 100f) to small, SizeF(300f, 60f) to large))
            } else large

            mgr.updateAppWidget(ids, rv)
            if (now.isBefore(TARGET)) schedule(ctx, toBoundary(now) + 500)
        }

        private fun pending(ctx: Context): PendingIntent {
            val i = Intent(ctx, DoomsdayWidget::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun schedule(ctx: Context, inMs: Long) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val at = System.currentTimeMillis() + inMs
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
        }

        fun cancel(ctx: Context) {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onEnabled(context: Context) = refresh(context)

    override fun onDisabled(context: Context) = cancel(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refresh(context)
        }
    }
}
