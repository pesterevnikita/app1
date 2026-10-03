package io.github.pesterevnikita.focusgate.security
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
data class PasswordHash(val salt: String, val hash: String, val iterations: Int, val version: Int = 1)
object PasswordVerifier {
    /** PBKDF2 makes guesses expensive; clear the crypto library's temporary password copy afterward. */
    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec=PBEKeySpec(password,salt,iterations,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    /** A fresh random salt ensures two identical passwords do not produce identical stored records. */
    fun create(password: CharArray, iterations: Int = 210000): PasswordHash {
        require(password.size>=6) { "Use at least six characters." }; require(iterations>=10000)
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
        return PasswordHash(Base64.getEncoder().encodeToString(salt),Base64.getEncoder().encodeToString(derive(password,salt,iterations)),iterations)
    }
    /** Constant-time byte comparison; malformed/unsupported records fail closed without throwing to UI. */
    fun verify(password: CharArray, stored: PasswordHash): Boolean = runCatching {
        require(stored.version==1 && stored.iterations in 10000..2000000)
        MessageDigest.isEqual(Base64.getDecoder().decode(stored.hash),derive(password,Base64.getDecoder().decode(stored.salt),stored.iterations))
    }.getOrDefault(false)
}
