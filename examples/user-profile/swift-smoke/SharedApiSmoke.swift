// Compiled against the generated Objective-C header of UserProfile.framework.
// Proves the exported surface is callable from Swift: no Flow, no generics, @Throws honoured.
// Compile-only (no simulator run): see references/quality/ci-pipeline.md § Swift smoke step.
// Objective-C names carry the framework prefix (SharedApi) but the header adds
// swift_name("SharedApi"), so Swift uses the unprefixed Kotlin names. Verified against the header.
import Foundation
import UserProfile

enum SharedApiSmoke {
    static func exercise(_ api: SharedApi) async throws {
        // suspend fun with @Throws → `async throws`
        let snapshot: ProfileSnapshot? = try await api.currentProfile()
        _ = snapshot?.label

        // suspend fun refresh() → throws SharedApiException as a Swift Error
        do {
            try await api.refresh()
        } catch let error as NSError {
            _ = error.localizedDescription
        }

        // Flow replaced by a callback + handle
        let handle: Cancellable = api.observeProfile { snapshot in
            _ = snapshot?.email
        }
        handle.cancel()
        api.close()
    }

    static func construct() -> SharedApi {
        // Non-reified factory; Swift cannot call Koin's get<T>()
        SharedApiFactory.shared.create()
    }
}
