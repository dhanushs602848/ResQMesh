package com.resqmesh.security.keys

import java.security.MessageDigest

/** SHA-256 verification fingerprint; compare out of band with the intended responder group. */
object KeyFingerprint {
    fun sha256(keyMaterial: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(keyMaterial)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
