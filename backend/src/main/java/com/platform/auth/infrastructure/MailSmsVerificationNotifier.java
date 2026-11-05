package com.platform.auth.infrastructure;

import com.platform.auth.application.VerificationNotifier;
import com.platform.notifications.mail.MailService;
import com.platform.notifications.sms.SmsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real adapter for {@link VerificationNotifier}: email over SMTP (notetaboite.com account) and
 * SMS over Djosend (mon-point account). Active only when {@code notifications.enabled=true};
 * {@link LoggingVerificationNotifier} covers every environment where it isn't.
 */
@Component
@ConditionalOnProperty(prefix = "notifications", name = "enabled", havingValue = "true")
public class MailSmsVerificationNotifier implements VerificationNotifier {

    private final MailService mailService;
    private final SmsService smsService;

    public MailSmsVerificationNotifier(MailService mailService, SmsService smsService) {
        this.mailService = mailService;
        this.smsService = smsService;
    }

    @Override
    public void sendEmailVerification(String email, String token) {
        mailService.send(email, "Vérifiez votre adresse email",
                "Voici votre code de vérification : " + token
                        + "\n\nCe code est valable 24 heures. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
    }

    @Override
    public void sendPhoneOtp(String phone, String code) {
        smsService.send(phone, "Votre code de vérification : " + code + " (valable 10 min).");
    }

    @Override
    public void sendPasswordResetEmail(String email, String token) {
        mailService.send(email, "Réinitialisation de votre mot de passe",
                "Voici votre code de réinitialisation : " + token
                        + "\n\nCe code est valable 1 heure. Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
    }
}
