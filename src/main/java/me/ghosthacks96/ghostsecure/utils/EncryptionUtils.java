package me.ghosthacks96.ghostsecure.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int KEY_LENGTH = 256;

    /**
     * Creates a 256-bit AES key from user-specific information
     * @param username The username
     * @param systemInfo Additional system-specific info (like hostname, MAC address)
     * @param salt A random salt (should be stored securely)
     * @return SecretKey for AES encryption
     */
    public static SecretKey deriveKeyFromUserInfo(String username, String systemInfo, byte[] salt) {
        try {
            // Combine user info into a single string
            String userInfo = username + ":" + systemInfo;

            // Use PBKDF2 to derive a strong key
            PBEKeySpec spec = new PBEKeySpec(
                    userInfo.toCharArray(),
                    salt,
                    PBKDF2_ITERATIONS,
                    KEY_LENGTH
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();

            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            me.ghosthacks96.ghostsecure.Main.logger.logError("Failed to derive key from user info", e);
            return null;
        }
    }

    /**
     * Generate a random salt for key derivation
     * @return byte array containing random salt
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Get system-specific information for key derivation
     * @return String containing system info
     */
    public static String getSystemInfo() {
        try {
            // Combine multiple system properties for uniqueness
            String hostname = System.getenv("COMPUTERNAME"); // Windows
            if (hostname == null) {
                hostname = System.getenv("HOSTNAME"); // Linux/Mac
            }
            if (hostname == null) {
                hostname = "unknown";
            }

            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String userHome = System.getProperty("user.home");

            // Hash the combined info to create a consistent identifier
            String combined = hostname + ":" + osName + ":" + osVersion + ":" + userHome;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            me.ghosthacks96.ghostsecure.Main.logger.logError("Failed to get system info", e);
            return null;
        }
    }

    /**
     * Encrypt data using AES-GCM
     * @param data The data to encrypt
     * @param key The encryption key
     * @return Encrypted data with IV prepended
     */
    public static String encrypt(String data, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(encryptedWithIv);
        } catch (Exception e) {
            me.ghosthacks96.ghostsecure.Main.logger.logError("Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypt data using AES-GCM
     * @param encryptedData The encrypted data (with IV prepended)
     * @param key The decryption key
     * @return Decrypted data
     */
    public static String decrypt(String encryptedData, SecretKey key) {
        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);

            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[decodedData.length - GCM_IV_LENGTH];

            System.arraycopy(decodedData, 0, iv, 0, iv.length);
            System.arraycopy(decodedData, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decryptedData = cipher.doFinal(encrypted);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            me.ghosthacks96.ghostsecure.Main.logger.logError("Decryption failed", e);
            return null;
        }
    }
}