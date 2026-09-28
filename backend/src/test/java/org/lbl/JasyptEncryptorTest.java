package org.lbl;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.jasypt.salt.RandomSaltGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class JasyptEncryptorTest {
    @Autowired
    StringEncryptor encryptor;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void encryptAndDecrypt() {
        // 1. 实例化加密器
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        // 2. 设置与 application.yml 中完全一致的参数
        encryptor.setPassword("kariya"); // 你的加密盐
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setKeyObtentionIterations(100000);
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        encryptor.setStringOutputType("base64");
        // 3. 将此处替换为你真实的 MySQL 密码 (例如: "MyRealP@ssw0rd!2026")
        String plaintext = "";
        // 4. 生成密文
        String encrypted = encryptor.encrypt(plaintext);
        // 打印时自动加上 ENC() 包裹，方便直接复制到 yml
        System.out.println("生成的密文: ENC(" + encrypted + ")");
        // 5. 验证解密 (确保生成的密文能和 application.yml 配合正确解密)
        assertEquals(plaintext, encryptor.decrypt(encrypted));
        System.out.println("验证通过！解密结果与原文一致。");
    }

    @Test
    void passwordGenerator() {
        // 2. 输入你要设置的超管新密码，例如 "admin123"
        String rawPassword = "123456";
        // 3. 生成加密后的字符串
        String encodedPassword = passwordEncoder.encode(rawPassword);
        // 4. 打印结果
        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后密码: " + encodedPassword);
    }
}
