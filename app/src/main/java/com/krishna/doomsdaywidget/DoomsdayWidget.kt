package com.krishna.doomsdaywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class DoomsdayWidget : AppWidgetProvider() {

    companion object {
        // Target: 18 Dec 2026, 00:00 (phone local time). Change here if needed.
        private val TARGET: LocalDateTime = LocalDateTime.of(2026, 12, 18, 0, 0, 0)
        private const val ACTION_TICK = "com.krishna.doomsdaywidget.TICK"
        private const val HOUR_MS = 3_600_000L

        private fun pad(n: Long) = n.toString().padStart(2, '0')

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DoomsdayWidget::class.java))
            if (ids.isEmpty()) return

            val rv = RemoteViews(ctx.packageName, R.layout.widget_doomsday)
            val now = LocalDateTime.now()

            if (!now.isBefore(TARGET)) {
                rv.setTextViewText(R.id.title, "DOOMSDAY IS HERE")
                rv.setTextViewText(R.id.mo, "00")
                rv.setTextViewText(R.id.d, "00")
                rv.setTextViewText(R.id.h, "00")
                rv.setChronometerCountDown(R.id.chrono, true)
                rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime(), null, false)
                mgr.updateAppWidget(ids, rv)
                return
            }

            val months = ChronoUnit.MONTHS.between(now, TARGET)
            var cur = now.plusMonths(months)
            val days = ChronoUnit.DAYS.between(cur, TARGET)
            cur = cur.plusDays(days)
            val hours = ChronoUnit.HOURS.between(cur, TARGET)

            rv.setTextViewText(R.id.title, "DOOMSDAY IS COMING")
            rv.setTextViewText(R.id.mo, pad(months))
            rv.setTextViewText(R.id.d, pad(days))
            rv.setTextViewText(R.id.h, pad(hours))

            // Chronometer counts down minutes:seconds to the next top-of-hour
            // boundary (counted back from the target), so it ticks every second
            // without the app doing any work.
            val remainingMs = Duration.between(now, TARGET).toMillis()
            var toBoundary = remainingMs % HOUR_MS
            if (toBoundary == 0L) toBoundary = HOUR_MS - 1
            rv.setChronometerCountDown(R.id.chrono, true)
            rv.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + toBoundary, null, true)

            mgr.updateAppWidget(ids, rv)
            schedule(ctx, toBoundary + 500)
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
