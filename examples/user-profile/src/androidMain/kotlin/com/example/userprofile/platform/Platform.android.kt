package com.example.userprofile.platform

import android.os.Build

internal class AndroidDeviceInfo : DeviceInfo {
    override val platform: String = "android"
    override val osVersion: String = Build.VERSION.RELEASE ?: "unknown"
}

public actual fun deviceInfo(): DeviceInfo = AndroidDeviceInfo()
