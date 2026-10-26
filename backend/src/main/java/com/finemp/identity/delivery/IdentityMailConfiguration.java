package com.finemp.identity.delivery;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@ConditionalOnProperty(prefix = "finemp.identity.mail", name = "enabled", havingValue = "true")
public class IdentityMailConfiguration {

    @Bean
    AccountActionMailSender accountActionMailSender(
            JavaMailSender sender,
            @Value("${finemp.identity.mail.from}") String from) {
        return new SmtpAccountActionMailSender(sender, from);
    }

    @Bean
    AccountActionMailDelivery accountActionMailDelivery(
            AccountActionMailSender sender,
            @Value("${finemp.identity.mail.frontend-base-uri}") URI frontendBaseUri,
            @Value("${finemp.identity.mail.require-https:true}") boolean requireHttps) {
        return new AccountActionMailDelivery(sender, frontendBaseUri, requireHttps);
    }
}
