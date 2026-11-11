package com.photobooth.kiosk.ui.admin

/**
 * One destination of the admin area. The shell keeps a back stack of route
 * keys so the system back gesture walks back through visited screens.
 */
sealed interface AdminRoute {
    val key: String

    data object Home : AdminRoute {
        override val key: String = "home"
    }

    data object Brands : AdminRoute {
        override val key: String = "brands"
    }

    data class BrandEditor(val profileId: String) : AdminRoute {
        override val key: String = "editor:$profileId"
    }

    data object Camera : AdminRoute {
        override val key: String = "camera"
    }

    data object Styles : AdminRoute {
        override val key: String = "styles"
    }

    data object Photos : AdminRoute {
        override val key: String = "photos"
    }

    data object Usage : AdminRoute {
        override val key: String = "usage"
    }

    data object Network : AdminRoute {
        override val key: String = "network"
    }

    data object System : AdminRoute {
        override val key: String = "system"
    }

    companion object {
        fun fromKey(key: String): AdminRoute = when {
            key == Home.key -> Home
            key == Brands.key -> Brands
            key.startsWith("editor:") -> BrandEditor(key.removePrefix("editor:"))
            key == "event" -> Brands
            key == Camera.key -> Camera
            key == Styles.key -> Styles
            key == Photos.key -> Photos
            key == Usage.key -> Usage
            key == Network.key -> Network
            key == System.key -> System
            else -> Home
        }
    }
}
