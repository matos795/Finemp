package com.finemp.identity.delivery;

import com.finemp.identity.AccountActionPurpose;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

public class SmtpAccountActionMailSender implements AccountActionMailSender {

    private final JavaMailSender sender;
    private final String from;

    public SmtpAccountActionMailSender(JavaMailSender sender, String from) {
        if (from == null || from.isBlank() || from.contains("\r") || from.contains("\n")) {
            throw new IllegalArgumentException("An account mail sender address is required.");
        }
        this.sender = sender;
        this.from = from;
        if (sender instanceof JavaMailSenderImpl implementation) {
            var properties = implementation.getJavaMailProperties();
            String protocol = implementation.getProtocol();
            properties.putIfAbsent("mail." + protocol + ".connectiontimeout", "5000");
            properties.putIfAbsent("mail." + protocol + ".timeout", "5000");
            properties.putIfAbsent("mail." + protocol + ".writetimeout", "5000");
            if ("smtp".equalsIgnoreCase(protocol)) {
                properties.putIfAbsent("mail.smtp.starttls.enable", "true");
                properties.putIfAbsent("mail.smtp.starttls.required", "true");
            }
            properties.setProperty("mail.debug", "false");
        }
    }

    @Override
    public void send(AccountActionMail message) {
        if (message.recipient().contains("\r") || message.recipient().contains("\n")) {
            throw new IllegalArgumentException("Invalid account mail recipient.");
        }
        boolean invitation = message.purpose() == AccountActionPurpose.INVITATION;
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.recipient());
        mail.setSubject(invitation ? "FINEMP — convite de acesso" : "FINEMP — redefinição de senha");
        mail.setText((invitation ? "Defina sua senha para ativar o acesso ao FINEMP."
                : "Use o link para definir uma nova senha no FINEMP.")
                + "\n\n" + message.actionLink()
                + "\n\nO link é de uso único e expira em " + message.expiresAt()
                + ".\nSe você não solicitou esta ação, ignore esta mensagem.");
        sender.send(mail);
    }
}
