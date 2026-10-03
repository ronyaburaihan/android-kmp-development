package com.example.userprofile.di

import org.koin.core.module.Module

/**
 * In a real app this is where `androidContext()`-dependent bindings live (database builder
 * from `context.getDatabasePath`, DataStore path from `context.filesDir`, the OkHttp engine).
 * This example keeps the Android target to the common bindings so it compiles without an
 * Application; the pattern is in templates/platform-module.kt.
 */
public actual fun platformModule(): Module = commonPlatformBindings()
