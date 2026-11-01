package com.example.calcvault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalcVaultApp(this) }
    }

    // We deliberately do NOT lock on onStop(): opening the photo picker or the
    // fingerprint prompt briefly backgrounds this activity, and locking there
    // would wipe the key mid-import. The vault locks via the Lock button and
    // when the process is killed. The key never touches disk.
}

enum class Screen { CALC, SETUP, VAULT, TRASH, SETTINGS, VIEWER, PICKER }

@Composable
fun CalcVaultApp(activity: FragmentActivity) {
    val ctx = activity
    val repo = remember { VaultRepository(ctx) }
    val scope = rememberCoroutineScope()

    val storageReady = true
    var configured by remember { mutableStateOf(VaultManager.vaultExists(repo)) }

    // First run (no vault yet) opens setup so the app clearly asks you to create
    // your code. After that it starts as a plain calculator.
    var screen by remember { mutableStateOf(if (configured) Screen.CALC else Screen.SETUP) }
    var items by remember { mutableStateOf(listOf<VaultItem>()) }
    var trashItems by remember { mutableStateOf(listOf<VaultItem>()) }
    var viewerIndex by remember { mutableStateOf(0) }

    fun refresh() {
        scope.launch {
            val v = withContext(Dispatchers.IO) { repo.listVault() }
            val t = withContext(Dispatchers.IO) { repo.listTrash() }
            items = v
            trashItems = t
        }
    }

    fun toCalc() {
        VaultSession.lock()
        screen = Screen.CALC
    }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) screen = Screen.PICKER
    }

    fun neededMediaPerms(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun hasMediaPerm(): Boolean {
        fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        // Full OR partial ("Select photos") access both let the picker work.
        return neededMediaPerms().any { granted(it) }
    }

    fun addPhotos() {
        if (hasMediaPerm()) screen = Screen.PICKER else mediaPermLauncher.launch(neededMediaPerms())
    }

    fun finishVault(code: String, wantBio: Boolean, onError: (String) -> Unit) {
        scope.launch {
            val exists = withContext(Dispatchers.IO) { VaultManager.vaultExists(repo) }
            val ok = withContext(Dispatchers.IO) {
                if (exists) VaultManager.attachWithCode(repo, code) else VaultManager.createVault(repo, code)
            }
            if (!ok) {
                onError(
                    if (exists) "A vault already exists and this code doesn't open it. Use the code you first set."
                    else "Couldn't create the vault. Make sure storage access is on."
                )
                return@launch
            }
            configured = true
            if (wantBio && BiometricAuth.canUse(activity)) {
                try {
                    val cipher = KeystoreBio.encryptCipher()
                    BiometricAuth.promptCipher(
                        activity, cipher, "Enable fingerprint unlock",
                        onSuccess = { c -> VaultManager.storeBioWrap(ctx, c); screen = Screen.VAULT; refresh() },
                        onError = { screen = Screen.VAULT; refresh() }
                    )
                    return@launch
                } catch (e: Exception) {
                    // fall through: code-only
                }
            }
            screen = Screen.VAULT
            refresh()
        }
    }

    fun codeAttempt(code: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { VaultManager.attachWithCode(repo, code) }
            if (ok) {
                screen = Screen.VAULT
                refresh()
            }
        }
    }

    fun biometricUnlock() {
        try {
            val iv = SecurityPrefs.bioIv(ctx) ?: return
            val cipher = KeystoreBio.decryptCipher(iv)
            BiometricAuth.promptCipher(
                activity, cipher, "Unlock your vault",
                onSuccess = { c -> if (VaultManager.bioUnlock(ctx, c)) { screen = Screen.VAULT; refresh() } },
                onError = {}
            )
        } catch (e: Exception) {
            VaultManager.disableBio(ctx)
        }
    }

    fun enableBio(onError: (String) -> Unit) {
        if (!BiometricAuth.canUse(activity)) {
            onError("No fingerprint/face is set up on this phone.")
            return
        }
        try {
            val cipher = KeystoreBio.encryptCipher()
            BiometricAuth.promptCipher(
                activity, cipher, "Enable fingerprint unlock",
                onSuccess = { c -> VaultManager.storeBioWrap(ctx, c) },
                onError = { onError("Fingerprint not enabled.") }
            )
        } catch (e: Exception) {
            onError("Couldn't set up fingerprint unlock.")
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        when (screen) {
            Screen.CALC -> CalculatorScreen(
                configured = configured,
                biometricEnabled = SecurityPrefs.bioEnabled(ctx),
                onCodeAttempt = { codeAttempt(it) },
                onSetup = { screen = Screen.SETUP },
                onBiometric = { biometricUnlock() }
            )

            Screen.SETUP -> SetupScreen(
                storageReady = storageReady,
                canBio = BiometricAuth.canUse(activity),
                vaultAlreadyExists = configured,
                onGrantStorage = {},
                onFinish = { code, wantBio, onError -> finishVault(code, wantBio, onError) },
                onCancel = { screen = Screen.CALC }
            )

            Screen.VAULT -> VaultScreen(
                media = items,
                repo = repo,
                storageReady = storageReady,
                onGrantStorage = {},
                onAdd = { addPhotos() },
                onOpen = { item ->
                    viewerIndex = items.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                    screen = Screen.VIEWER
                },
                onTrash = { screen = Screen.TRASH; refresh() },
                onSettings = { screen = Screen.SETTINGS },
                onLock = { toCalc() },
                onBulkDelete = { list ->
                    scope.launch { withContext(Dispatchers.IO) { list.forEach { repo.moveToTrash(it) } }; refresh() }
                },
                onBulkSave = { list ->
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { list.count { repo.exportToGallery(it) } }
                        Toast.makeText(ctx, "Saved $n to gallery", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Screen.TRASH -> TrashScreen(
                media = trashItems,
                repo = repo,
                onBack = { screen = Screen.VAULT },
                onRestore = { list ->
                    scope.launch { withContext(Dispatchers.IO) { list.forEach { repo.restore(it) } }; refresh() }
                },
                onDeleteForever = { list ->
                    scope.launch { withContext(Dispatchers.IO) { list.forEach { repo.deleteForever(it) } }; refresh() }
                },
                onEmptyTrash = {
                    scope.launch { withContext(Dispatchers.IO) { repo.emptyTrash() }; refresh() }
                }
            )

            Screen.SETTINGS -> SettingsScreen(
                folderPath = repo.folderPath,
                canBio = BiometricAuth.canUse(activity),
                bioEnabled = SecurityPrefs.bioEnabled(ctx),
                onChangeCode = { newCode, onResult ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { VaultManager.changeCode(repo, newCode) }
                        onResult(if (ok) null else "Couldn't update the code.")
                    }
                },
                onEnableBio = { onError -> enableBio(onError) },
                onDisableBio = { VaultManager.disableBio(ctx) },
                onEmptyTrash = {
                    scope.launch { withContext(Dispatchers.IO) { repo.emptyTrash() }; refresh() }
                },
                onBack = { screen = Screen.VAULT }
            )

            Screen.PICKER -> MediaPickerScreen(
                repo = repo,
                onCancel = { screen = Screen.VAULT },
                onConfirm = { sel ->
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.import(sel.map { it.uri }) }
                        screen = Screen.VAULT
                        refresh()
                    }
                }
            )

            Screen.VIEWER -> ViewerScreen(
                media = items,
                startIndex = viewerIndex,
                repo = repo,
                onBack = { screen = Screen.VAULT },
                onSaveToGallery = { item ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { repo.exportToGallery(item) }
                        Toast.makeText(
                            ctx,
                            if (ok) "Saved to gallery" else "Couldn't save to gallery",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDelete = { item ->
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.moveToTrash(item) }
                        screen = Screen.VAULT
                        refresh()
                    }
                }
            )
        }
    }
}
