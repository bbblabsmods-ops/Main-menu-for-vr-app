package com.example.questhub.tools

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** Wireless-debugging ADB client (same libadb-android stack the WirelessDebugOverlay APK uses). */
class AdbManager private constructor(private val appCtx: Context) : AbsAdbConnectionManager() {
    private val keys: Pair<PrivateKey, Certificate>

    init {
        setApi(Build.VERSION.SDK_INT)
        val kf = File(appCtx.filesDir, "adb_key.pk8")
        val cf = File(appCtx.filesDir, "adb_cert.der")
        keys = if (kf.exists() && cf.exists()) {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(kf.readBytes())) to
                CertificateFactory.getInstance("X.509").generateCertificate(cf.inputStream())
        } else {
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val subject = X500Name("CN=QuestHub")
            val now = System.currentTimeMillis()
            val holder = JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), Date(now - 86_400_000L), Date(now + 3_650L * 86_400_000L),
                subject, kp.public
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(kp.private))
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            kf.writeBytes(kp.private.encoded); cf.writeBytes(cert.encoded)
            kp.private to cert
        }
    }

    override fun getPrivateKey(): PrivateKey = keys.first
    override fun getCertificate(): Certificate = keys.second
    override fun getDeviceName(): String = "QuestHub"

    /** Runs one shell command on the headset and returns its output. */
    fun shell(cmd: String): String =
        openStream("shell:$cmd").use { s -> s.openInputStream().bufferedReader().readText() }

    /** Connect if needed (auto-discovers the Wireless Debugging port; no IP or port needed). */
    fun ensureConnected(): Boolean {
        if (isConnected) return true
        return try { autoConnect(appCtx, 8_000L) } catch (_: Exception) { false }
    }

    companion object {
        @Volatile private var inst: AdbManager? = null
        fun get(ctx: Context): AdbManager = inst ?: synchronized(this) {
            inst ?: AdbManager(ctx.applicationContext).also { inst = it }
        }
    }
}
