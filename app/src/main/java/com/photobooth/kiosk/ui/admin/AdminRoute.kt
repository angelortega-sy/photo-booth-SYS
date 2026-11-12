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

    /** Personalización hub: opens the backgrounds and AI styles screens. */
    data object Styles : AdminRoute {
        override val key: String = "styles"
    }

    data object Backgrounds : AdminRoute {
        override val key: String = "backgrounds"
    }

    data object AiStyles : AdminRoute {
        override val key: String = "ai-styles"
    }

    data object Photos : AdminRoute {
        override val key: String = "photos"
    }

    data object Usage : AdminRoute {
        override val key: String = "usage"
    }

    /** Servidor: address, health, enroll and local unenroll of this booth. */
    data object Server : AdminRoute {
        override val key: String = "server"
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
            key == Backgrounds.key -> Backgrounds
            key == AiStyles.key -> AiStyles
            key == Photos.key -> Photos
            key == Usage.key -> Usage
            key == Server.key -> Server
            key == Network.key -> Network
            key == System.key -> System
            else -> Home
        }
    }
}
