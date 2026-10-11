package com.arjun.gander

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class ZipEncryptionTest {

    @Test
    fun asciiPasswordProducesSingleByteArray() {
        val bytes = ZipEncryption.passwordBytes("secret123", Locale.US)
        assertThat(bytes).hasSize(1)
        assertThat(String(bytes.single(), Charsets.UTF_8)).isEqualTo("secret123")
    }

    @Test
    fun nonAsciiPasswordProducesUtf8First() {
        val bytes = ZipEncryption.passwordBytes("пароль", Locale.US)
        assertThat(bytes).isNotEmpty()
        assertThat(String(bytes.first(), Charsets.UTF_8)).isEqualTo("пароль")
    }

    @Test
    fun passwordBytesDeduplicatesIdenticalEncodings() {
        val bytes = ZipEncryption.passwordBytes("hello", Locale.US)
        assertThat(bytes).hasSize(1)
    }
}
