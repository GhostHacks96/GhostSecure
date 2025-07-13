package me.ghosthacks96.ghostsecure.utils.auth;

import me.ghosthacks96.ghostsecure.Main;
import me.ghosthacks96.ghostsecure.utils.api_handlers.EmailService;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Utility class for two-factor authentication
 */
public class TwoFactorAuthUtil {
    
    private static final Random RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    
    /**
     * Generate a random verification code
     * @return a 6-digit verification code
     */
    public static String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(RANDOM.nextInt(10)); // Append a random digit (0-9)
        }
        return code.toString();
    }
    
    /**
     * Send a verification code to the user's email
     * @param email The user's email address
     * @return The generated verification code, or null if sending failed
     */
    public static String sendVerificationCode(String email) {
        try {
            // Generate a verification code
            String code = generateVerificationCode();

            // Create email service and send the code
            EmailService emailService = new EmailService();
            boolean sent = emailService.sendVerificationCode(email, code);
            
            if (sent) {
                Main.logger.logInfo("Verification code sent to " + email);
                return code;
            } else {
                Main.logger.logError("Failed to send verification code to " + email);
                return null;
            }
        } catch (Exception e) {
            Main.logger.logError("Error sending verification code: " + e.getMessage(), e);
            return null;
        }
    }
}