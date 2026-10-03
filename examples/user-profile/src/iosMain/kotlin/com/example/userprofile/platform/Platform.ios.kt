package com.example.userprofile.platform

import platform.UIKit.UIDevice

internal class IosDeviceInfo : DeviceInfo {
    override val platform: String = "ios"
    override val osVersion: String = UIDevice.currentDevice.systemVersion
}

public actual fun deviceInfo(): DeviceInfo = IosDeviceInfo()
