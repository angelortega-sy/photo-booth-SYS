package com.photobooth.kiosk.data

/**
 * Stable Drive folder base name for an event.
 *
 * Uses the admin override ("Nombre de la carpeta en Drive") and falls back to the brand name,
 * so an unconfigured booth still groups its photos per event. The backend appends the month
 * and creates `fotos-1`, `fotos-2`, ... inside it, so the app only sends the stable name.
 */
fun EventBranding.driveFolderBase(): String? {
    val base = driveFolderName.ifBlank { brand }.trim()
    if (base.isBlank()) return null
    return base.replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(80).ifBlank { null }
}
