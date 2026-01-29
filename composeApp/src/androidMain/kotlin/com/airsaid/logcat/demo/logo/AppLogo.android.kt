package com.airsaid.logcat.demo.logo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun AppLogo(modifier: Modifier) {
  val context = LocalContext.current
  val logoBitmap = remember {
    val iconRes = context.applicationInfo.icon
    if (iconRes == 0) return@remember null
    val drawable = context.getDrawable(iconRes) ?: return@remember null
    drawable.toBitmap()?.asImageBitmap()
  }
  if (logoBitmap != null) {
    Image(
      bitmap = logoBitmap,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = modifier,
    )
  }
}

private fun Drawable.toBitmap(): Bitmap? {
  return when (this) {
    is BitmapDrawable -> this.bitmap
    else -> createBitmapFromDrawable(this)
  }
}

private fun createBitmapFromDrawable(drawable: Drawable): Bitmap? {
  val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
  val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  drawable.setBounds(0, 0, canvas.width, canvas.height)
  drawable.draw(canvas)
  return bitmap
}
