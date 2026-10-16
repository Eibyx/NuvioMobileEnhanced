// Ported from AKRusso/NuvioMobile-Enhanced (GPL-3.0), commit dfee7fd and follow-ups.
package com.nuvio.app.features.mal

import com.nuvio.app.core.deeplink.APP_URL_SCHEME

object MalConfig {
    const val CLIENT_ID = MalBuildConfig.CLIENT_ID
    const val REDIRECT_URI = APP_URL_SCHEME + "://auth/mal"
    const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
    const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
    const val API_BASE_URL = "https://api.myanimelist.net/v2"
    const val WEBSITE_URL = "https://myanimelist.net"

    val isConfigured: Boolean
        get() = CLIENT_ID.isNotBlank()
}
