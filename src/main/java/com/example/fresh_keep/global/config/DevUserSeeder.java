package com.example.fresh_keep.global.config;

import com.example.fresh_keep.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!userRepository.existsById(1L)) {
            log.info("Seeding developer test user with ID 1 using native query...");
            try {
                entityManager.createNativeQuery(
                        "INSERT INTO users (id, email, name, provider, provider_id, created_at, updated_at) " +
                        "VALUES (:id, :email, :name, :provider, :providerId, :createdAt, :updatedAt)"
                )
                .setParameter("id", 1L)
                .setParameter("email", "dev@freshkeep.com")
                .setParameter("name", "개발자 테스트 유저")
                .setParameter("provider", "developer")
                .setParameter("providerId", "mock_dev_id")
                .setParameter("createdAt", LocalDateTime.now())
                .setParameter("updatedAt", LocalDateTime.now())
                .executeUpdate();

                log.info("Developer test user seeded successfully.");
            } catch (Exception e) {
                log.error("Failed to seed developer test user", e);
            }
        } else {
            log.info("Developer test user already exists.");
        }
    }
}
