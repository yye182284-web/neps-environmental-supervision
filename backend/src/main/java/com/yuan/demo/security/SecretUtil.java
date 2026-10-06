package com.yuan.demo.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * 登录密码传输加解密工具。浏览器使用公开公钥加密，服务端使用仅在内存中的私钥解密。
 */
@Component
public class SecretUtil {
    private final KeyPair keyPair;

    public SecretUtil() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("初始化登录加密密钥失败", e);
        }
    }

    public String publicKeyPem() {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
    }

    public String decrypt(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            throw new IllegalArgumentException("登录密码不能为空");
        }
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            String password = new String(plain, StandardCharsets.UTF_8);
            if (password.isBlank() || password.length() > 72) {
                throw new IllegalArgumentException("登录密码格式不正确");
            }
            return password;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("密码加密数据无效，请刷新页面后重试");
        }
    }
}
