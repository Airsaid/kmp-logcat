package com.airsaid.logcat.internal

import android.app.Application
import android.content.pm.ApplicationInfo

/**
 * Returns `true` if the application is debuggable, otherwise `false`.
 */
internal val Application.isDebuggableApp: Boolean
  get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0