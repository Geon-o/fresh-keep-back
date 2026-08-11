package com.example.fresh_keep.domain.ingredient.repository;

import com.example.fresh_keep.domain.ingredient.entity.IngredientHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientHistoryRepository extends JpaRepository<IngredientHistory, Long> {
    // 최근 200건만 보여준다 (무한정 쌓이는 이력을 한 번에 다 내려주지 않기 위한 최소한의 안전장치)
    List<IngredientHistory> findTop200ByFridgeIdOrderByCreatedAtDesc(Long fridgeId);
}
