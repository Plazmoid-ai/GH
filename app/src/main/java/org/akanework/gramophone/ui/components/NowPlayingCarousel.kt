/*
 *     Copyright (C) 2026 Plazmoid-ai
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package org.akanework.gramophone.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.akanework.gramophone.R

@Composable
internal fun NowPlayingCarousel(
    previousArtworkUri: Uri?,
    currentArtworkUri: Uri?,
    nextArtworkUri: Uri?,
    modifier: Modifier = Modifier,
    onPreviousArtworkClick: () -> Unit,
    onCurrentArtworkClick: () -> Unit,
    onNextArtworkClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Scale down on narrow screens; keep 90dp / 140dp / 90dp on normal phones.
        val sideSize = minOf(90.dp, maxWidth * 0.24f)
        val centerSize = minOf(140.dp, maxWidth * 0.40f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkCard(previousArtworkUri, sideSize, onPreviousArtworkClick)
            ArtworkCard(currentArtworkUri, centerSize, onCurrentArtworkClick)
            ArtworkCard(nextArtworkUri, sideSize, onNextArtworkClick)
        }
    }
}

@Composable
private fun ArtworkCard(
    artworkUri: Uri?,
    size: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri == null) {
            Image(
                painter = painterResource(R.drawable.ic_default_cover),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(R.drawable.ic_default_cover),
                fallback = painterResource(R.drawable.ic_default_cover),
                placeholder = painterResource(R.drawable.ic_default_cover),
            )
        }
    }
}
