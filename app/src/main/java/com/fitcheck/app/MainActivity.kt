package com.fitcheck.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fitcheck.app.ui.FitCheckApp
import com.fitcheck.app.ui.theme.FitCheckTheme
import com.fitcheck.app.widget.FitCheckWidget
import com.fitcheck.app.ai.AiRuntimeProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 42)
        }
        runCatching { FitCheckWidget.refresh(this) }
        // Warm the local Gemma engine while the first screen is rendering so
        // the first recommendation does not block on model initialization.
        lifecycleScope.launch {
            runCatching { AiRuntimeProvider.get(applicationContext).initialize() }
        }
        setContent {
            FitCheckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FitCheckApp()
                }
            }
        }
    }
}
