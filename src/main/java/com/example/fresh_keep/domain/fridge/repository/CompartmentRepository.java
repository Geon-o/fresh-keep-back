package com.example.fresh_keep.domain.fridge.repository;

import com.example.fresh_keep.domain.fridge.entity.Compartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompartmentRepository extends JpaRepository<Compartment, Long> {
}
