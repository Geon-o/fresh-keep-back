package com.example.fresh_keep.domain.fridge.repository;

import com.example.fresh_keep.domain.fridge.entity.Fridge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FridgeRepository extends JpaRepository<Fridge, Long> {
    Optional<Fridge> findByUuid(String uuid);
}
