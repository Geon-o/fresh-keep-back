package com.example.fresh_keep.domain.memo.repository;

import com.example.fresh_keep.domain.memo.entity.FridgeMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface FridgeMemoRepository extends JpaRepository<FridgeMemo, Long> {
    List<FridgeMemo> findByFridgeIdOrderByCreatedAtDesc(Long fridgeId);

    // 안읽음 배지 판정용: 내가 마지막으로 본 시각 이후 "남이 남긴" 메모가 있는지.
    boolean existsByFridgeIdAndAuthorUserIdNotAndCreatedAtAfter(Long fridgeId, Long authorUserId, LocalDateTime after);

    // getFridges 안읽음 배지 배치 판정용: 냉장고마다 exists 쿼리를 반복하던 N+1을 없애기 위해,
    // 여러 냉장고의 "남(userId 아님)이 남긴 마지막 메모 시각"을 한 번에 집계한다.
    // 멤버별 기준시각(lastMemoViewedAt)이 달라 비교는 서비스 메모리에서 수행한다.
    @Query("SELECT m.fridgeId AS fridgeId, MAX(m.createdAt) AS lastCreatedAt " +
           "FROM FridgeMemo m WHERE m.fridgeId IN :fridgeIds AND m.authorUserId <> :userId " +
           "GROUP BY m.fridgeId")
    List<LastOtherMemo> findLastOtherMemoCreatedAt(Collection<Long> fridgeIds, Long userId);

    interface LastOtherMemo {
        Long getFridgeId();
        LocalDateTime getLastCreatedAt();
    }
}
