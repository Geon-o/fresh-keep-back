package com.example.fresh_keep.domain.memo.repository;

import com.example.fresh_keep.domain.memo.entity.FridgeMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FridgeMemoRepository extends JpaRepository<FridgeMemo, Long> {
    List<FridgeMemo> findByFridgeIdOrderByCreatedAtDesc(Long fridgeId);

    // 안읽음 배지 판정용: 내가 마지막으로 본 시각 이후 "남이 남긴" 메모가 있는지.
    boolean existsByFridgeIdAndAuthorUserIdNotAndCreatedAtAfter(Long fridgeId, Long authorUserId, LocalDateTime after);
}
