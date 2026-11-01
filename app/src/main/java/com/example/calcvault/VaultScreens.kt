package com.example.calcvault

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0B0F)
private val Bar = Color(0xFF16161C)
private val Accent = Color(0xFFFF9500)
private val Danger = Color(0xFFFF6B6B)

/* ----------------------------- First-run setup ----------------------------- */

@Composable
fun SetupScreen(
    storageReady: Boolean,
    canBio: Boolean,
    vaultAlreadyExists: Boolean,
    onGrantStorage: () -> Unit,
    onFinish: (code: String, wantBio: Boolean, onError: (String) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf(canBio) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            if (vaultAlreadyExists) "Open your vault" else "Set up your vault",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (vaultAlreadyExists)
                "This folder already has an encrypted vault. Enter the code you first set to open it."
            else
                "Choose a secret code. It encrypts your files, so pick something only you know and don't forget it — without it (and without fingerprint) the files can't be opened.",
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Secret code") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm code") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))
        Text("Storage", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            "The app automatically makes its own hidden, encrypted folder — no permission or folder picker needed. Note: uninstalling the app also removes this vault, so keep the app installed.",
            color = Color.Gray
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = bio, onCheckedChange = { bio = it }, enabled = canBio)
            Spacer(Modifier.width(10.dp))
            Text(
                if (canBio) "Also unlock with fingerprint / face" else "Biometric needs a strong fingerprint/face (Android 11+)",
                color = Color.White
            )
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Danger)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                when {
                    code.length < 4 -> error = "Use at least 4 characters/digits."
                    code != confirm -> error = "Codes don't match."
                    !storageReady -> error = "Storage isn't ready, try again."
                    else -> { error = null; onFinish(code, bio) { error = it } }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (vaultAlreadyExists) "Open vault" else "Create vault") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = Color.Gray)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Calculator vault v${BuildConfig.VERSION_NAME}",
            color = Color(0xFF555566),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* -------------------------------- Vault grid -------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(
    media: List<VaultItem>,
    repo: VaultRepository,
    storageReady: Boolean,
    onGrantStorage: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (VaultItem) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onBulkDelete: (List<VaultItem>) -> Unit,
    onBulkSave: (List<VaultItem>) -> Unit
) {
    val selected = remember { mutableStateListOf<Uri>() }
    val selectionMode = selected.isNotEmpty()
    fun selectedItems() = media.filter { selected.contains(it.uri) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    actions = {
                        IconButton(onClick = { onBulkSave(selectedItems()); selected.clear() }) {
                            Icon(Icons.Filled.Download, "Save to gallery")
                        }
                        IconButton(onClick = { onBulkDelete(selectedItems()); selected.clear() }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bar, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(if (media.isEmpty()) "Vault" else "Vault · ${media.size}") },
                    navigationIcon = { IconButton(onClick = onLock) { Icon(Icons.Filled.Lock, "Lock") } },
                    actions = {
                        IconButton(onClick = onTrash) { Icon(Icons.Filled.Delete, "Recycle bin") }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bar, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
            }
        },
        floatingActionButton = {
            if (storageReady && !selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    containerColor = Accent,
                    contentColor = Color.Black,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Add photo / video") }
                )
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when {
                !storageReady -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Storage access is off", color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onGrantStorage) { Text("Turn on storage access") }
                }

                media.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Lock, null, tint = Color(0xFF444455), modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Your vault is empty", color = Color.White)
                    Text("Tap below to add photos or videos.", color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAdd) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add photos / videos")
                    }
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(media, key = { it.uri.toString() }) { item ->
                        val isSel = selected.contains(item.uri)
                        Box(
                            Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickable(
                                    onClick = { if (selectionMode) toggle(selected, item.uri) else onOpen(item) },
                                    onLongClick = { toggle(selected, item.uri) }
                                )
                        ) {
                            Thumb(repo, item, Modifier.fillMaxSize())
                            SelectionOverlay(isSel)
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------- Recycle bin ------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrashScreen(
    media: List<VaultItem>,
    repo: VaultRepository,
    onBack: () -> Unit,
    onRestore: (List<VaultItem>) -> Unit,
    onDeleteForever: (List<VaultItem>) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val selected = remember { mutableStateListOf<Uri>() }
    val selectionMode = selected.isNotEmpty()
    fun selectedItems() = media.filter { selected.contains(it.uri) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    actions = {
                        IconButton(onClick = { onRestore(selectedItems()); selected.clear() }) {
                            Icon(Icons.Filled.Restore, "Restore")
                        }
                        IconButton(onClick = { onDeleteForever(selectedItems()); selected.clear() }) {
                            Icon(Icons.Filled.DeleteForever, "Delete forever")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bar, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Recycle bin") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                    actions = {
                        if (media.isNotEmpty()) TextButton(onClick = onEmptyTrash) { Text("Empty", color = Danger) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bar, titleContentColor = Color.White,
                        navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { pad ->
        Box(
            Modifier.fillMaxSize().padding(pad)
        ) {
            if (media.isEmpty()) {
                Text("Recycle bin is empty", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(media, key = { it.uri.toString() }) { item ->
                        val isSel = selected.contains(item.uri)
                        Box(
                            Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickable(
                                    onClick = { toggle(selected, item.uri) },
                                    onLongClick = { toggle(selected, item.uri) }
                                )
                        ) {
                            Thumb(repo, item, Modifier.fillMaxSize())
                            SelectionOverlay(isSel)
                        }
                    }
                }
            }
        }
    }
}

private fun toggle(list: MutableList<Uri>, uri: Uri) {
    if (list.contains(uri)) list.remove(uri) else list.add(uri)
}

@Composable
private fun SelectionOverlay(selected: Boolean) {
    if (selected) {
        Box(Modifier.fillMaxSize().background(Color(0x663DA9FF)))
        Icon(
            Icons.Filled.CheckCircle, "Selected", tint = Accent,
            modifier = Modifier.padding(4.dp)
        )
    }
}

/* --------------------------------- Settings -------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    folderPath: String,
    canBio: Boolean,
    bioEnabled: Boolean,
    onChangeCode: (String, (String?) -> Unit) -> Unit,
    onEnableBio: ((String) -> Unit) -> Unit,
    onDisableBio: () -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit
) {
    var bio by remember { mutableStateOf(bioEnabled) }
    var newCode by remember { mutableStateOf("") }
    var newConfirm by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bar, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Change secret code", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newCode, onValueChange = { newCode = it },
                label = { Text("New code") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newConfirm, onValueChange = { newConfirm = it },
                label = { Text("Confirm new code") }, visualTransformation = PasswordVisualTransformation(),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    when {
                        newCode.length < 4 -> msg = "Use at least 4 characters/digits."
                        newCode != newConfirm -> msg = "Codes don't match."
                        else -> onChangeCode(newCode) { err ->
                            msg = err ?: "Code updated."
                            if (err == null) { newCode = ""; newConfirm = "" }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Update code") }
            msg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = Color.Gray) }

            Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = bio, enabled = canBio,
                    onCheckedChange = { want ->
                        if (want) { onEnableBio { err -> msg = err; bio = false }; bio = true }
                        else { onDisableBio(); bio = false }
                    }
                )
                Spacer(Modifier.width(10.dp))
                Text(if (canBio) "Fingerprint / face unlock" else "Biometric not available", color = Color.White)
            }

            Spacer(Modifier.height(20.dp)); HorizontalDivider(); Spacer(Modifier.height(20.dp))
            Text("Storage folder", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Your encrypted files are kept in:", color = Color.Gray)
            Text(folderPath, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onEmptyTrash, modifier = Modifier.fillMaxWidth()) {
                Text("Empty recycle bin", color = Danger)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Your files are encrypted with your code. Keep it safe — if you lose both the code and fingerprint, the files can't be recovered.",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Calculator vault v${BuildConfig.VERSION_NAME}",
                color = Color(0xFF555566),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/* ------------------------------ Viewer (pager) ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    media: List<VaultItem>,
    startIndex: Int,
    repo: VaultRepository,
    onBack: () -> Unit,
    onSaveToGallery: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit
) {
    if (media.isEmpty()) {
        onBack()
        return
    }
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, media.size - 1)) { media.size }
    val current = media.getOrNull(pagerState.currentPage) ?: media.first()

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(current.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { onSaveToGallery(current) }) { Icon(Icons.Filled.Download, "Save to gallery") }
                    IconButton(onClick = { onDelete(current) }) { Icon(Icons.Filled.Delete, "Move to recycle bin") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xCC000000), titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(pad)
        ) { page ->
            val item = media[page]
            if (item.isVideo) VideoPage(repo, item) else ZoomableImagePage(repo, item)
        }
    }
}

@Composable
private fun ZoomableImagePage(repo: VaultRepository, item: VaultItem) {
    var bmp by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(item.uri) { mutableStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(item.uri) { bmp = withContext(Dispatchers.IO) { repo.decodeFull(item) } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.uri) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
private fun VideoPage(repo: VaultRepository, item: VaultItem) {
    var playing by remember(item.uri) { mutableStateOf(false) }
    var path by remember(item.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.uri, playing) {
        if (playing && path == null) {
            path = withContext(Dispatchers.IO) { repo.decryptToCache(item)?.absolutePath }
        }
    }
    DisposableEffect(item.uri) {
        onDispose { path?.let { runCatching { File(it).delete() } } }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val p = path
        if (playing && p != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    VideoView(c).apply {
                        setVideoPath(p)
                        val mc = MediaController(c)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setOnPreparedListener { mp -> mp.isLooping = true; start() }
                    }
                }
            )
        } else {
            var bmp by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(item.uri) { bmp = withContext(Dispatchers.IO) { repo.decodeFull(item) } }
            bmp?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            IconButton(onClick = { playing = true }, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }
    }
}

/* ------------------------------- Thumbnails -------------------------------- */

@Composable
fun Thumb(repo: VaultRepository, item: VaultItem, modifier: Modifier) {
    var bmp by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) { bmp = withContext(Dispatchers.IO) { repo.decodeThumb(item) } }
    Box(modifier.background(Color(0xFF1C1C22)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (item.isVideo) {
            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
        }
    }
}

/* ----------------------- In-app photo/video picker ----------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerScreen(
    repo: VaultRepository,
    onCancel: () -> Unit,
    onConfirm: (List<DeviceMedia>) -> Unit
) {
    var all by remember { mutableStateOf<List<DeviceMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<Uri>() }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) { repo.queryDeviceMedia() }
        loading = false
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text("Add to vault") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(
                        onClick = { onConfirm(all.filter { selected.contains(it.uri) }) },
                        enabled = selected.isNotEmpty()
                    ) { Text("Add (${selected.size})", color = Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bar, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                all.isEmpty() -> Text(
                    "No photos or videos found on this phone.",
                    color = Color.Gray, modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    items(all, key = { it.uri.toString() }) { m ->
                        val isSel = selected.contains(m.uri)
                        Box(
                            Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .combinedClickableSimple { toggle(selected, m.uri) }
                        ) {
                            DeviceThumb(repo, m.uri, Modifier.fillMaxSize())
                            if (m.isVideo) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                            SelectionOverlay(isSel)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
fun DeviceThumb(repo: VaultRepository, uri: Uri, modifier: Modifier) {
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) { bmp = withContext(Dispatchers.IO) { repo.deviceThumb(uri) } }
    Box(modifier.background(Color(0xFF1C1C22))) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}
