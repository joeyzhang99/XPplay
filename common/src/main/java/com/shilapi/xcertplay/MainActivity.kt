package com.shilapi.xcertplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.ui.theme.XcertplayTheme

class MainActivity : ComponentActivity() {
    private var appNight by mutableStateOf(true)
    private var stopObservingAppearance: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appNight = resolveAppNightNow()
        enableEdgeToEdge()
        setContent {
            XcertplayTheme(darkTheme = appNight) {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(
                        modifier = Modifier.padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("XPplay")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appNight = resolveAppNightNow()
        stopObservingAppearance?.invoke()
        stopObservingAppearance = AppAppearanceRuntime.observeHost {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) appNight = resolveAppNightNow()
            }
        }
    }

    override fun onPause() {
        stopObservingAppearance?.invoke()
        stopObservingAppearance = null
        super.onPause()
    }
}
