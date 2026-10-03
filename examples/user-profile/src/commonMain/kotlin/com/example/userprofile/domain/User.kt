package com.example.userprofile.domain

// Required for `@JvmInline` to resolve in commonMain. Without this import the native
// targets fail with "Unresolved reference 'JvmInline'"; without the annotation the JVM
// target fails with "Value classes without '@JvmInline' annotation are not yet supported".
// Both failures are target-specific, which is why every target must be compiled.
import kotlin.jvm.JvmInline

/**
 * Identity of a user, distinct from any other `String` in the system.
 *
 * A value class costs nothing at runtime on Kotlin/JVM and Kotlin/Native, and it makes
 * `repository.user(userId)` impossible to call with an email by mistake.
 *
 * `@JvmInline` plus `import kotlin.jvm.JvmInline` are both required — see the note on the
 * import above. This is a worked example of why `compileKotlinMetadata` is not sufficient
 * verification: each target rejects a different mistake.
 *
 * Boundary caveat: inline value classes collapse to the underlying primitive in generated
 * Objective-C headers, so Swift sees `String`, not `UserId`. The safety holds inside the
 * shared module and is lost at the Swift boundary.
 * See references/kmp/ios-interop.md § Unsupported or limited.
 */
@JvmInline
public value class UserId(public val value: String) {
    init {
        require(value.isNotBlank()) { "UserId must not be blank" }
    }
}

/**
 * Domain model. Carries no serialization, persistence, or framework annotations — those
 * belong to the transport and storage types in the data layer.
 * See references/architecture/clean-architecture.md § Entities and value types.
 */
public data class User(
    val id: UserId,
    val displayName: String,
    val email: String,
    val avatarUrl: String?,
)
