package com.appause.android.util

import android.content.Intent
import android.net.Uri

internal fun supportEmailIntent(subject: String, body: String): Intent {
    val uri = Uri.parse(
        "mailto:rng2018520@gmail.com?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
    )
    return Intent(Intent.ACTION_SENDTO, uri)
}
