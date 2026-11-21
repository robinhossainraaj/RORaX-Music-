package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.navigation.RoraxApp
import com.example.player.PlaybackService
import com.example.ui.viewmodel.RoraxViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: RoraxViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val openPlayer = intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) == true
    setContent {
      RoraxApp(
        viewModel = viewModel,
        openPlayerInitially = openPlayer,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
  }
}
