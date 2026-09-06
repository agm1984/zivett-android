package com.zivett.app.core

/// Client feature flags, mirroring the web's `resources/js/lib/features.js`
/// — keep the two in sync when a flag flips.
object Features {
    /// Coupons are feature-flagged off platform-wide: the endpoints are
    /// live, but no client should surface them yet (docs/mobile-api.md).
    const val couponsEnabled = false
}
