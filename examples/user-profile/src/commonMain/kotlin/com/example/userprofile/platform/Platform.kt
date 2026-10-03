package com.example.userprofile.platform

/**
 * The preferred shape for a platform capability: an **interface** declared in common code,
 * implemented per platform, supplied by DI.
 *
 * Why not `expect class`: an expected class permits exactly one implementation per target,
 * cannot be faked in a test, and is Beta. The official KMP documentation recommends
 * interfaces plus dependency injection over expect/actual classes.
 * See references/kmp/project-structure.md § expect / actual.
 */
public interface DeviceInfo {
    public val platform: String
    public val osVersion: String
}

/**
 * The one place `expect` is appropriate: a **factory function**, not a class.
 *
 * `expect fun` is stable (classes are Beta) and gives the common module a way to obtain a
 * platform object without the DI container being involved — useful for the DI module
 * itself, which has to come from somewhere. Everything else goes through [DeviceInfo].
 *
 * File naming: this file is `Platform.kt`; the actuals are `Platform.jvm.kt` and
 * `Platform.ios.kt`. Source sets are flattened at compile time, so distinct names keep
 * navigation and stack traces unambiguous. See references/kotlin/coding-conventions.md.
 */
public expect fun deviceInfo(): DeviceInfo
