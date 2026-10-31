package com.woody.planner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

enum class ThemeMode { System, Light, Dark }

/** Язык интерфейса и уведомлений. */
enum class AppLanguage(val title: String, val locale: Locale) {
    Russian("Русский", Locale.forLanguageTag("ru")),
    English("English", Locale.ENGLISH);

    fun pick(ru: String, en: String): String = if (this == English) en else ru
}

/** Анимация свайпа карточки задачи. */
enum class SwipeStyle(private val titleRu: String, private val titleEn: String, private val descriptionRu: String, private val descriptionEn: String) {
    Classic("Классика", "Classic", "Подложка проявляется под карточкой", "The action shows up under the card"),
    Liquid("Капля", "Drop", "Из края тянется капля воды и лопается", "A water drop stretches out and pops"),
    Book("Книжка", "Book", "Страница загибается за тот край, за который тянете", "The page curls from where you pull it");

    fun title(lang: AppLanguage) = lang.pick(titleRu, titleEn)
    fun description(lang: AppLanguage) = lang.pick(descriptionRu, descriptionEn)
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val swipeKey = stringPreferencesKey("swipe_style")
    private val menuHintKey = booleanPreferencesKey("menu_hint_seen")
    private val recentCategoriesKey = stringPreferencesKey("recent_categories")
    private val languageKey = stringPreferencesKey("language")
    private val alarmClockKey = booleanPreferencesKey("alarm_clock_reminders")
    private val missedNudgeKey = intPreferencesKey("missed_nudge_minutes")
    private val dynamicColorsKey = booleanPreferencesKey("dynamic_colors")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
    }

    val swipeStyle: Flow<SwipeStyle> = context.dataStore.data.map { prefs ->
        prefs[swipeKey]?.let { runCatching { SwipeStyle.valueOf(it) }.getOrNull() } ?: SwipeStyle.Classic
    }

    val language: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        prefs[languageKey]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.Russian
    }

    /**
     * Напоминания как системный будильник ([android.app.AlarmManager.setAlarmClock]): их не откладывает
     * режим сна и не отменяют «оптимизаторы» прошивок, но в строке состояния виден значок будильника.
     */
    val alarmClockReminders: Flow<Boolean> = context.dataStore.data.map { it[alarmClockKey] ?: true }

    /**
     * Через сколько минут после времени задачи напомнить, если она ещё не выполнена; 0 — выключено
     * (по умолчанию). Работает только для задач со временем.
     */
    val missedNudgeMinutes: Flow<Int> = context.dataStore.data.map { it[missedNudgeKey] ?: 0 }

    /** Цвета Material You (из обоев) вместо фирменных цветов Woody. По умолчанию — фирменные. */
    val dynamicColors: Flow<Boolean> = context.dataStore.data.map { it[dynamicColorsKey] ?: false }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorsKey] = enabled }
    }

    suspend fun setMissedNudgeMinutes(minutes: Int) {
        context.dataStore.edit { it[missedNudgeKey] = minutes }
    }

    /** Пользователь уже открывал меню кнопки «+» — подсказку больше не показываем. */
    val menuHintSeen: Flow<Boolean> = context.dataStore.data.map { it[menuHintKey] ?: false }

    /** id категорий в порядке последнего изменения (сначала самые свежие). */
    val recentCategories: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        prefs[recentCategoriesKey].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    }

    suspend fun touchCategory(id: Long) {
        context.dataStore.edit { prefs ->
            val ids = prefs[recentCategoriesKey].orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            prefs[recentCategoriesKey] = (listOf(id) + ids.filter { it != id }).take(20).joinToString(",")
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setSwipeStyle(style: SwipeStyle) {
        context.dataStore.edit { it[swipeKey] = style.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[languageKey] = language.name }
    }

    suspend fun setAlarmClockReminders(enabled: Boolean) {
        context.dataStore.edit { it[alarmClockKey] = enabled }
    }

    suspend fun setMenuHintSeen() {
        context.dataStore.edit { it[menuHintKey] = true }
    }
}
