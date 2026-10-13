package com.pwtf.konkrremap

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** ADB client that talks to this device's own Wireless debugging. The key pair persists so pairing is one-time. */
class Adb(ctx: Context) : AbsAdbConnectionManager() {
    private val keyFile = File(ctx.noBackupFilesDir, "adb.key")
    private val certFile = File(ctx.noBackupFilesDir, "adb.crt")
    private val keys: Pair<PrivateKey, Certificate> by lazy {
        if (keyFile.exists() && certFile.exists()) {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes())) to
                certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val name = X500Name("CN=Konkr Remap")
            val now = Date()
            val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.time), now, Date(now.time + 100L * 365 * 86_400_000), name, kp.public)
                .build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            keyFile.writeBytes(kp.private.encoded)
            certFile.writeBytes(cert.encoded)
            kp.private to cert
        }
    }

    init { api = Build.VERSION.SDK_INT }

    override fun getPrivateKey() = keys.first
    override fun getCertificate() = keys.second
    override fun getDeviceName() = "Konkr Remap"

    companion object {
        /** Starts the helper over Wireless debugging. Returns null on success, else a message for the user. */
        @Synchronized
        fun startHelper(ctx: Context): String? {
            if (Injector.connected()) return null
            // After a reboot Wireless debugging is off; we can flip it back on once the helper granted us the permission.
            if (canWriteSecureSettings(ctx)) runCatching { Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 1) }
            return try {
                Adb(ctx).use { adb ->
                    if (!adb.connectTls(ctx, 10_000)) return "Wireless debugging not found. Connect to Wi-Fi and turn it on."
                    adb.openStream("shell:" + Helper.startCommand(Process.myUid())).use { it.openInputStream().readBytes() }
                }
                repeat(30) { if (Injector.connected()) return null; Thread.sleep(100) }
                "Helper didn't start. Try again."
            } catch (e: AdbPairingRequiredException) {
                "Not paired yet. Tap Pair first."
            } catch (e: Exception) {
                "Couldn't start: ${e.message}"
            }
        }

        /** Pairs using the code from Settings > Developer options > Wireless debugging > Pair device with pairing code. */
        fun pair(ctx: Context, code: String) {
            val latch = CountDownLatch(1)
            var port = -1
            val mdns = AdbMdns(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, p -> if (p > 0) { port = p; latch.countDown() } }
            mdns.start()
            try {
                latch.await(10, TimeUnit.SECONDS)
            } finally {
                mdns.stop()
            }
            check(port > 0) { "Pairing screen not found. Keep the pairing code dialog open." }
            Adb(ctx).use { it.pair("127.0.0.1", port, code) }
        }

        fun canWriteSecureSettings(ctx: Context) =
            ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /** Turns our accessibility service on directly; skips Android 13's "restricted setting" block for sideloaded apps. */
        fun enableService(ctx: Context): Boolean {
            if (!canWriteSecureSettings(ctx)) return false
            val me = ComponentName(ctx, RemapService::class.java).flattenToString()
            val cr = ctx.contentResolver
            val enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (me !in enabled) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (enabled.isEmpty()) me else "$enabled:$me")
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            return true
        }

        fun serviceEnabled(ctx: Context) =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                .contains(ComponentName(ctx, RemapService::class.java).flattenToString())
    }
}

/** Pairing happens from a notification, because leaving the Settings pairing dialog would cancel it. */
object Pairing {
    private const val CHANNEL = "pairing"
    private const val ID = 1
    const val KEY_CODE = "code"

    fun prompt(ctx: Context, text: String = "Open Wireless debugging > Pair device with pairing code, then enter the code here.") {
        val reply = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, PairReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = Notification.Action.Builder(null, "Enter pairing code", reply)
            .addRemoteInput(RemoteInput.Builder(KEY_CODE).setLabel("6-digit code").build())
            .build()
        notify(ctx, Notification.Builder(ctx, CHANNEL).setContentText(text).addAction(action))
    }

    fun done(ctx: Context, text: String) = notify(ctx, Notification.Builder(ctx, CHANNEL).setContentText(text).setTimeoutAfter(15_000))

    private fun notify(ctx: Context, b: Notification.Builder) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH))
        nm.notify(ID, b.setSmallIcon(R.drawable.ic_launcher_fg).setContentTitle("Konkr Remap").setOnlyAlertOnce(true).build())
    }
}

class PairReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Pairing.KEY_CODE)?.toString()?.trim() ?: return
        val pending = goAsync()
        thread {
            try {
                Adb.pair(ctx, code)
                val err = Adb.startHelper(ctx)
                if (err == null) Adb.enableService(ctx)
                Pairing.done(ctx, err ?: "Ready! Go back to Konkr Remap.")
            } catch (e: Exception) {
                Pairing.prompt(ctx, "Pairing failed (${e.message}). Try again.")
            } finally {
                pending.finish()
            }
        }
    }
}
