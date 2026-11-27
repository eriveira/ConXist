package com.conxist.platform.config;

import java.time.Clock;
import java.util.Base64;
import com.conxist.platform.SecretBox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Seals secrets and customer data at rest for every module (ADR 0004). */
    @Bean
    SecretBox secretBox(SecretsProperties secrets) {
        return new SecretBox(Base64.getDecoder().decode(secrets.encryptionKey()));
    }
}
