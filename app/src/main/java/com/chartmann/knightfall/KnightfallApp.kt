package com.chartmann.knightfall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.chartmann.knightfall.chess.StockfishEngine
import com.chartmann.knightfall.coach.CoachModelManager
import com.chartmann.knightfall.coach.GemmaCoach
import com.chartmann.knightfall.data.AuthRepository
import com.chartmann.knightfall.data.GameRepository
import com.chartmann.knightfall.data.SettingsRepository
import com.chartmann.knightfall.data.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Simple manual DI container; everything in the app is reachable from here. */
class AppContainer(app: KnightfallApp) {
    val settings = SettingsRepository(app)
    val auth = AuthRepository()
    val users = UserRepository()
    val games = GameRepository()
    val stockfish = StockfishEngine(app)
    val modelManager = CoachModelManager(app)
    val coach = GemmaCoach(modelManager)
    val sounds = com.chartmann.knightfall.ui.SoundManager(app)
    val feedbackRepo = com.chartmann.knightfall.data.feedback.BugReportRepo(app)
    val githubApi = com.chartmann.knightfall.data.feedback.GithubApi.create()
}

class KnightfallApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    val settings: SettingsRepository get() = container.settings

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        appScope.launch { settings.applyPrivacyChoices() }
        com.chartmann.knightfall.ads.AdManager.initialize(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GENERAL,
                getString(R.string.notification_channel_general),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GAMES,
                getString(R.string.notification_channel_games),
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    companion object {
        const val CHANNEL_GENERAL = "knightfall_general"
        const val CHANNEL_GAMES = "knightfall_games"
    }
}
