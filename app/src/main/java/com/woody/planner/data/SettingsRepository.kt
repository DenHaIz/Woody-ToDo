package com.woody.planner.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

/** Анимация свайпа карточки задачи. */
enum class SwipeStyle(val title: String, val description: String) {
    Classic("Классика", "Подложка проявляется под карточкой"),
    Liquid("Капля", "Из края вытягивается капля воды и лопается"),
    Book("Книжка", "Карточка перелистывается, как страница"),
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val swipeKey = stringPreferencesKey("swipe_style")
    private val menuHintKey = booleanPreferencesKey("menu_hint_seen")
    private val recentCategoriesKey = stringPreferencesKey("recent_categories")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
    }

    val swipeStyle: Flow<SwipeStyle> = context.dataStore.data.map { prefs ->
        prefs[swipeKey]?.let { runCatching { SwipeStyle.valueOf(it) }.getOrNull() } ?: SwipeStyle.Classic
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

    suspend fun setMenuHintSeen() {
        context.dataStore.edit { it[menuHintKey] = true }
    }
}
