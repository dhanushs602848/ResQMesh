package com.resqmesh.security.keys

import javax.crypto.SecretKey

/** Supplies a provisioned responder-group AES key without embedding key material in code. */
interface ResponderKeyProvider {
    @Throws(Exception::class)
    fun getResponderKey(keyId: String): SecretKey?
}
