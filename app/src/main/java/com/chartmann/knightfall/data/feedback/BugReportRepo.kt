package com.chartmann.knightfall.data.feedback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.feedbackDataStore by preferencesDataStore(name = "feedback_bug_reports")

class BugReportRepo(private val context: Context) {
    private object Keys {
        val BUG_REPORTS_LIST = stringPreferencesKey("bug_reports_list")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val bugReports: Flow<List<BugReport>> = context.feedbackDataStore.data
        .map { preferences ->
            val jsonString = preferences[Keys.BUG_REPORTS_LIST] ?: "[]"
            deserializeReports(jsonString)
        }
        .catch { emit(emptyList()) }

    suspend fun getBugReportsList(): List<BugReport> {
        return try {
            val preferences = context.feedbackDataStore.data.first()
            val jsonString = preferences[Keys.BUG_REPORTS_LIST] ?: "[]"
            deserializeReports(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveBugReport(report: BugReport) {
        context.feedbackDataStore.edit { preferences ->
            val currentList = getBugReportsList().toMutableList()
            currentList.removeAll { it.number == report.number }
            currentList.add(0, report)
            val jsonString = json.encodeToString(kotlinx.serialization.serializer(), currentList)
            preferences[Keys.BUG_REPORTS_LIST] = jsonString
        }
    }

    suspend fun updateBugReports(reports: List<BugReport>) {
        context.feedbackDataStore.edit { preferences ->
            val currentList = getBugReportsList().toMutableList()
            reports.forEach { report ->
                currentList.removeAll { it.number == report.number }
                currentList.add(0, report)
            }
            currentList.sortByDescending { it.number }
            val jsonString = json.encodeToString(kotlinx.serialization.serializer(), currentList)
            preferences[Keys.BUG_REPORTS_LIST] = jsonString
        }
    }

    private fun deserializeReports(jsonString: String): List<BugReport> {
        return try {
            json.decodeFromString<List<BugReport>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
