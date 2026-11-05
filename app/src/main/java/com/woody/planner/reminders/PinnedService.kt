package com.woody.planner.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.woody.planner.MainActivity
import com.woody.planner.R
import com.woody.planner.WoodyApp
import com.woody.planner.data.AppLanguage
import com.woody.planner.domain.DayAgenda
import com.woody.planner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * «Постоянное уведомление» (включается в настройках): план дня в шторке. Служба переднего плана сама ничего
 * не делает — ни таймеров, ни фоновой работы, — но процесс с ней прошивки (vivo OriginOS, Xiaomi) не
 * «замораживают», и будильники напоминаний и полночное обновление виджета доходят вовремя.
 * Просыпается она только при разблокировке телефона: если наступил новый день — перерисовывает виджет,
 * если какое-то напоминание прошивка всё же придержала — показывает его. Расход заряда практически нулевой.
 */
class PinnedService : Service() {

    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = applicationContext
            scope.launch {
                runCatching {
                    WidgetUpdater.refreshIfStale(app)
                    (app as WoodyApp).container.reminders.catchUp()
                    // Новый день — новый план в уведомлении.
                    if (shownDay != LocalDate.now()) update(app)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        // Системные события доходят и до неэкспортированного приёмника.
        ContextCompat.registerReceiver(
            this,
            unlocked,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground — сразу, пока данные не прочитаны: у службы на это всего несколько секунд.
        val started = runCatching {
            val notification = build(this, lastLanguage, title = lastTitle, text = lastText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (started.isFailure) {
            // Система не разрешила запуск из фона — вернёмся при следующем открытии приложения.
            Log.w("Woody", "pinned notification", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        refresh(this)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(unlocked) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "pinned_day"
        private const val NOTIFICATION_ID = Int.MAX_VALUE - 9
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var running = false

        @Volatile
        private var shownDay: LocalDate? = null

        // Последний показанный текст — чтобы при перезапуске службы не мелькала заглушка.
        @Volatile
        private var lastTitle: String? = null

        @Volatile
        private var lastText: String? = null

        @Volatile
        private var lastLanguage: AppLanguage = AppLanguage.Russian

        /** Включить или выключить по настройке. */
        fun apply(context: Context, enabled: Boolean) {
            val intent = Intent(context, PinnedService::class.java)
            if (enabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { Log.w("Woody", "pinned notification", it) }
            } else {
                context.stopService(intent)
            }
        }

        /** Вернуть службу, если она включена, но процесс перезапускался. Из фона система может не разрешить — не страшно. */
        suspend fun ensure(context: Context) {
            if (running) return
            val container = (context.applicationContext as WoodyApp).container
            if (container.settings.pinnedNotification.first()) apply(context.applicationContext, enabled = true)
        }

        /** Задачи изменились — обновить текст уведомления (если оно включено). */
        fun refresh(context: Context) {
            if (!running) return
            val app = context.applicationContext
            scope.launch { runCatching { update(app) } }
        }

        private suspend fun update(context: Context) {
            val container = (context as WoodyApp).container
            val today = LocalDate.now()
            val agenda = container.repository.loadDay(today, today)
            val lang = container.settings.language.first()
            val (title, text) = summary(agenda, lang)
            lastTitle = title
            lastText = text
            lastLanguage = lang
            shownDay = today
            if (!running || !Notifications.canPost(context)) return
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context, lang, title, text))
            } catch (_: SecurityException) {
            }
        }

        private fun summary(agenda: DayAgenda, lang: AppLanguage): Pair<String, String> {
            val open = agenda.overdue + agenda.timed + agenda.untimed
            val total = open.size + agenda.done.size
            val title = if (total == 0) lang.pick("Сегодня", "Today") else lang.pick("Сегодня · ${agenda.done.size} из $total", "Today · ${agenda.done.size} of $total")
            val next = open.firstOrNull()
            val text = when {
                next != null -> buildString {
                    append(next.title)
                    next.time?.let { append(" · ").append(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                    if (open.size > 1) append(lang.pick(" · ещё ${open.size - 1}", " · ${open.size - 1} more"))
                }
                total == 0 -> lang.pick("Свободный день", "A free day")
                else -> lang.pick("Всё сделано!", "All done!")
            }
            return title to text
        }

        private fun build(context: Context, lang: AppLanguage, title: String?, text: String?): Notification {
            ensureChannel(context, lang)
            val open = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_woody)
                .setColor(0xFFE63946.toInt())
                .setContentTitle(title ?: lang.pick("Сегодня", "Today"))
                .setContentText(text ?: lang.pick("План на день", "Plan for the day"))
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        private fun ensureChannel(context: Context, lang: AppLanguage) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, lang.pick("План дня", "Today's plan"), NotificationManager.IMPORTANCE_MIN).apply {
                    description = lang.pick(
                        "Постоянное уведомление: не даёт телефону усыплять напоминания",
                        "Persistent notification that keeps reminders from being put to sleep",
                    )
                    setShowBadge(false)
                },
            )
        }
    }
}
