package com.example.fresh_keep.domain.guide.repository;

import com.example.fresh_keep.domain.guide.entity.StorageGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageGuideRepository extends JpaRepository<StorageGuide, Long> {
    Optional<StorageGuide> findByName(String name);
    List<StorageGuide> findByNameContaining(String name);
}
