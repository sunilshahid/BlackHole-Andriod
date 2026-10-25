package com.blackhole.app.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackhole.app.model.FileItem
import com.blackhole.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaThumb(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedMediaPickerDialog(
    onDismiss: () -> Unit,
    onMediaSelected: (List<FileItem>) -> Unit,
    onOpenSystemGallery: () -> Unit
) {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf<List<MediaThumb>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Query MediaStore for device photos
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val items = mutableListOf<MediaThumb>()
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE
            )
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                    var count = 0
                    while (cursor.moveToNext() && count < 180) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                        var size = cursor.getLong(sizeCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        if (size <= 0L) {
                            try {
                                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                                    val s = pfd.statSize
                                    if (s > 0) size = s
                                }
                            } catch (_: Exception) {}
                        }
                        if (size <= 0L) {
                            try {
                                context.contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { afd ->
                                    val s = afd.length
                                    if (s > 0) size = s
                                }
                            } catch (_: Exception) {}
                        }
                        items.add(MediaThumb(id, contentUri, name, size))
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            mediaList = items
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BlackSpace
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Select Media",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${selectedIds.size} selected",
                                fontSize = 12.sp,
                                color = if (selectedIds.isNotEmpty()) AccentCyan else TextMuted
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        // Open system gallery option
                        IconButton(onClick = onOpenSystemGallery) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "System Gallery", tint = AccentViolet)
                        }

                        // Done button
                        Button(
                            onClick = {
                                val selectedItems = mediaList.filter { selectedIds.contains(it.id) }
                                    .map { thumb ->
                                        var sz = thumb.size
                                        if (sz <= 0L) {
                                            try {
                                                context.contentResolver.openFileDescriptor(thumb.uri, "r")?.use { pfd ->
                                                    val s = pfd.statSize
                                                    if (s > 0) sz = s
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        if (sz <= 0L) {
                                            try {
                                                context.contentResolver.openAssetFileDescriptor(thumb.uri, "r")?.use { afd ->
                                                    val s = afd.length
                                                    if (s > 0) sz = s
                                                }
                                            } catch (_: Exception) {}
                                        }
                                        FileItem(
                                            name = thumb.name,
                                            size = sz,
                                            uri = thumb.uri,
                                            mimeType = "image/*"
                                        )
                                    }
                                onMediaSelected(selectedItems)
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                disabledContainerColor = Color(0x3310B981)
                            ),
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = if (selectedIds.isNotEmpty()) "Add (${selectedIds.size})" else "Done",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedIds.isNotEmpty()) Color.Black else TextMuted
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBackground)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                } else if (mediaList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No media found on device", color = TextSecondary, fontSize = 14.sp)
                    }
                } else {
                    // Responsive 3-Column Image Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(mediaList, key = { it.id }) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            MediaGridTile(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    selectedIds = if (isSelected) {
                                        selectedIds - item.id
                                    } else {
                                        selectedIds + item.id
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaGridTile(
    item: MediaThumb,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    bitmap = context.contentResolver.loadThumbnail(item.uri, Size(250, 250), null)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            } catch (e: Exception) {
                // Fallback stream decode
                try {
                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                        bitmap = BitmapFactory.decodeStream(stream, null, options)
                    }
                } catch (err: Exception) {
                    err.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, AccentCyan)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x5506B6D4))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(AccentCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
