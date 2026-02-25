package com.airsaid.logcat.demo.logcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airsaid.logcat.LogPriority
import com.airsaid.logcat.asLog
import com.airsaid.logcat.logcat

@Composable
fun LogcatDemoScreen(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Tap the buttons to emit logcat messages.",
      style = MaterialTheme.typography.bodyMedium
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Button(onClick = {
          logcat { "Default log message from logcat()" }
        }) {
          Text("Log default (DEBUG)")
        }

        Button(onClick = {
          logcat(LogPriority.INFO) { "Info log from logcat(LogPriority.INFO)" }
        }) {
          Text("Log INFO")
        }

        Button(onClick = {
          logcat(LogPriority.WARN) { "Warning log from logcat(LogPriority.WARN)" }
        }) {
          Text("Log WARN")
        }

        Button(onClick = {
          logcat(LogPriority.ERROR) { "Error log from logcat(LogPriority.ERROR)" }
        }) {
          Text("Log ERROR")
        }

        Button(onClick = {
          logcat(tag = "CustomTag") { "Log with custom tag" }
        }) {
          Text("Log with tag")
        }

        Button(onClick = {
          logcat { Throwable("Demo exception").asLog() }
        }) {
          Text("Log throwable")
        }
      }
    }
  }
}
