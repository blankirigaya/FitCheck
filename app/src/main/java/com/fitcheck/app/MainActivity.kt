package com.fitcheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.fitcheck.app.ui.FitCheckApp
import com.fitcheck.app.ui.theme.FitCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitCheckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FitCheckApp()
                }
            }
        }
    }
}
