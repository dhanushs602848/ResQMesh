package com.resqmesh.security.keys

import javax.crypto.SecretKey
import java.security.GeneralSecurityException

/** Supplies a provisioned responder-group AES key without embedding key material in code. */
interface ResponderKeyProvider {
    @Throws(GeneralSecurityException::class)
    fun getResponderKey(keyId: String): SecretKey?
}
