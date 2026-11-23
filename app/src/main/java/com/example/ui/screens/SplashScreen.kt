package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.RoraxLogoMark
import com.example.ui.viewmodel.SplashInitState
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  initState: SplashInitState,
  reducedMotion: Boolean,
  onRetry: () -> Unit,
  onReadyToNavigate: () -> Unit,
) {
  LaunchedEffect(initState) {
    if (initState is SplashInitState.Ready) {
      // Brief non-blocking transition for brand continuity without artificial wait
      delay(280L)
      onReadyToNavigate()
    }
  }

  val bg = MaterialTheme.colorScheme.background
  val primary = MaterialTheme.colorScheme.primary

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.14f), bg),
            radius = 900f,
          )
        )
        .systemBarsPadding()
        .padding(32.dp)
        .testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      RoraxLogoMark(size = 84.dp, animated = !reducedMotion)
      Spacer(Modifier.height(20.dp))
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Spacer(Modifier.height(32.dp))

      when (initState) {
        is SplashInitState.Initializing,
        is SplashInitState.Ready -> {
          CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = primary,
          )
        }
        is SplashInitState.Error -> {
          Text(
            text = stringResource(R.string.splash_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = initState.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(16.dp))
          Button(
            onClick = onRetry,
            modifier = Modifier.testTag("splash_retry_button"),
          ) {
            Text(stringResource(R.string.action_retry))
          }
        }
      }
    }
  }
}
