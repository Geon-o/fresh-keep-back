package com.example.fresh_keep.domain.fridge.repository;

import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {
    List<FridgeMember> findByUserId(Long userId);
    boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);
}
