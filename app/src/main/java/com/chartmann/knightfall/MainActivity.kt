package com.chartmann.knightfall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import com.chartmann.knightfall.ads.ConsentManager
import com.chartmann.knightfall.ui.components.BannerAdView
import com.chartmann.knightfall.ui.KnightfallNavHost
import com.chartmann.knightfall.ui.theme.KnightfallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // UMP consent first: the Mobile Ads SDK is initialized from
        // ConsentManager once consent gathering completes and allows ads.
        ConsentManager.gatherConsent(this)
        setContent {
            KnightfallTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            KnightfallNavHost()
                        }
                        BannerAdView(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}
