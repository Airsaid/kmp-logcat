package com.airsaid.logcat.demo.logo

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

@Composable
actual fun AppLogo(modifier: Modifier) {
  val logoBitmap = remember { loadAppLogoImageBitmap() }
  if (logoBitmap != null) {
    Image(
      bitmap = logoBitmap,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = modifier,
    )
  }
}

private fun loadAppLogoImageBitmap(): ImageBitmap? {
  val image = loadAppLogoImage() ?: return null
  val data = UIImagePNGRepresentation(image) ?: return null
  val bytes = data.toByteArray()
  if (bytes.isEmpty()) return null
  return bytes.decodeToImageBitmap()
}

private fun loadAppLogoImage(): UIImage? {
  val info = NSBundle.mainBundle.infoDictionary ?: return null
  val icons = info["CFBundleIcons"] as? Map<*, *> ?: return null
  val primary = icons["CFBundlePrimaryIcon"] as? Map<*, *> ?: return null
  val files = primary["CFBundleIconFiles"] as? List<*> ?: return null
  val iconName = files.lastOrNull() as? String ?: return null
  return UIImage.imageNamed(iconName)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
  val length = this.length.toInt()
  if (length == 0) return ByteArray(0)
  val bytes = ByteArray(length)
  bytes.usePinned { pinned ->
    memcpy(pinned.addressOf(0), this.bytes, length.toULong())
  }
  return bytes
}
