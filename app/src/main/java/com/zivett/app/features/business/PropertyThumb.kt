package com.zivett.app.features.business

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zivett.app.core.models.Property
import com.zivett.app.core.models.initialsOf
import com.zivett.app.design.ZAvatarShape
import com.zivett.app.design.ZPhotoAvatar
import com.zivett.app.design.ZRadius

/// A property at a glance: its photo when one's been added, otherwise its
/// initials (the web's `PropertyThumb`). Rounded square — places, like
/// company logos, are never circles.
@Composable
fun PropertyThumb(property: Property, size: Dp = 48.dp) =
    PropertyThumb(property.name, property.photoUrl, size = size)

/// `preview` is a just-picked photo not uploaded yet (the edit sheet).
@Composable
fun PropertyThumb(name: String, photoUrl: String?, preview: ByteArray? = null, size: Dp = 48.dp) {
    if (preview != null) {
        AsyncImage(
            model = preview,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(ZRadius.tile)),
            contentScale = ContentScale.Crop,
        )
    } else {
        ZPhotoAvatar(photoUrl, initialsOf(name.ifBlank { "?" }), size, ZAvatarShape.COMPANY)
    }
}
