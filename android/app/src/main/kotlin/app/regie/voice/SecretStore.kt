package app.regie.voice

import android.content.Context
import androidx.core.content.edit
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets typed by the user (the ElevenLabs key), encrypted with AES-GCM by a key of the Android
 * Keystore. The key never leaves the Keystore; only the IV and the ciphertext are stored, in
 * private preferences excluded from backups. Values are never logged.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private val cache = HashMap<String, String?>()

    @Synchronized
    fun read(name: String): String? = cache.getOrPut(name) { decrypt(prefs.getString(name, null)) }

    fun contains(name: String): Boolean = prefs.contains(name)

    @Synchronized
    fun write(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit { putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
        cache[name] = value
    }

    @Synchronized
    fun delete(name: String) {
        prefs.edit { remove(name) }
        cache.remove(name)
    }

    private fun decrypt(stored: String?): String? {
        if (stored == null) return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null // the Keystore key was lost (app data cleared): the user types the key again
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val ELEVENLABS_KEY = "elevenlabs_api_key"

        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "regie-voice-secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
    }
}
