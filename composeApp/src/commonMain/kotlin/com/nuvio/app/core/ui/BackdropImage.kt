package com.nuvio.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/** The screen owns the painter; a recycled LazyColumn item only draws it. */
@Composable
internal fun BackdropImage(
    model: Any?,
    retainedPainter: Painter?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Crop,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
) {
    if (retainedPainter != null) {
        Image(retainedPainter, contentDescription, modifier.artworkDrawMarker(model.toString(),
            retainedPainter !is AsyncImagePainter || retainedPainter.state.value is AsyncImagePainter.State.Success), alignment, contentScale)
    } else {
        AsyncImage(model, contentDescription, modifier, alignment = alignment,
            contentScale = contentScale, onSuccess = onSuccess)
    }
}
