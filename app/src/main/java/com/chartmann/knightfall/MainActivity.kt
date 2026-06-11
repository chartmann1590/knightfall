package com.chartmann.knightfall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chartmann.knightfall.ui.KnightfallNavHost
import com.chartmann.knightfall.ui.theme.KnightfallTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KnightfallTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KnightfallNavHost()
                }
            }
        }
    }
}
