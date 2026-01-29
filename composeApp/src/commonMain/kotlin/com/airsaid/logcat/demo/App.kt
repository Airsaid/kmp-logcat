@file:OptIn(ExperimentalMaterial3Api::class)

package com.airsaid.logcat.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airsaid.logcat.demo.logcat.LogcatDemoScreen

@Composable
fun App() {
  MaterialTheme {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("kmp-logcat") },
          colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
          ),
        )
      }
    ) { innerPadding ->
      LogcatDemoScreen(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      )
    }
  }
}
