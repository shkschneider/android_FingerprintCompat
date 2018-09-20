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
import java.security.KeyStore
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

object FingerprintCompat {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY = "FingerPrint"

    @SuppressLint("MissingPermission")
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 23) {
            return false
        }
        @Suppress("DEPRECATION")
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

    /**
     * This uses API-23.
     * There is no interface for this, but you could always implement
     * your own Dialog or whatever and call this yourself then.
     */
    @Suppress("DEPRECATION")
    @RequiresApi(23)
    @RequiresPermission(Manifest.permission.USE_FINGERPRINT)
    fun background(context: Context, callback: Callback): android.support.v4.os.CancellationSignal? {
        if (! available(context)) return null
        val cancellationSignal = android.support.v4.os.CancellationSignal()
        val fingerprintManager = FingerprintManagerCompat.from(context)
        fingerprintManager.authenticate(signature(), 0, cancellationSignal, object: FingerprintManagerCompat.AuthenticationCallback() {
            override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
                super.onAuthenticationHelp(helpMsgId, helpString)
                callback.onFingerprintHelp(helpMsgId, helpString)
            }
            override fun onAuthenticationSucceeded(result: FingerprintManagerCompat.AuthenticationResult?) {
                super.onAuthenticationSucceeded(result)
                // result?.cryptoObject as FingerprintManagerCompat.CryptoObject
                callback.onFingerprintSucceeded(result?.cryptoObject?.signature, result?.cryptoObject?.cipher, result?.cryptoObject?.mac)
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
        return cancellationSignal
    }

    /**
     * This uses API-28 with the build-in prompt dialog.
     */
    @RequiresApi(28)
    @RequiresPermission(Manifest.permission.USE_BIOMETRIC)
    fun foreground(context: Context, callback: Callback, title: String, subtitle: String? = null, description: String? = null): android.os.CancellationSignal? {
        if (! available(context)) return null
        val biometricPromptBuilder = BiometricPrompt.Builder(context).setTitle(title)
        subtitle?.let {
            biometricPromptBuilder.setSubtitle(subtitle)
        }
        description?.let {
            biometricPromptBuilder.setDescription(description)
        }
        biometricPromptBuilder.setNegativeButton(context.getString(android.R.string.cancel), context.mainExecutor, DialogInterface.OnClickListener { dialog, _ ->
            callback.onFingerprintFailed()
            dialog.dismiss()
        })
        val cancellationSignal = android.os.CancellationSignal()
        biometricPromptBuilder.build().authenticate(cancellationSignal, context.mainExecutor, object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationHelp(helpMsgId: Int, helpString: CharSequence?) {
                super.onAuthenticationHelp(helpMsgId, helpString)
                callback.onFingerprintHelp(helpMsgId, helpString)
            }
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                super.onAuthenticationSucceeded(result)
                // result?.cryptoObject as BiometricPrompt.CryptoObject
                callback.onFingerprintSucceeded(result?.cryptoObject?.signature, result?.cryptoObject?.cipher, result?.cryptoObject?.mac)
            }
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback.onFingerprintFailed()
            }
            override fun onAuthenticationError(errMsgId: Int, errString: CharSequence?) {
                super.onAuthenticationError(errMsgId, errString)
                callback.onFingerprintError(errMsgId, errString)
            }
        })
        return cancellationSignal
    }

    fun drawable(context: Context, @ColorInt color: Int = Color.WHITE): Drawable? {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_fingerprint)
        drawable?.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
        return drawable
    }

    interface Callback {

        fun onFingerprintSucceeded(signature: Signature?, cipher: Cipher?, mac: Mac?)

        fun onFingerprintHelp(helpMsgId: Int, helpString: CharSequence?)

        fun onFingerprintFailed()

        fun onFingerprintError(errMsgId: Int, errString: CharSequence?)

    }

}
