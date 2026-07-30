package com.blindtest.command;

import com.blindtest.model.entity.UserEntity;
import com.blindtest.repository.UserRepository;
import com.blindtest.service.FirstAdminProvisioningService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Minimal non-web Spring context used only by the provisioning command. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
@Import({FirstAdminProvisioningCommand.class, FirstAdminProvisioningService.class})
@Profile("first-admin-cli")
public class FirstAdminProvisioningCliConfiguration {
}
