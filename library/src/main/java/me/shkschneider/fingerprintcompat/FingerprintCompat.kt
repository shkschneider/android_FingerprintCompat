package me.shkschneider.fingerprintcompat

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Handler
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.support.annotation.ColorInt
import android.support.annotation.RequiresApi
import android.support.annotation.RequiresPermission
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat
import android.support.v4.os.CancellationSignal
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * As of now, only supports background detection.
 */
object FingerprintCompat {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY = "FingerPrint"

    @SuppressLint("MissingPermission")
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) {
            return false
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) return false
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        keyguardManager ?: return false
        if (!keyguardManager.isKeyguardSecure) return false
        FingerprintManagerCompat.from(context).let {
            if (! it.isHardwareDetected) return false
            if (! it.hasEnrolledFingerprints()) return false
        }
        return true
    }

    @RequiresApi(23)
    private fun signature(): FingerprintManagerCompat.CryptoObject {
        val keyStore = KeyStore.getInstance(KEYSTORE)
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        keyStore.load(null)
        keyGenerator.init(KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .build())
        keyGenerator.generateKey()
        val cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_CBC + "/"
                + KeyProperties.ENCRYPTION_PADDING_PKCS7)
        val key = keyStore.getKey(KEY, null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return FingerprintManagerCompat.CryptoObject(cipher)
    }

    @RequiresApi(23)
    @RequiresPermission(Manifest.permission.USE_FINGERPRINT)
    fun authenticate(context: Context, callback: Callback) : Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            callback.onFingerprintError(0, "Build.VERSION.SDK_INT")
            return false
        }
        val fingerprintManager = FingerprintManagerCompat.from(context)
        if (! fingerprintManager.hasEnrolledFingerprints()) {
            callback.onFingerprintError(0, "hasEnrolledFingerprints")
            return false
        }
        if (! fingerprintManager.isHardwareDetected) {
            callback.onFingerprintError(0, "isHardwareDetected")
            return false
        }
        return authenticate23(fingerprintManager, context, callback)
    }

    @RequiresApi(23)
    @RequiresPermission(Manifest.permission.USE_FINGERPRINT)
    fun authenticate23(fingerprintManager: FingerprintManagerCompat, context: Context, callback: Callback) : Boolean {
        val cancellationSignal = CancellationSignal()
        fingerprintManager.authenticate(signature(), 0, cancellationSignal, object: FingerprintManagerCompat.AuthenticationCallback() {
            override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
                super.onAuthenticationHelp(helpMsgId, helpString)
                callback.onFingerprintHelp(helpMsgId, helpString)
            }
            override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult?) {
                super.onAuthenticationSucceeded(result)
                callback.onFingerprintSucceeded(result?.cryptoObject)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onFingerprintFailed()
            }
            override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
                super.onAuthenticationError(errMsgId, errString)
                callback.onFingerprintError(errMsgId, errString)
            }
        }, Handler())
        return true
    }

    @RequiresApi(28)
    @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    fun authenticate28(context: Context) {
        BiometricPrompt.Builder(context)
                .setTitle("Title")
                .setSubtitle("Subtitle")
                .setDescription("Description")
                .setNegativeButton("Negative", context.mainExecutor, DialogInterface.OnClickListener { dialog, _ ->
                    dialog.dismiss()
                })
                .build()
    }

    fun drawable(context: Context, @ColorInt color: Int = Color.WHITE): Drawable? {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_fingerprint)
        drawable?.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
        return drawable
    }

    open interface Callback {

        open fun onFingerprintSucceeded(result: FingerprintManagerCompat.CryptoObject?)

        open fun onFingerprintHelp(helpMsgId: Int, helpString: CharSequence?)

        open fun onFingerprintFailed()

        open fun onFingerprintError(errMsgId: Int, errString: CharSequence?)

    }

}
