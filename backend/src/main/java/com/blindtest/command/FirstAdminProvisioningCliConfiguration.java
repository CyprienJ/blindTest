package com.blindtest.command;

import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.FirstAdminInvitationService;
import com.blindtest.service.FirstAdminProvisioningService;
import com.blindtest.service.InvitationIssuer;
import java.time.Clock;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal non-web Spring context used only by the first-administrator operator commands. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
@EnableConfigurationProperties(ApplicationProperties.class)
@Import({
    FirstAdminProvisioningCommand.class,
    FirstAdminProvisioningService.class,
    FirstAdminInvitationCommand.class,
    FirstAdminInvitationService.class,
    InvitationIssuer.class
})
@Profile("first-admin-cli")
public class FirstAdminProvisioningCliConfiguration {

    /** UTC wall clock used for invitation expiry. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
