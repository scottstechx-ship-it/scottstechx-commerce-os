package com.scottstechx.commerceos.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic root / tamper detection. The checks are deliberately
 * conservative (positive signal only) so a false positive never locks
 * the user out. If any indicator trips, the app surfaces a warning
 * banner on the Login screen and the Buyer/Driver top bar; it does
 * not block usage, because the trust model expects the server to
 * enforce authoritative decisions.
 */
@Singleton
class TamperDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Report(
        val isRooted: Boolean,
        val isDebuggable: Boolean,
        val isEmulator: Boolean,
        val isInstallerSuspicious: Boolean,
        val tags: List<String>
    )

    fun inspect(): Report {
        val tags = mutableListOf<String>()

        val rooted = checkSuExists() || checkSuBinaries() || checkRwSystem()
        if (rooted) tags += "root"

        val debuggable = (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) tags += "debuggable"

        val emulator = checkEmulator()
        if (emulator) tags += "emulator"

        val installerSuspicious = checkInstaller()
        if (installerSuspicious) tags += "installer-suspicious"

        return Report(
            isRooted = rooted,
            isDebuggable = debuggable,
            isEmulator = emulator,
            isInstallerSuspicious = installerSuspicious,
            tags = tags
        )
    }

    private fun checkSuExists(): Boolean =
        runCatching { File("/system/xbin/su").exists() }.getOrDefault(false) ||
        runCatching { File("/system/bin/su").exists() }.getOrDefault(false) ||
        runCatching { File("/sbin/su").exists() }.getOrDefault(false) ||
        runCatching { File("/data/local/su").exists() }.getOrDefault(false) ||
        runCatching { File("/data/local/bin/su").exists() }.getOrDefault(false) ||
        runCatching { File("/data/local/xbin/su").exists() }.getOrDefault(false)

    private fun checkSuBinaries(): Boolean {
        val paths = listOf(
            "/system/xbin/su", "/system/bin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/app/Superuser.apk",
            "/system/app/SuperSU.apk", "/data/adb/magisk",
            "/cache/.disable_magisk", "/dev/.magisk.unblock"
        )
        return paths.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

    private fun checkRwSystem(): Boolean = runCatching {
        File("/system").canWrite()
    }.getOrDefault(false)

    private fun checkEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("vbox") ||
            model.contains("google sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for x86") ||
            product.contains("sdk") ||
            product.contains("vbox") ||
            product.contains("emulator") ||
            brand.contains("generic") ||
            brand.contains("android")
    }

    private fun checkInstaller(): Boolean {
        // Anything that isn't the Play Store, an OEM store, or our
        // own package is "suspicious" for a production app. For the
        // dev build, we exempt sideloads (the emulator installs APKs
        // via adb, not via a package installer).
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val source = context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
            source != null && source != context.packageName &&
                !source.contains("com.android.vending") &&
                !source.contains("com.google.android.gms")
        } else {
            @Suppress("DEPRECATION")
            val source = context.packageManager
                .getInstallerPackageName(context.packageName)
            source != null && source != context.packageName &&
                !source.contains("com.android.vending") &&
                !source.contains("com.google.android.gms")
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object TamperModule {
    @Provides
    @Singleton
    fun provideTamperDetector(
        @ApplicationContext context: Context
    ): TamperDetector = TamperDetector(context)
}
