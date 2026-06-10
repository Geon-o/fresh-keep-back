package com.example.fresh_keep.global.config;

import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsById(1L)) {
            log.info("Seeding developer test user with ID 1...");
            try {
                User devUser = User.builder()
                        .id(1L)
                        .email("dev@freshkeep.com")
                        .name("개발자 테스트 유저")
                        .provider("developer")
                        .providerId("mock_dev_id")
                        .build();
                userRepository.save(devUser);
                log.info("Developer test user seeded successfully.");
            } catch (Exception e) {
                log.error("Failed to seed developer test user", e);
            }
        } else {
            log.info("Developer test user already exists.");
        }
    }
}
