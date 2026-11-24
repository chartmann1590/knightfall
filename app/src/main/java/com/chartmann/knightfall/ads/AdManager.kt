package com.chartmann.knightfall.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chartmann.knightfall.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var initialized = false

    /**
     * True only after UMP consent gathering has completed and the stored
     * consent state allows ads (see ConsentManager). Until then no ad is
     * loaded or shown. Backed by Compose state so BannerAdView appears as
     * soon as consent completes.
     */
    var adsAllowed by mutableStateOf(false)
        private set

    /**
     * Initializes the Mobile Ads SDK and preloads an interstitial. Called
     * exclusively by ConsentManager once consent allows requesting ads.
     */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        adsAllowed = true
        MobileAds.initialize(context) {}
        loadInterstitial(context)
    }

    fun loadInterstitial(context: Context) {
        if (!adsAllowed) return
        if (interstitialAd != null || isAdLoading) return

        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_ID
        if (adUnitId.isBlank()) {
            Log.w(TAG, "Interstitial Ad Unit ID is blank, skipping load.")
            return
        }

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial ad loaded successfully.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.e(TAG, "Failed to load interstitial ad: ${loadAdError.message}")
                }
            }
        )
    }

    fun showInterstitialBeforeGame(activity: Activity, onAdClosedOrFailed: () -> Unit) {
        if (!adsAllowed) {
            // Consent has not (yet) allowed ads: never block the game on it.
            onAdClosedOrFailed()
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed.")
                    interstitialAd = null
                    // Preload the next one
                    loadInterstitial(activity)
                    onAdClosedOrFailed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e(TAG, "Failed to show interstitial ad: ${adError.message}")
                    interstitialAd = null
                    // Preload the next one
                    loadInterstitial(activity)
                    onAdClosedOrFailed()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad not ready, calling callback directly.")
            // Preload if we don't have it
            loadInterstitial(activity)
            onAdClosedOrFailed()
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
