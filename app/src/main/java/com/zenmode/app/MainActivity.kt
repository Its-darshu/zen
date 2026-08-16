package com.zenmode.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.navigation.ZenNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZenModeTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ZenBlack),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ZenNavHost()
                }
            }
        }
    }
}
