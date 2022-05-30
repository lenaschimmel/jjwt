/*
 * Copyright (C) 2014 jsonwebtoken.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jsonwebtoken

import io.jsonwebtoken.impl.*
import io.jsonwebtoken.impl.compression.DefaultCompressionCodecResolver
import io.jsonwebtoken.impl.compression.GzipCompressionCodec
import io.jsonwebtoken.impl.lang.Services
import io.jsonwebtoken.impl.security.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.io.Serializer
import io.jsonwebtoken.lang.Strings
import io.jsonwebtoken.security.*
import org.junit.Test

import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

import static org.junit.Assert.*

class JwtsTest {

    private static Date now() {
        return dateWithOnlySecondPrecision(System.currentTimeMillis())
    }

    private static int later() {
        def date = laterDate(10000)
        def seconds = date.getTime() / 1000
        return seconds as int
    }

    private static Date laterDate(int seconds) {
        def millis = seconds * 1000L
        def time = System.currentTimeMillis() + millis
        return dateWithOnlySecondPrecision(time)
    }

    private static Date dateWithOnlySecondPrecision(long millis) {
        long seconds = (millis / 1000) as long
        long secondOnlyPrecisionMillis = seconds * 1000
        return new Date(secondOnlyPrecisionMillis)
    }

    protected static String base64Url(String s) {
        byte[] bytes = s.getBytes(Strings.UTF_8)
        return Encoders.BASE64URL.encode(bytes)
    }

    protected static String toJson(o) {
        def serializer = Services.loadFirst(Serializer)
        byte[] bytes = serializer.serialize(o)
        return new String(bytes, Strings.UTF_8)
    }

    @Test
    void testPrivateCtor() { // for code coverage only
        //noinspection GroovyAccessibility
        new Jwts()
    }

    @Test
    void testHeaderWithNoArgs() {
        def header = Jwts.header()
        assertTrue header instanceof DefaultHeader
    }

    @Test
    void testHeaderWithMapArg() {
        def header = Jwts.header([alg: "HS256"])
        assertTrue header instanceof DefaultHeader
        assertEquals 'HS256', header.alg
    }

    @Test
    void testJwsHeaderWithNoArgs() {
        def header = Jwts.jwsHeader()
        assertTrue header instanceof DefaultJwsHeader
    }

    @Test
    void testJwsHeaderWithMapArg() {
        def header = Jwts.jwsHeader([alg: "HS256"])
        assertTrue header instanceof DefaultJwsHeader
        assertEquals 'HS256', header.getAlgorithm()
    }

    @Test
    void testJweHeaderWithNoArgs() {
        def header = Jwts.jweHeader()
        assertTrue header instanceof DefaultJweHeader
    }

    @Test
    void testJweHeaderWithMapArg() {
        def header = Jwts.jweHeader([enc: 'foo'])
        assertTrue header instanceof DefaultJweHeader
        assertEquals 'foo', header.getEncryptionAlgorithm()
    }

    @Test
    void testClaims() {
        Claims claims = Jwts.claims()
        assertNotNull claims
    }

    @Test
    void testClaimsWithMapArg() {
        Claims claims = Jwts.claims([sub: 'Joe'])
        assertNotNull claims
        assertEquals 'Joe', claims.getSubject()
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseMalformedHeader() {
        def headerString = '{"jku":42}' // cannot be parsed as a URI --> malformed header
        def claimsString = '{"sub":"joe"}'
        def encodedHeader = base64Url(headerString)
        def encodedClaims = base64Url(claimsString)
        def compact = encodedHeader + '.' + encodedClaims + '.AAD='
        try {
            Jwts.parserBuilder().build().parseClaimsJws(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Invalid protected header: Invalid JWS header \'jku\' (JWK Set URL) value: 42. ' +
                    'Cause: Values must be either String or java.net.URI instances. ' +
                    'Value type found: java.lang.Integer.'
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseMalformedClaims() {
        def key = TestKeys.HS256
        def h = base64Url('{"alg":"HS256"}')
        def c = base64Url('{"sub":"joe","exp":"-42-"}')
        def payload = ("$h.$c" as String).getBytes(StandardCharsets.UTF_8)
        def result = SignatureAlgorithms.HS256.sign(new DefaultSignatureRequest<SecretKey>(null, null, payload, key))
        def sig = Encoders.BASE64URL.encode(result)
        def compact = "$h.$c.$sig" as String
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Invalid claims: Invalid JWT Claim \'exp\' (Expiration Time) value: -42-. Cause: ' +
                    'String value is not a JWT NumericDate, nor is it ISO-8601-formatted. All heuristics exhausted. ' +
                    'Cause: Unparseable date: "-42-"'
            assertEquals expected, e.getMessage()
        }
    }

    @Test
    void testPlaintextJwtString() {
        // Assert exact output per example at https://datatracker.ietf.org/doc/html/rfc7519#section-6.1
        String encodedBody = 'eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ'
        String payload = new String(Decoders.BASE64URL.decode(encodedBody), StandardCharsets.UTF_8)
        String val = Jwts.builder().setPayload(payload).compact()
        String RFC_VALUE = 'eyJhbGciOiJub25lIn0.eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ.'
        assertEquals RFC_VALUE, val
    }

    @Test
    void testParsePlaintextToken() {

        def claims = [iss: 'joe', exp: later(), 'https://example.com/is_root': true]

        String jwt = Jwts.builder().setClaims(claims).compact()

        def token = Jwts.parserBuilder().enableUnsecuredJws().build().parse(jwt)

        //noinspection GrEqualsBetweenInconvertibleTypes
        assert token.body == claims
    }

    @Test(expected = IllegalArgumentException)
    void testParseNull() {
        Jwts.parserBuilder().build().parse(null)
    }

    @Test(expected = IllegalArgumentException)
    void testParseEmptyString() {
        Jwts.parserBuilder().build().parse('')
    }

    @Test(expected = IllegalArgumentException)
    void testParseWhitespaceString() {
        Jwts.parserBuilder().build().parse('   ')
    }

    @Test
    void testParseWithNoPeriods() {
        try {
            Jwts.parserBuilder().build().parse('foo')
            fail()
        } catch (MalformedJwtException e) {
            //noinspection GroovyAccessibility
            String expected = JwtTokenizer.DELIM_ERR_MSG_PREFIX + '0'
            assertEquals expected, e.message
        }
    }

    @Test
    void testParseWithOnePeriodOnly() {
        try {
            Jwts.parserBuilder().build().parse('.')
            fail()
        } catch (MalformedJwtException e) {
            //noinspection GroovyAccessibility
            String expected = JwtTokenizer.DELIM_ERR_MSG_PREFIX + '1'
            assertEquals expected, e.message
        }
    }

    @Test
    void testParseWithTwoPeriodsOnly() {
        try {
            Jwts.parserBuilder().build().parse('..')
            fail()
        } catch (MalformedJwtException e) {
            String msg = 'Compact JWT strings MUST always have a Base64Url protected header per ' +
                    'https://tools.ietf.org/html/rfc7519#section-7.2 (steps 2-4).'
            assertEquals msg, e.message
        }
    }

    @Test
    void testParseWithHeaderOnly() {
        String unsecuredJwt = base64Url("{\"alg\":\"none\"}") + ".."
        Jwt jwt = Jwts.parserBuilder().enableUnsecuredJws().build().parse(unsecuredJwt)
        assertEquals "none", jwt.getHeader().get("alg")
    }

    @Test
    void testParseWithSignatureOnly() {
        try {
            Jwts.parserBuilder().build().parse('..bar')
            fail()
        } catch (MalformedJwtException e) {
            assertEquals 'Compact JWT strings MUST always have a Base64Url protected header per https://tools.ietf.org/html/rfc7519#section-7.2 (steps 2-4).', e.message
        }
    }

    @Test
    void testParseWithMissingRequiredSignature() {
        Key key = SignatureAlgorithms.HS256.keyBuilder().build()
        String compact = Jwts.builder().setSubject('foo').signWith(key).compact()
        int i = compact.lastIndexOf('.')
        String missingSig = compact.substring(0, i + 1)
        try {
            Jwts.parserBuilder().enableUnsecuredJws().setSigningKey(key).build().parseClaimsJws(missingSig)
            fail()
        } catch (MalformedJwtException expected) {
            String s = String.format(DefaultJwtParser.MISSING_JWS_DIGEST_MSG_FMT, 'HS256')
            assertEquals s, expected.getMessage()
        }
    }

    @Test
    void testWithInvalidCompressionAlgorithm() {
        try {

            Jwts.builder().setHeaderParam(Header.COMPRESSION_ALGORITHM, "CUSTOM").setId("andId").compact()
        } catch (CompressionException e) {
            assertEquals "Unsupported compression algorithm 'CUSTOM'", e.getMessage()
        }
    }

    @Test
    void testConvenienceIssuer() {
        String compact = Jwts.builder().setIssuer("Me").compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertEquals 'Me', claims.getIssuer()

        compact = Jwts.builder().setSubject("Joe")
                .setIssuer("Me") //set it
                .setIssuer(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getIssuer()
    }

    @Test
    void testConvenienceSubject() {
        String compact = Jwts.builder().setSubject("Joe").compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertEquals 'Joe', claims.getSubject()

        compact = Jwts.builder().setIssuer("Me")
                .setSubject("Joe") //set it
                .setSubject(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getSubject()
    }

    @Test
    void testConvenienceAudience() {
        String compact = Jwts.builder().setAudience("You").compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertEquals 'You', claims.getAudience()

        compact = Jwts.builder().setIssuer("Me")
                .setAudience("You") //set it
                .setAudience(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getAudience()
    }

    @Test
    void testConvenienceExpiration() {
        Date then = laterDate(10000)
        String compact = Jwts.builder().setExpiration(then).compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        def claimedDate = claims.getExpiration()
        assertEquals then, claimedDate

        compact = Jwts.builder().setIssuer("Me")
                .setExpiration(then) //set it
                .setExpiration(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getExpiration()
    }

    @Test
    void testConvenienceNotBefore() {
        Date now = now() //jwt exp only supports *seconds* since epoch:
        String compact = Jwts.builder().setNotBefore(now).compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        def claimedDate = claims.getNotBefore()
        assertEquals now, claimedDate

        compact = Jwts.builder().setIssuer("Me")
                .setNotBefore(now) //set it
                .setNotBefore(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getNotBefore()
    }

    @Test
    void testConvenienceIssuedAt() {
        Date now = now() //jwt exp only supports *seconds* since epoch:
        String compact = Jwts.builder().setIssuedAt(now).compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        def claimedDate = claims.getIssuedAt()
        assertEquals now, claimedDate

        compact = Jwts.builder().setIssuer("Me")
                .setIssuedAt(now) //set it
                .setIssuedAt(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getIssuedAt()
    }

    @Test
    void testConvenienceId() {
        String id = UUID.randomUUID().toString()
        String compact = Jwts.builder().setId(id).compact()
        Claims claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertEquals id, claims.getId()

        compact = Jwts.builder().setIssuer("Me")
                .setId(id) //set it
                .setId(null) //null should remove it
                .compact()

        claims = Jwts.parserBuilder().enableUnsecuredJws().build().parse(compact).body as Claims
        assertNull claims.getId()
    }

    @Test
    void testUncompressedJwt() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String id = UUID.randomUUID().toString()

        String compact = Jwts.builder().setId(id).setAudience("an audience").signWith(key, alg)
                .claim("state", "hello this is an amazing jwt").compact()

        def jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(compact)

        Claims claims = jws.body

        assertNull jws.header.getCompressionAlgorithm()

        assertEquals id, claims.getId()
        assertEquals "an audience", claims.getAudience()
        assertEquals "hello this is an amazing jwt", claims.state
    }

    @Test
    void testCompressedJwtWithDeflate() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String id = UUID.randomUUID().toString()

        String compact = Jwts.builder().setId(id).setAudience("an audience").signWith(key, alg)
                .claim("state", "hello this is an amazing jwt").compressWith(CompressionCodecs.DEFLATE).compact()

        def jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(compact)

        Claims claims = jws.body

        assertEquals "DEF", jws.header.getCompressionAlgorithm()

        assertEquals id, claims.getId()
        assertEquals "an audience", claims.getAudience()
        assertEquals "hello this is an amazing jwt", claims.state
    }

    @Test
    void testCompressedJwtWithGZIP() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String id = UUID.randomUUID().toString()

        String compact = Jwts.builder().setId(id).setAudience("an audience").signWith(key, alg)
                .claim("state", "hello this is an amazing jwt").compressWith(CompressionCodecs.GZIP).compact()

        def jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(compact)

        Claims claims = jws.body

        assertEquals "GZIP", jws.header.getCompressionAlgorithm()

        assertEquals id, claims.getId()
        assertEquals "an audience", claims.getAudience()
        assertEquals "hello this is an amazing jwt", claims.state
    }

    @Test
    void testCompressedWithCustomResolver() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String id = UUID.randomUUID().toString()

        String compact = Jwts.builder().setId(id).setAudience("an audience").signWith(key, alg)
                .claim("state", "hello this is an amazing jwt").compressWith(new GzipCompressionCodec() {
            @Override
            String getId() {
                return "CUSTOM"
            }
        }).compact()

        def jws = Jwts.parserBuilder().setSigningKey(key).setCompressionCodecResolver(new DefaultCompressionCodecResolver() {
            @Override
            CompressionCodec resolveCompressionCodec(Header header) {
                String algorithm = header.getCompressionAlgorithm()
                //noinspection ChangeToOperator
                if ("CUSTOM".equals(algorithm)) {
                    return CompressionCodecs.GZIP
                } else {
                    return null
                }
            }
        }).build().parseClaimsJws(compact)

        Claims claims = jws.body

        assertEquals "CUSTOM", jws.header.getCompressionAlgorithm()

        assertEquals id, claims.getId()
        assertEquals "an audience", claims.getAudience()
        assertEquals "hello this is an amazing jwt", claims.state

    }

    @Test(expected = CompressionException.class)
    void testCompressedJwtWithUnrecognizedHeader() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String id = UUID.randomUUID().toString()

        String compact = Jwts.builder().setId(id).setAudience("an audience").signWith(key, alg)
                .claim("state", "hello this is an amazing jwt").compressWith(new GzipCompressionCodec() {
            @Override
            String getId() {
                return "CUSTOM"
            }
        }).compact()

        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(compact)
    }

    @Test
    void testCompressStringPayloadWithDeflate() {

        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String payload = "this is my test for a payload"

        String compact = Jwts.builder().setPayload(payload).signWith(key, alg)
                .compressWith(CompressionCodecs.DEFLATE).compact()

        def jws = Jwts.parserBuilder().setSigningKey(key).build().parsePlaintextJws(compact)

        String parsed = jws.body

        assertEquals "DEF", jws.header.getCompressionAlgorithm()

        assertEquals "this is my test for a payload", parsed
    }

    @Test
    void testHS256() {
        testHmac(SignatureAlgorithms.HS256)
    }

    @Test
    void testHS384() {
        testHmac(SignatureAlgorithms.HS384)
    }

    @Test
    void testHS512() {
        testHmac(SignatureAlgorithms.HS512)
    }

    @Test
    void testRS256() {
        testRsa(SignatureAlgorithms.RS256)
    }

    @Test
    void testRS384() {
        testRsa(SignatureAlgorithms.RS384)
    }

    @Test
    void testRS512() {
        testRsa(SignatureAlgorithms.RS512)
    }

    @Test
    void testPS256() {
        testRsa(SignatureAlgorithms.PS256)
    }

    @Test
    void testPS384() {
        testRsa(SignatureAlgorithms.PS384)
    }

    @Test
    void testPS512() {
        testRsa(SignatureAlgorithms.PS512)
    }

    @Test
    void testRSA256WithPrivateKeyValidation() {
        testRsa(SignatureAlgorithms.RS256, true)
    }

    @Test
    void testRSA384WithPrivateKeyValidation() {
        testRsa(SignatureAlgorithms.RS384, true)
    }

    @Test
    void testRSA512WithPrivateKeyValidation() {
        testRsa(SignatureAlgorithms.RS512, true)
    }

    @Test
    void testES256() {
        testEC(SignatureAlgorithms.ES256)
    }

    @Test
    void testES384() {
        testEC(SignatureAlgorithms.ES384)
    }

    @Test
    void testES512() {
        testEC(SignatureAlgorithms.ES512)
    }

    @Test
    void testES256WithPrivateKeyValidation() {
        try {
            testEC(SignatureAlgorithms.ES256, true)
            fail("EC private keys cannot be used to validate EC signatures.")
        } catch (UnsupportedJwtException e) {
            String msg = "Elliptic Curve verification keys must be PublicKeys (implement java.security.PublicKey). " +
                    "Provided key type: sun.security.ec.ECPrivateKeyImpl."
            assertEquals msg, e.cause.message
        }
    }

    @Test(expected = WeakKeyException)
    void testParseClaimsJwsWithWeakHmacKey() {

        def alg = SignatureAlgorithms.HS384
        def key = alg.keyBuilder().build()
        def weakKey = SignatureAlgorithms.HS256.keyBuilder().build()

        String jws = Jwts.builder().setSubject("Foo").signWith(key, alg).compact()

        Jwts.parserBuilder().setSigningKey(weakKey).build().parseClaimsJws(jws)
        fail('parseClaimsJws must fail for weak keys')
    }

    /**
     * @since 0.11.5
     */
    @Test
    void testBuilderWithEcdsaPublicKey() {
        def builder = Jwts.builder().setSubject('foo')
        def pair = TestKeys.ES256.pair
        try {
            builder.signWith(pair.public, SignatureAlgorithms.ES256) //public keys can't be used to create signatures
        } catch (InvalidKeyException expected) {
            String msg = "ECDSA signing keys must be PrivateKey instances."
            assertEquals msg, expected.getMessage()
        }
    }

    /**
     * @since 0.11.5 as part of testing guards against JVM CVE-2022-21449
     */
    @Test
    void testBuilderWithMismatchedEllipticCurveKeyAndAlgorithm() {
        def builder = Jwts.builder().setSubject('foo')
        def pair = TestKeys.ES384.pair
        try {
            builder.signWith(pair.private, SignatureAlgorithms.ES256)
            //ES384 keys can't be used to create ES256 signatures
        } catch (InvalidKeyException expected) {
            String msg = "EllipticCurve key has a field size of 48 bytes (384 bits), but ES256 requires a " +
                    "field size of 32 bytes (256 bits) per [RFC 7518, Section 3.4 (validation)]" +
                    "(https://datatracker.ietf.org/doc/html/rfc7518#section-3.4)."
            assertEquals msg, expected.getMessage()
        }
    }

    /**
     * @since 0.11.5 as part of testing guards against JVM CVE-2022-21449
     */
    @Test
    void testParserWithMismatchedEllipticCurveKeyAndAlgorithm() {
        def pair = TestKeys.ES256.pair
        def jws = Jwts.builder().setSubject('foo').signWith(pair.private).compact()
        def parser = Jwts.parserBuilder().setSigningKey(TestKeys.ES384.pair.public).build()
        try {
            parser.parseClaimsJws(jws)
        } catch (UnsupportedJwtException expected) {
            String msg = 'The parsed JWT indicates it was signed with the \'ES256\' signature algorithm, but ' +
                    'the provided sun.security.ec.ECPublicKeyImpl key may not be used to verify ES256 signatures.  ' +
                    'Because the specified key reflects a specific and expected algorithm, and the JWT does not ' +
                    'reflect this algorithm, it is likely that the JWT was not expected and therefore should not ' +
                    'be trusted.  Another possibility is that the parser was provided the incorrect signature ' +
                    'verification key, but this cannot be assumed for security reasons.'
            assertEquals msg, expected.getMessage()
        }
    }

    /**
     * @since 0.11.5 as part of testing guards against JVM CVE-2022-21449
     */
    @Test(expected = io.jsonwebtoken.security.SignatureException)
    void testEcdsaInvalidSignatureValue() {
        def withoutSignature = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def invalidEncodedSignature = "_____wAAAAD__________7zm-q2nF56E87nKwvxjJVH_____AAAAAP__________vOb6racXnoTzucrC_GMlUQ"
        String jws = withoutSignature + '.' + invalidEncodedSignature
        def keypair = SignatureAlgorithms.ES256.keyPairBuilder().build()
        Jwts.parserBuilder().setSigningKey(keypair.public).build().parseClaimsJws(jws)
    }

    //Asserts correct/expected behavior discussed in https://github.com/jwtk/jjwt/issues/20
    @Test
    void testParseClaimsJwsWithUnsignedJwt() {

        //create random signing key for testing:
        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        String notSigned = Jwts.builder().setSubject("Foo").compact()

        try {
            Jwts.parserBuilder().enableUnsecuredJws().setSigningKey(key).build().parseClaimsJws(notSigned)
            fail('parseClaimsJws must fail for unsigned JWTs')
        } catch (UnsupportedJwtException expected) {
            assertEquals 'Unsigned Claims JWTs are not supported.', expected.message
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweMissingAlg() {
        def h = base64Url('{"enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.ecek.iv.' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            assertEquals DefaultJwtParser.MISSING_JWE_ALG_MSG, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweEmptyAlg() {
        def h = base64Url('{"alg":"","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.ecek.iv.' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            assertEquals DefaultJwtParser.MISSING_JWE_ALG_MSG, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWhitespaceAlg() {
        def h = base64Url('{"alg":"  ","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.ecek.iv.' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            assertEquals DefaultJwtParser.MISSING_JWE_ALG_MSG, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithNoneAlg() {
        def h = base64Url('{"alg":"none","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.ecek.iv.' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            assertEquals DefaultJwtParser.JWE_NONE_MSG, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithMissingAadTag() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.ecek.iv.' + c + '.'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = String.format(DefaultJwtParser.MISSING_JWE_DIGEST_MSG_FMT, 'dir')
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithEmptyAadTag() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        // our decoder skips invalid Base64Url characters, so this decodes to empty which is not allowed:
        def tag = '&'
        def compact = h + '.IA==.IA==.' + c + '.' + tag
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Compact JWE strings must always contain an AAD Authentication Tag.'
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithMissingRequiredBody() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def compact = h + '.ecek.iv..tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Compact JWE strings MUST always contain a payload (ciphertext).'
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithEmptyEncryptedKey() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        // our decoder skips invalid Base64Url characters, so this decodes to empty which is not allowed:
        def encodedKey = '&'
        def compact = h + '.' + encodedKey + '.iv.' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Compact JWE string represents an encrypted key, but the key is empty.'
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithMissingInitializationVector() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def compact = h + '.IA==..' + c + '.tag'
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            String expected = 'Compact JWE strings must always contain an Initialization Vector.'
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithMissingEncHeader() {
        def h = base64Url('{"alg":"dir"}')
        def c = base64Url('{"sub":"joe"}')
        def ekey = 'IA=='
        def iv = 'IA=='
        def tag = 'IA=='
        def compact = "$h.$ekey.$iv.$c.$tag" as String
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (MalformedJwtException e) {
            assertEquals DefaultJwtParser.MISSING_ENC_MSG, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithUnrecognizedEncValue() {
        def h = base64Url('{"alg":"dir","enc":"foo"}')
        def c = base64Url('{"sub":"joe"}')
        def ekey = 'IA=='
        def iv = 'IA=='
        def tag = 'IA=='
        def compact = "$h.$ekey.$iv.$c.$tag" as String
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (UnsupportedJwtException e) {
            String expected = "Unrecognized JWE 'enc' header value: foo"
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithUnrecognizedAlgValue() {
        def h = base64Url('{"alg":"bar","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def ekey = 'IA=='
        def iv = 'IA=='
        def tag = 'IA=='
        def compact = "$h.$ekey.$iv.$c.$tag" as String
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (UnsupportedJwtException e) {
            String expected = "Unrecognized JWE 'alg' header value: bar"
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJwsWithUnrecognizedAlgValue() {
        def h = base64Url('{"alg":"bar"}')
        def c = base64Url('{"sub":"joe"}')
        def sig = 'IA=='
        def compact = "$h.$c.$sig" as String
        try {
            Jwts.parserBuilder().build().parseClaimsJws(compact)
            fail()
        } catch (io.jsonwebtoken.security.SignatureException e) {
            String expected = "Unsupported signature algorithm 'bar'"
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithUnlocatableKey() {
        def h = base64Url('{"alg":"dir","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def ekey = 'IA=='
        def iv = 'IA=='
        def tag = 'IA=='
        def compact = "$h.$ekey.$iv.$c.$tag" as String
        try {
            Jwts.parserBuilder().build().parseClaimsJwe(compact)
            fail()
        } catch (UnsupportedJwtException e) {
            String expected = "Cannot decrypt JWE payload: unable to locate key for JWE with header: {alg=dir, enc=A128GCM}"
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJwsWithCustomSignatureAlgorithm() {
        def realAlg = SignatureAlgorithms.HS256 // any alg will do, we're going to wrap it
        def key = TestKeys.HS256
        def id = realAlg.getId() + 'X' // custom id
        def alg = new SecretKeySignatureAlgorithm() {
            @Override
            SecretKeyBuilder keyBuilder() {
                return realAlg.keyBuilder()
            }

            @Override
            int getKeyBitLength() {
                return realAlg.keyBitLength
            }

            @Override
            byte[] sign(SignatureRequest<SecretKey> request) throws SecurityException {
                return realAlg.sign(request)
            }

            @Override
            boolean verify(VerifySignatureRequest<SecretKey> request) throws SecurityException {
                return realAlg.verify(request)
            }

            @Override
            String getId() {
                return id
            }
        }

        def jws = Jwts.builder().setSubject("joe").signWith(key, alg).compact()

        assertEquals 'joe', Jwts.parserBuilder()
                .addSignatureAlgorithms([alg])
                .setSigningKey(key)
                .build()
                .parseClaimsJws(jws).body.getSubject()
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithCustomEncryptionAlgorithm() {
        def realAlg = EncryptionAlgorithms.A128GCM // any alg will do, we're going to wrap it
        def key = realAlg.keyBuilder().build()
        def enc = realAlg.getId() + 'X' // custom id
        def encAlg = new AeadAlgorithm() {
            @Override
            AeadResult encrypt(AeadRequest request) throws SecurityException {
                return realAlg.encrypt(request)
            }

            @Override
            Message decrypt(DecryptAeadRequest request) throws SecurityException {
                return realAlg.decrypt(request)
            }

            @Override
            String getId() {
                return enc
            }

            @Override
            SecretKeyBuilder keyBuilder() {
                return realAlg.keyBuilder()
            }

            @Override
            int getKeyBitLength() {
                return realAlg.getKeyBitLength()
            }
        }

        def jwe = Jwts.jweBuilder().setSubject("joe").encryptWith(encAlg, key).compact()

        assertEquals 'joe', Jwts.parserBuilder()
                .addEncryptionAlgorithms([encAlg])
                .decryptWith(key)
                .build()
                .parseClaimsJwe(jwe).body.getSubject()
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseJweWithBadKeyAlg() {
        def alg = 'foo'
        def h = base64Url('{"alg":"foo","enc":"A128GCM"}')
        def c = base64Url('{"sub":"joe"}')
        def ekey = 'IA=='
        def iv = 'IA=='
        def tag = 'IA=='
        def compact = "$h.$ekey.$iv.$c.$tag" as String

        def badKeyAlg = new KeyAlgorithm() {
            @Override
            KeyResult getEncryptionKey(KeyRequest request) throws SecurityException {
                return null
            }

            @Override
            SecretKey getDecryptionKey(DecryptionKeyRequest request) throws SecurityException {
                return null // bad implementation here - returns null, and that's not good
            }

            @Override
            String getId() {
                return alg
            }
        }

        try {
            Jwts.parserBuilder()
                    .setKeyLocator(new ConstantKeyLocator(TestKeys.HS256, TestKeys.A128GCM))
                    .addKeyAlgorithms([badKeyAlg]) // <-- add bad alg here
                    .build()
                    .parseClaimsJwe(compact)
            fail()
        } catch (IllegalStateException e) {
            String expected = "The 'foo' JWE key algorithm did not return a decryption key. " +
                    "Unable to perform 'A128GCM' decryption."
            assertEquals expected, e.getMessage()
        }
    }

    /**
     * @since JJWT_RELEASE_VERSION
     */
    @Test
    void testParseRequiredInt() {
        def key = TestKeys.HS256
        def jws = Jwts.builder().signWith(key).claim("foo", 42).compact()
        Jwts.parserBuilder().setSigningKey(key)
                .require("foo", 42L) //require a long, but jws contains int, should still work
                .build().parseClaimsJws(jws)
    }

    //Asserts correct/expected behavior discussed in https://github.com/jwtk/jjwt/issues/20
    @Test
    void testForgedTokenWithSwappedHeaderUsingNoneAlgorithm() {

        //create random signing key for testing:
        def alg = SignatureAlgorithms.HS256
        SecretKey key = alg.keyBuilder().build()

        //this is a 'real', valid JWT:
        String compact = Jwts.builder().setSubject("Joe").signWith(key, alg).compact()

        //Now strip off the signature so we can add it back in later on a forged token:
        int i = compact.lastIndexOf('.')
        String signature = compact.substring(i + 1)

        //now let's create a fake header and payload with whatever we want (without signing):
        String forged = Jwts.builder().setSubject("Not Joe").compact()

        //assert that our forged header has a 'NONE' algorithm:
        assertEquals 'none', Jwts.parserBuilder().enableUnsecuredJws().build().parseClaimsJwt(forged).getHeader().get('alg')

        //now let's forge it by appending the signature the server expects:
        forged += signature

        //now assert that, when the server tries to parse the forged token, parsing fails:
        try {
            Jwts.parserBuilder().enableUnsecuredJws().setSigningKey(key).build().parse(forged)
            fail("Parsing must fail for a forged token.")
        } catch (MalformedJwtException expected) {
            assertEquals 'The JWS header references signature algorithm \'none\' yet the compact JWS string contains a signature. This is not permitted per https://tools.ietf.org/html/rfc7518#section-3.6.', expected.message
        }
    }

    //Asserts correct/expected behavior discussed in https://github.com/jwtk/jjwt/issues/20 and https://github.com/jwtk/jjwt/issues/25
    @Test
    void testParseForgedRsaPublicKeyAsHmacTokenVerifiedWithTheRsaPrivateKey() {

        //Create a legitimate RSA public and private key pair:
        KeyPair kp = TestKeys.RS256.pair
        PublicKey publicKey = kp.getPublic()
        PrivateKey privateKey = kp.getPrivate()

        String header = base64Url(toJson(['alg': 'HS256']))
        String body = base64Url(toJson('foo'))
        String compact = header + '.' + body + '.'

        // Now for the forgery: simulate an attacker using the RSA public key to sign a token, but
        // using it as an HMAC signing key instead of RSA:
        Mac mac = Mac.getInstance('HmacSHA256')
        mac.init(new SecretKeySpec(publicKey.getEncoded(), 'HmacSHA256'))
        byte[] signatureBytes = mac.doFinal(compact.getBytes(Charset.forName('US-ASCII')))
        String encodedSignature = Encoders.BASE64URL.encode(signatureBytes)

        //Finally, the forged token is the header + body + forged signature:
        String forged = compact + encodedSignature

        // Assert that the server (that should always use the private key) does not recognized the forged token:
        try {
            Jwts.parserBuilder().setSigningKey(privateKey).build().parse(forged)
            fail("Forged token must not be successfully parsed.")
        } catch (UnsupportedJwtException expected) {
            assertTrue expected.getMessage().startsWith('The parsed JWT indicates it was signed with the')
        }
    }

    //Asserts correct behavior for https://github.com/jwtk/jjwt/issues/25
    @Test
    void testParseForgedRsaPublicKeyAsHmacTokenVerifiedWithTheRsaPublicKey() {

        //Create a legitimate RSA public and private key pair:
        KeyPair kp = TestKeys.RS256.pair
        PublicKey publicKey = kp.getPublic()
        //PrivateKey privateKey = kp.getPrivate();

        String header = base64Url(toJson(['alg': 'HS256']))
        String body = base64Url(toJson('foo'))
        String compact = header + '.' + body + '.'

        // Now for the forgery: simulate an attacker using the RSA public key to sign a token, but
        // using it as an HMAC signing key instead of RSA:
        Mac mac = Mac.getInstance('HmacSHA256')
        mac.init(new SecretKeySpec(publicKey.getEncoded(), 'HmacSHA256'))
        byte[] signatureBytes = mac.doFinal(compact.getBytes(Charset.forName('US-ASCII')))
        String encodedSignature = Encoders.BASE64URL.encode(signatureBytes)

        //Finally, the forged token is the header + body + forged signature:
        String forged = compact + encodedSignature

        // Assert that the parser does not recognized the forged token:
        try {
            Jwts.parserBuilder().setSigningKey(publicKey).build().parse(forged)
            fail("Forged token must not be successfully parsed.")
        } catch (UnsupportedJwtException expected) {
            assertTrue expected.getMessage().startsWith('The parsed JWT indicates it was signed with the')
        }
    }

    //Asserts correct behavior for https://github.com/jwtk/jjwt/issues/25
    @Test
    void testParseForgedEllipticCurvePublicKeyAsHmacToken() {

        //Create a legitimate EC public and private key pair:
        KeyPair kp = TestKeys.ES256.pair
        PublicKey publicKey = kp.getPublic()
        //PrivateKey privateKey = kp.getPrivate();

        String header = base64Url(toJson(['alg': 'HS256']))
        String body = base64Url(toJson('foo'))
        String compact = header + '.' + body + '.'

        // Now for the forgery: simulate an attacker using the Elliptic Curve public key to sign a token, but
        // using it as an HMAC signing key instead of Elliptic Curve:
        Mac mac = Mac.getInstance('HmacSHA256')
        mac.init(new SecretKeySpec(publicKey.getEncoded(), 'HmacSHA256'))
        byte[] signatureBytes = mac.doFinal(compact.getBytes(Charset.forName('US-ASCII')))
        String encodedSignature = Encoders.BASE64URL.encode(signatureBytes)

        //Finally, the forged token is the header + body + forged signature:
        String forged = compact + encodedSignature

        // Assert that the parser does not recognized the forged token:
        try {
            Jwts.parserBuilder().setSigningKey(publicKey).build().parse(forged)
            fail("Forged token must not be successfully parsed.")
        } catch (UnsupportedJwtException expected) {
            assertTrue expected.getMessage().startsWith('The parsed JWT indicates it was signed with the')
        }
    }

    @Test
    void testSecretKeyJwes() {

        def algs = KeyAlgorithms.values().findAll({ it ->
            it instanceof DirectKeyAlgorithm || it instanceof SecretKeyAlgorithm
        })// as Collection<KeyAlgorithm<SecretKey, SecretKey>>

        for (KeyAlgorithm alg : algs) {

            for (AeadAlgorithm enc : EncryptionAlgorithms.values()) {

                SecretKey key = alg instanceof SecretKeyAlgorithm ?
                        ((SecretKeyAlgorithm) alg).keyBuilder().build() :
                        enc.keyBuilder().build()

                // encrypt:
                String jwe = Jwts.jweBuilder()
                        .claim('foo', 'bar')
                        .encryptWith(enc, key, alg)
                        .compact()

                //decrypt:
                def jwt = Jwts.parserBuilder()
                        .decryptWith(key)
                        .build()
                        .parseClaimsJwe(jwe)
                assertEquals 'bar', jwt.getBody().get('foo')
            }
        }
    }

    @Test
    void testJweCompression() {

        def codecs = [CompressionCodecs.DEFLATE, CompressionCodecs.GZIP]

        for (CompressionCodec codec : codecs) {

            for (AeadAlgorithm enc : EncryptionAlgorithms.values()) {

                SecretKey key = enc.keyBuilder().build()

                // encrypt and compress:
                String jwe = Jwts.jweBuilder()
                        .claim('foo', 'bar')
                        .compressWith(codec)
                        .encryptWith(enc, key)
                        .compact()

                //decompress and decrypt:
                def jwt = Jwts.parserBuilder()
                        .decryptWith(key)
                        .build()
                        .parseClaimsJwe(jwe)
                assertEquals 'bar', jwt.getBody().get('foo')
            }
        }
    }

    @Test
    void testPasswordJwes() {

        def algs = KeyAlgorithms.values().findAll({ it ->
            it instanceof Pbes2HsAkwAlgorithm
        })// as Collection<KeyAlgorithm<SecretKey, SecretKey>>

        PasswordKey key = Keys.forPassword("12345678".toCharArray())

        for (KeyAlgorithm alg : algs) {

            for (AeadAlgorithm enc : EncryptionAlgorithms.values()) {

                // encrypt:
                String jwe = Jwts.jweBuilder()
                        .claim('foo', 'bar')
                        .encryptWith(enc, key, alg)
                        .compact()

                //decrypt:
                def jwt = Jwts.parserBuilder()
                        .decryptWith(key)
                        .build()
                        .parseClaimsJwe(jwe)
                assertEquals 'bar', jwt.getBody().get('foo')
            }
        }
    }

    @Test
    void testPasswordJweWithoutSpecifyingAlg() {

        PasswordKey key = Keys.forPassword("12345678".toCharArray())

        // encrypt:
        String jwe = Jwts.jweBuilder()
                .claim('foo', 'bar')
                .encryptWith(EncryptionAlgorithms.A256GCM, key) // should auto choose KeyAlg PBES2_HS512_A256KW
                .compact()

        //decrypt:
        def jwt = Jwts.parserBuilder()
                .decryptWith(key)
                .build()
                .parseClaimsJwe(jwe)
        assertEquals 'bar', jwt.getBody().get('foo')
        assertEquals KeyAlgorithms.PBES2_HS512_A256KW, KeyAlgorithms.forId(jwt.getHeader().getAlgorithm())
    }

    @Test
    void testRsaJwes() {

        def pairs = [TestKeys.RS256.pair, TestKeys.RS384.pair, TestKeys.RS512.pair]

        def algs = KeyAlgorithms.values().findAll({ it ->
            it instanceof RsaKeyAlgorithm
        })// as Collection<KeyAlgorithm<SecretKey, SecretKey>>

        for (KeyPair pair : pairs) {

            def pubKey = pair.getPublic()
            def privKey = pair.getPrivate()

            for (KeyAlgorithm alg : algs) {

                for (AeadAlgorithm enc : EncryptionAlgorithms.values()) {

                    // encrypt:
                    String jwe = Jwts.jweBuilder()
                            .claim('foo', 'bar')
                            .encryptWith(enc, pubKey, alg)
                            .compact()

                    //decrypt:
                    def jwt = Jwts.parserBuilder()
                            .decryptWith(privKey)
                            .build()
                            .parseClaimsJwe(jwe)
                    assertEquals 'bar', jwt.getBody().get('foo')
                }
            }
        }
    }

    @Test
    void testEcJwes() {

        def pairs = [TestKeys.ES256.pair, TestKeys.ES384.pair, TestKeys.ES512.pair]

        def algs = KeyAlgorithms.values().findAll({ it ->
            it instanceof EcKeyAlgorithm
        })

        for (KeyPair pair : pairs) {

            def pubKey = pair.getPublic()
            def privKey = pair.getPrivate()

            for (KeyAlgorithm alg : algs) {

                for (AeadAlgorithm enc : EncryptionAlgorithms.values()) {

                    // encrypt:
                    String jwe = Jwts.jweBuilder()
                            .claim('foo', 'bar')
                            .encryptWith(enc, pubKey, alg)
                            .compact()

                    //decrypt:
                    def jwt = Jwts.parserBuilder()
                            .decryptWith(privKey)
                            .build()
                            .parseClaimsJwe(jwe)
                    assertEquals 'bar', jwt.getBody().get('foo')
                }
            }
        }
    }

    static void testRsa(AsymmetricKeySignatureAlgorithm alg, boolean verifyWithPrivateKey = false) {

        KeyPair kp = TestKeys.forAlgorithm(alg).pair
        PublicKey publicKey = kp.getPublic()
        PrivateKey privateKey = kp.getPrivate()

        def claims = new DefaultClaims([iss: 'joe', exp: later(), 'https://example.com/is_root': true])

        String jwt = Jwts.builder().setClaims(claims).signWith(privateKey, alg).compact()

        def key = publicKey
        if (verifyWithPrivateKey) {
            key = privateKey
        }

        def token = Jwts.parserBuilder().setSigningKey(key).build().parse(jwt)

        assertEquals([alg: alg.getId()], token.header)
        assertEquals(claims, token.body)
    }

    static void testHmac(SecretKeySignatureAlgorithm alg) {

        //create random signing key for testing:
        SecretKey key = alg.keyBuilder().build()

        def claims = new DefaultClaims([iss: 'joe', exp: later(), 'https://example.com/is_root': true])

        String jwt = Jwts.builder().setClaims(claims).signWith(key, alg).compact()

        def token = Jwts.parserBuilder().setSigningKey(key).build().parse(jwt)

        assertEquals([alg: alg.getId()], token.header)
        assertEquals(claims, token.body)
    }

    static void testEC(AsymmetricKeySignatureAlgorithm alg, boolean verifyWithPrivateKey = false) {

        KeyPair pair = TestKeys.forAlgorithm(alg).pair
        PublicKey publicKey = pair.getPublic()
        PrivateKey privateKey = pair.getPrivate()

        def claims = new DefaultClaims([iss: 'joe', exp: later(), 'https://example.com/is_root': true])

        String jwt = Jwts.builder().setClaims(claims).signWith(privateKey, alg).compact()

        def key = publicKey
        if (verifyWithPrivateKey) {
            key = privateKey
        }

        def token = Jwts.parserBuilder().setSigningKey(key).build().parse(jwt)

        assertEquals([alg: alg.getId()], token.header)
        assertEquals(claims, token.body)
    }
}

