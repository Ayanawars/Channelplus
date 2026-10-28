package tv.own.owntv.core.metadata

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local credentials; deliberately excluded from portable backups. Never log this payload. */
class ExternalMetadataConfig(
    val enabled: Boolean = true,
    val tvdbKey: String = "",
    val tvdbPin: String = "",
    val traktClientId: String = "",
) {
    internal fun json() = JSONObject().put("enabled", enabled)
        .put("tvdb", tvdbKey).put("pin", tvdbPin).put("trakt", traktClientId)
}

class ExternalMetadataStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "external-metadata.sealed"))
    private val mutable = MutableStateFlow(load())
    val config = mutable.asStateFlow()

    /** Throws on storage/Keystore failure so Settings never reports a failed save as successful. */
    @Synchronized
    fun save(value: ExternalMetadataConfig) {
        persist(value)
        mutable.value = value
    }

    private fun persist(value: ExternalMetadataConfig) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.json().toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(cipher.iv.size)
            stream.write(cipher.iv)
            stream.write(ciphertext)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun load(): ExternalMetadataConfig = runCatching {
        if (!file.baseFile.exists()) return@runCatching ExternalMetadataConfig()
        val bytes = file.readFully()
        val n = bytes.first().toInt()
        require(n == 12 && bytes.size > n + 17)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, n + 1)))
        val o = JSONObject(String(cipher.doFinal(bytes.copyOfRange(n + 1, bytes.size)), Charsets.UTF_8))
        ExternalMetadataConfig(o.optBoolean("enabled", true), o.optString("tvdb"), o.optString("pin"), o.optString("trakt")).also { clean ->
            // Remove unsupported legacy credential fields while preserving current service settings.
            if (o.length() != clean.json().length()) persist(clean)
        }
    }.getOrElse { ExternalMetadataConfig() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "channelplus_external_metadata"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
