package me.shkschneider.fingerprintcompat.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.ColorInt
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.shkschneider.fingerprintcompat.FingerprintCompat
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.Mac

class MainActivity : AppCompatActivity(), FingerprintCompat.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fingerprint.setImageDrawable(FingerprintCompat.drawable(applicationContext, Color.BLACK))
        status.text = "Waiting..."
    }

    override fun onResume() {
        super.onResume()

        fingerprintOn()
    }

    fun update(@ColorInt color: Int = Color.BLACK, text: String = "Waiting...") {
        fingerprint.setImageDrawable(FingerprintCompat.drawable(applicationContext, color))
        status.text = text
        Handler(Looper.getMainLooper()).postDelayed({
            fingerprint.setImageDrawable(FingerprintCompat.drawable(applicationContext, Color.BLACK))
            status.text = "Waiting..."
        }, 1000)
    }

    private fun fingerprintOn() {
        if (Build.VERSION.SDK_INT >= 28) {
            if (checkSelfPermission(Manifest.permission.USE_BIOMETRIC) == PackageManager.PERMISSION_GRANTED) {
                FingerprintCompat.foreground(applicationContext, this, "FingerprintCompat")
            } else if (! FingerprintCompat.available(applicationContext)) {
                Toast.makeText(applicationContext, "Unavailable", Toast.LENGTH_SHORT).show()
            } else {
                @Suppress("DEPRECATION")
                requestPermissions(arrayOf(Manifest.permission.USE_FINGERPRINT), 0)
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            if (checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED) {
                FingerprintCompat.background(applicationContext, this)
            } else if (! FingerprintCompat.available(applicationContext)) {
                Toast.makeText(applicationContext, "Unavailable", Toast.LENGTH_SHORT).show()
            } else {
                @Suppress("DEPRECATION")
                requestPermissions(arrayOf(Manifest.permission.USE_FINGERPRINT), 0)
            }
        }
    }

    override fun onFingerprintSucceeded(signature: Signature?, cipher: Cipher?, mac: Mac?) {
        update(Color.GREEN, "OK")
        signature?.let {
            Toast.makeText(applicationContext, signature.toString(), Toast.LENGTH_SHORT).show()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            fingerprintOn()
        }, 2000)
    }

    override fun onFingerprintHelp(helpMsgId: Int, helpString: CharSequence?) {
        update(Color.YELLOW, helpString?.toString().orEmpty())
    }

    override fun onFingerprintFailed() {
        update(Color.RED, "KO")
    }

    override fun onFingerprintError(errMsgId: Int, errString: CharSequence?) {
        update(Color.RED, errString?.toString().orEmpty())
    }

}
