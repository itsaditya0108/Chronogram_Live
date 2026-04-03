package live.chronogram.auth.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import live.chronogram.auth.exception.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

/**
 * Service for sending system emails like OTPs and security alerts.
 * Uses JavaMailSender with a branded HTML template matching the app's theme.
 */
@Service
public class EmailService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a 6-digit OTP to the specified email address.
     * Annotated with @Async to run in a background thread, preventing
     * database connections from being held open during slow SMTP calls.
     * 
     * @param toEmail The recipient's email.
     * @param otp     The 6-digit code.
     */
    @Async("emailTaskExecutor")
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(new InternetAddress("chronogram.live@gmail.com", "Chronogram Live"));
            helper.setTo(toEmail);
            helper.setSubject("Your Verification Code - Chronogram Live");
            helper.setText(buildOtpTemplate(otp), true);

            mailSender.send(message);
            logger.info("OTP email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            // Enhanced logging for VPS SMTP diagnostics
            logger.error("!!! SMTP FAILURE !!! Target: {}, Error: {}, Cause: {}", 
                    toEmail, e.getMessage(), (e.getCause() != null ? e.getCause().getMessage() : "N/A"));
            
            // Detailed stack trace for deep debugging on VPS
            logger.error("Full SMTP Stack Trace:", e);

            throw new AuthException(HttpStatus.INTERNAL_SERVER_ERROR, "FAILED_TO_SEND_EMAIL_OTP");
        }
    }

    /**
     * Branded HTML Template using the app's signature colors.
     * Background: Dark (#121212)
     * Primary Accent: Orange (#FF9933)
     */
    private String buildOtpTemplate(String otp) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background-color: #f4f4f4; }
                        .container { max-width: 500px; margin: 40px auto; background: #121212; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.3); }
                        .header { background: #1a1a1a; padding: 30px; text-align: center; border-bottom: 2px solid #FF9933; }
                        .header h1 { color: #FF9933; margin: 0; font-size: 24px; letter-spacing: 1px; }
                        .content { padding: 40px 30px; color: #ffffff; text-align: center; line-height: 1.6; }
                        .otp-box { background: #1a1a1a; border: 1px solid #333; border-radius: 8px; padding: 20px; margin: 25px 0; display: inline-block; min-width: 200px; }
                        .otp-code { font-size: 36px; font-weight: bold; color: #FF9933; letter-spacing: 6px; margin: 0; }
                        .footer { background: #0a0a0a; padding: 20px; text-align: center; color: #666; font-size: 12px; }
                        .accent { color: #FF9933; font-weight: bold; }
                        p { margin-bottom: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Chronogram Live</h1>
                        </div>
                        <div class="content">
                            <p style="font-size: 18px;">Verification Required</p>
                            <p>Hello,</p>
                            <p>To finalize your action, please use the secure One-Time Password (OTP) below:</p>

                            <div class="otp-box">
                                <h2 class="otp-code">%s</h2>
                            </div>

                            <p>This code is valid for <span class="accent">5 minutes</span>.</p>
                            <p style="color: #999; font-size: 14px;">If you didn't request this code, you can safely ignore this email.</p>
                        </div>
                        <div class="footer">
                            &copy; 2026 Chronogram Live &bull; Secure Authentication System<br>
                            This is an automated message, please do not reply.
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(otp);
    }
}
