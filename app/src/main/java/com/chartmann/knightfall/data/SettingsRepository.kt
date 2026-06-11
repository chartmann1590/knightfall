package com.chartmann.knightfall.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.perf.FirebasePerformance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "knightfall_settings")

/**
 * Local app settings, including the user's privacy choices. Privacy
 * collection defaults to ON only after the user has completed onboarding
 * (the manifest disables all collection at cold start until applied here).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val ANALYTICS = booleanPreferencesKey("analytics_enabled")
        val CRASHLYTICS = booleanPreferencesKey("crashlytics_enabled")
        val PERFORMANCE = booleanPreferencesKey("performance_enabled")
        val MESSAGING = booleanPreferencesKey("messaging_enabled")
        val PUBLIC_PROFILE = booleanPreferencesKey("public_profile")
        val BOARD_THEME = stringPreferencesKey("board_theme")
        val SOUNDS = booleanPreferencesKey("sounds_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val COACH_ENABLED = booleanPreferencesKey("coach_enabled")
    }

    data class Snapshot(
        val onboarded: Boolean,
        val analytics: Boolean,
        val crashlytics: Boolean,
        val performance: Boolean,
        val messaging: Boolean,
        val publicProfile: Boolean,
        val boardTheme: String,
        val sounds: Boolean,
        val haptics: Boolean,
        val coachEnabled: Boolean,
    )

    val snapshots: Flow<Snapshot> = context.dataStore.data.map { p ->
        Snapshot(
            onboarded = p[Keys.ONBOARDED] ?: false,
            analytics = p[Keys.ANALYTICS] ?: true,
            crashlytics = p[Keys.CRASHLYTICS] ?: true,
            performance = p[Keys.PERFORMANCE] ?: true,
            messaging = p[Keys.MESSAGING] ?: true,
            publicProfile = p[Keys.PUBLIC_PROFILE] ?: false,
            boardTheme = p[Keys.BOARD_THEME] ?: "walnut",
            sounds = p[Keys.SOUNDS] ?: true,
            haptics = p[Keys.HAPTICS] ?: true,
            coachEnabled = p[Keys.COACH_ENABLED] ?: true,
        )
    }

    suspend fun current(): Snapshot = snapshots.first()

    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.ONBOARDED] = true }

    suspend fun setAnalytics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANALYTICS] = enabled }
        applyPrivacyChoices()
    }

    suspend fun setCrashlytics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CRASHLYTICS] = enabled }
        applyPrivacyChoices()
    }

    suspend fun setPerformance(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PERFORMANCE] = enabled }
        applyPrivacyChoices()
    }

    suspend fun setMessaging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MESSAGING] = enabled }
        applyPrivacyChoices()
    }

    suspend fun setPublicProfile(enabled: Boolean) =
        context.dataStore.edit { it[Keys.PUBLIC_PROFILE] = enabled }

    suspend fun setBoardTheme(theme: String) =
        context.dataStore.edit { it[Keys.BOARD_THEME] = theme }

    suspend fun setSounds(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SOUNDS] = enabled }

    suspend fun setHaptics(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }

    suspend fun setCoachEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.COACH_ENABLED] = enabled }

    /**
     * Pushes the persisted privacy choices into the Firebase SDKs.
     * Until onboarding completes, everything stays off (manifest default).
     */
    suspend fun applyPrivacyChoices() {
        val s = current()
        if (!s.onboarded) return
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(s.analytics)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = s.crashlytics
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = s.performance
        FirebaseMessaging.getInstance().isAutoInitEnabled = s.messaging
    }
}
