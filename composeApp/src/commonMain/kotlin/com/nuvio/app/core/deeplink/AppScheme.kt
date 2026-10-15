package com.nuvio.app.core.deeplink

/**
 * URL scheme owned by Nuvio Avix. It must differ from the `nuvio://` scheme used by
 * Nuvio and its forks so OAuth callbacks and deep links never open another installed app.
 */
const val APP_URL_SCHEME = "avixnuvio"
