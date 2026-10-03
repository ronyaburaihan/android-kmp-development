package com.example.userprofile.platform

internal class JvmDeviceInfo : DeviceInfo {
    override val platform: String = "jvm"
    override val osVersion: String = System.getProperty("os.version") ?: "unknown"
}

public actual fun deviceInfo(): DeviceInfo = JvmDeviceInfo()
