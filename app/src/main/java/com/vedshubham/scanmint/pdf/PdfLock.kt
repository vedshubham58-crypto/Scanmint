package com.vedshubham.scanmint.pdf

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password protection for the PDFs we write: the PDF "Standard security handler",
 * revision 4 with 128-bit AES (opens in every common PDF reader, phone or computer).
 * Anyone opening the file must type the password.
 */
class PdfLock(password: String, val fileId: ByteArray = randomBytes(16)) {

    /** Values that go into the /Encrypt dictionary. */
    val ownerKey: ByteArray
    val userKey: ByteArray
    private val fileKey: ByteArray

    init {
        val user = pad(password)
        // Owner password = user password: whoever knows it can open and print the file.
        ownerKey = computeOwnerKey(user, user)
        fileKey = computeFileKey(user, ownerKey, PERMISSIONS, fileId)
        userKey = computeUserKey(fileKey, fileId)
    }

    /** Encrypts the bytes of object [objectNumber] (generation 0). */
    fun encrypt(objectNumber: Int, data: ByteArray): ByteArray {
        val key = objectKey(objectNumber)
        val iv = randomBytes(16)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return iv + cipher.doFinal(data)
    }

    fun encryptDictionary(): String =
        "<< /Filter /Standard /V 4 /R 4 /Length 128 " +
            "/CF << /StdCF << /AuthEvent /DocOpen /CFM /AESV2 /Length 16 >> >> /StmF /StdCF /StrF /StdCF " +
            "/O <${hex(ownerKey)}> /U <${hex(userKey)}> /P $PERMISSIONS >>"

    fun idArray(): String = "[<${hex(fileId)}> <${hex(fileId)}>]"

    private fun objectKey(objectNumber: Int): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        md5.update(fileKey)
        md5.update(byteArrayOf(objectNumber.toByte(), (objectNumber shr 8).toByte(), (objectNumber shr 16).toByte(), 0, 0))
        md5.update(byteArrayOf(0x73, 0x41, 0x6C, 0x54)) // "sAlT", required for AES
        return md5.digest()
    }

    companion object {
        /** Everything allowed (print, copy, edit); the password only guards opening. */
        const val PERMISSIONS = -4

        private val PADDING = intArrayOf(
            0x28, 0xBF, 0x4E, 0x5E, 0x4E, 0x75, 0x8A, 0x41, 0x64, 0x00, 0x4E, 0x56, 0xFF, 0xFA, 0x01, 0x08,
            0x2E, 0x2E, 0x00, 0xB6, 0xD0, 0x68, 0x3E, 0x80, 0x2F, 0x0C, 0xA9, 0xFE, 0x64, 0x53, 0x69, 0x7A,
        ).map { it.toByte() }.toByteArray()

        private val random = SecureRandom()

        fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

        /** Passwords are Latin-1 in this PDF version, so other letters are replaced by "?". */
        fun pad(password: String): ByteArray {
            val raw = password.toByteArray(Charsets.ISO_8859_1).take(32).toByteArray()
            return raw + PADDING.copyOfRange(0, 32 - raw.size)
        }

        /** True when the password only uses letters that work in every PDF reader. */
        fun isSupported(password: String): Boolean = password.all { it.code in 32..126 }

        private fun md5(vararg parts: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("MD5")
            parts.forEach { md.update(it) }
            return md.digest()
        }

        // Algorithm 3 of the PDF spec.
        private fun computeOwnerKey(ownerPadded: ByteArray, userPadded: ByteArray): ByteArray {
            var hash = md5(ownerPadded)
            repeat(50) { hash = md5(hash) }
            val key = hash.copyOf(16)
            var out = rc4(key, userPadded)
            for (i in 1..19) out = rc4(ByteArray(16) { (key[it].toInt() xor i).toByte() }, out)
            return out
        }

        // Algorithm 2.
        private fun computeFileKey(userPadded: ByteArray, owner: ByteArray, permissions: Int, id: ByteArray): ByteArray {
            val p = byteArrayOf(permissions.toByte(), (permissions shr 8).toByte(), (permissions shr 16).toByte(), (permissions shr 24).toByte())
            var hash = md5(userPadded, owner, p, id)
            repeat(50) { hash = md5(hash.copyOf(16)) }
            return hash.copyOf(16)
        }

        // Algorithm 5.
        private fun computeUserKey(fileKey: ByteArray, id: ByteArray): ByteArray {
            var out = rc4(fileKey, md5(PADDING, id))
            for (i in 1..19) out = rc4(ByteArray(16) { (fileKey[it].toInt() xor i).toByte() }, out)
            return out + ByteArray(16)
        }

        /** RC4 is only used to make the password check values, as the PDF spec requires. */
        internal fun rc4(key: ByteArray, data: ByteArray): ByteArray {
            val s = IntArray(256) { it }
            var j = 0
            for (i in 0 until 256) {
                j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
                val t = s[i]; s[i] = s[j]; s[j] = t
            }
            var a = 0
            var b = 0
            return ByteArray(data.size) { k ->
                a = (a + 1) and 0xFF
                b = (b + s[a]) and 0xFF
                val t = s[a]; s[a] = s[b]; s[b] = t
                (data[k].toInt() xor s[(s[a] + s[b]) and 0xFF]).toByte()
            }
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
