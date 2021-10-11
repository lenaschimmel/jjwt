package io.jsonwebtoken.impl.security

import io.jsonwebtoken.security.AeadResult
import io.jsonwebtoken.security.EncryptionAlgorithms
import io.jsonwebtoken.security.SymmetricAeadRequest
import org.junit.Test

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import static org.junit.Assert.assertArrayEquals

/**
 * Tests successful encryption and decryption using 'AES_192_CBC_HMAC_SHA_384' as defined in
 * <a href="https://tools.ietf.org/html/rfc7518#appendix-B.2">RFC 7518, Appendix B.2</a>
 *
 * @since JJWT_RELEASE_VERSION
 */
class RFC7518AppendixB2Test {

    final byte[] K =
            [0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f,
             0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f,
             0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x2f] as byte[]
    final SecretKey KEY = new SecretKeySpec(K, "AES")

    final byte[] P =
            [0x41, 0x20, 0x63, 0x69, 0x70, 0x68, 0x65, 0x72, 0x20, 0x73, 0x79, 0x73, 0x74, 0x65, 0x6d, 0x20,
             0x6d, 0x75, 0x73, 0x74, 0x20, 0x6e, 0x6f, 0x74, 0x20, 0x62, 0x65, 0x20, 0x72, 0x65, 0x71, 0x75,
             0x69, 0x72, 0x65, 0x64, 0x20, 0x74, 0x6f, 0x20, 0x62, 0x65, 0x20, 0x73, 0x65, 0x63, 0x72, 0x65,
             0x74, 0x2c, 0x20, 0x61, 0x6e, 0x64, 0x20, 0x69, 0x74, 0x20, 0x6d, 0x75, 0x73, 0x74, 0x20, 0x62,
             0x65, 0x20, 0x61, 0x62, 0x6c, 0x65, 0x20, 0x74, 0x6f, 0x20, 0x66, 0x61, 0x6c, 0x6c, 0x20, 0x69,
             0x6e, 0x74, 0x6f, 0x20, 0x74, 0x68, 0x65, 0x20, 0x68, 0x61, 0x6e, 0x64, 0x73, 0x20, 0x6f, 0x66,
             0x20, 0x74, 0x68, 0x65, 0x20, 0x65, 0x6e, 0x65, 0x6d, 0x79, 0x20, 0x77, 0x69, 0x74, 0x68, 0x6f,
             0x75, 0x74, 0x20, 0x69, 0x6e, 0x63, 0x6f, 0x6e, 0x76, 0x65, 0x6e, 0x69, 0x65, 0x6e, 0x63, 0x65] as byte[]

    final byte[] IV =
            [0x1a, 0xf3, 0x8c, 0x2d, 0xc2, 0xb9, 0x6f, 0xfd, 0xd8, 0x66, 0x94, 0x09, 0x23, 0x41, 0xbc, 0x04] as byte[]

    final byte[] A =
            [0x54, 0x68, 0x65, 0x20, 0x73, 0x65, 0x63, 0x6f, 0x6e, 0x64, 0x20, 0x70, 0x72, 0x69, 0x6e, 0x63,
             0x69, 0x70, 0x6c, 0x65, 0x20, 0x6f, 0x66, 0x20, 0x41, 0x75, 0x67, 0x75, 0x73, 0x74, 0x65, 0x20,
             0x4b, 0x65, 0x72, 0x63, 0x6b, 0x68, 0x6f, 0x66, 0x66, 0x73] as byte[]

    @SuppressWarnings('unused')
    final byte[] AL = [0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x50] as byte[]

    final byte[] E =
            [0xea, 0x65, 0xda, 0x6b, 0x59, 0xe6, 0x1e, 0xdb, 0x41, 0x9b, 0xe6, 0x2d, 0x19, 0x71, 0x2a, 0xe5,
             0xd3, 0x03, 0xee, 0xb5, 0x00, 0x52, 0xd0, 0xdf, 0xd6, 0x69, 0x7f, 0x77, 0x22, 0x4c, 0x8e, 0xdb,
             0x00, 0x0d, 0x27, 0x9b, 0xdc, 0x14, 0xc1, 0x07, 0x26, 0x54, 0xbd, 0x30, 0x94, 0x42, 0x30, 0xc6,
             0x57, 0xbe, 0xd4, 0xca, 0x0c, 0x9f, 0x4a, 0x84, 0x66, 0xf2, 0x2b, 0x22, 0x6d, 0x17, 0x46, 0x21,
             0x4b, 0xf8, 0xcf, 0xc2, 0x40, 0x0a, 0xdd, 0x9f, 0x51, 0x26, 0xe4, 0x79, 0x66, 0x3f, 0xc9, 0x0b,
             0x3b, 0xed, 0x78, 0x7a, 0x2f, 0x0f, 0xfc, 0xbf, 0x39, 0x04, 0xbe, 0x2a, 0x64, 0x1d, 0x5c, 0x21,
             0x05, 0xbf, 0xe5, 0x91, 0xba, 0xe2, 0x3b, 0x1d, 0x74, 0x49, 0xe5, 0x32, 0xee, 0xf6, 0x0a, 0x9a,
             0xc8, 0xbb, 0x6c, 0x6b, 0x01, 0xd3, 0x5d, 0x49, 0x78, 0x7b, 0xcd, 0x57, 0xef, 0x48, 0x49, 0x27,
             0xf2, 0x80, 0xad, 0xc9, 0x1a, 0xc0, 0xc4, 0xe7, 0x9c, 0x7b, 0x11, 0xef, 0xc6, 0x00, 0x54, 0xe3] as byte[]

    @SuppressWarnings('unused')
    final byte[] M =
            [0x84, 0x90, 0xac, 0x0e, 0x58, 0x94, 0x9b, 0xfe, 0x51, 0x87, 0x5d, 0x73, 0x3f, 0x93, 0xac, 0x20,
             0x75, 0x16, 0x80, 0x39, 0xcc, 0xc7, 0x33, 0xd7, 0x45, 0x94, 0xf8, 0x86, 0xb3, 0xfa, 0xaf, 0xd4,
             0x86, 0xf2, 0x5c, 0x71, 0x31, 0xe3, 0x28, 0x1e, 0x36, 0xc7, 0xa2, 0xd1, 0x30, 0xaf, 0xde, 0x57] as byte[]

    final byte[] T =
            [0x84, 0x90, 0xac, 0x0e, 0x58, 0x94, 0x9b, 0xfe, 0x51, 0x87, 0x5d, 0x73, 0x3f, 0x93, 0xac, 0x20,
             0x75, 0x16, 0x80, 0x39, 0xcc, 0xc7, 0x33, 0xd7] as byte[]

    @Test
    void test() {

        def alg = EncryptionAlgorithms.A192CBC_HS384
        SymmetricAeadRequest req = new DefaultSymmetricAeadRequest(null, null, P, KEY, A, IV)
        AeadResult result = alg.encrypt(req)

        byte[] resultCiphertext = result.getPayload()
        byte[] resultTag = result.getDigest()
        byte[] resultIv = result.getInitializationVector()

        assertArrayEquals E, resultCiphertext
        assertArrayEquals T, resultTag
        assertArrayEquals IV, resultIv //shouldn't have been altered

        // now test decryption:
        def dreq = new DefaultAeadResult(null, null, resultCiphertext, KEY, A, resultTag, resultIv)
        byte[] decryptionResult = alg.decrypt(dreq).getPayload()
        assertArrayEquals(P, decryptionResult)
    }
}
