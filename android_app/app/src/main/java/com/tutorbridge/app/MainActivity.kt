package com.tutorbridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.tutorbridge.app.ui.navigation.TutorBridgeNavGraph
import com.tutorbridge.app.ui.theme.TutorBridgeTheme
import com.tutorbridge.app.viewmodel.TutorBridgeViewModel

/**
 * TutorBridge - Peer Tutoring Platform for South African Universities
 * Native Android Application with Jetpack Compose & Material 3 MVVM Architecture.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TutorBridgeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            TutorBridgeTheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TutorBridgeNavGraph(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}
