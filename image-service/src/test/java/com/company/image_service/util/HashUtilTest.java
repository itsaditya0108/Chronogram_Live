package com.company.image_service.util;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HashUtilTest {

    @Test
    void testSha256Hex_ByteArray() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertEquals(expected, HashUtil.sha256Hex(data));
    }

    @Test
    void testSha256Hex_InputStream() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(data);
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertEquals(expected, HashUtil.sha256Hex(is));
    }

    @Test
    void testSha256Hex_EmptyStream() {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertEquals(expected, HashUtil.sha256Hex(is));
    }
}
