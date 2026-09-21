package org.kariya;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class JasyptEncryptorTest {
    @Autowired
    StringEncryptor encryptor;

    @Test
    void encryptAndDecrypt() {
        String plaintext = "Gy54670.";
        String encrypted = encryptor.encrypt(plaintext);
        System.out.println("ENC(" + encrypted + ")");
        assertEquals(plaintext, encryptor.decrypt(encrypted));
    }
}
