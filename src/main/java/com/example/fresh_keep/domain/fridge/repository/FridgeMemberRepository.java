package com.example.fresh_keep.domain.fridge.repository;

import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface FridgeMemberRepository extends JpaRepository<FridgeMember, Long> {
    List<FridgeMember> findByUserId(Long userId);
    List<FridgeMember> findByFridgeId(Long fridgeId);
    // 냉장고 여러 개의 멤버를 한 번에 조회할 때 사용 (getFridges에서 냉장고 개수만큼 쿼리가 나가는 N+1을 피하기 위함)
    List<FridgeMember> findByFridgeIdIn(Collection<Long> fridgeIds);
    boolean existsByFridgeIdAndUserId(Long fridgeId, Long userId);
    Optional<FridgeMember> findByFridgeIdAndUserId(Long fridgeId, Long userId);
}
