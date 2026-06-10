package com.example.fresh_keep.domain.fridge.service;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.FridgeResponse;
import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.Fridge;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.fridge.enums.StorageType;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final CompartmentRepository compartmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public FridgeResponse createFridge(CreateFridgeRequest request, Long userId) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. 냉장고 생성 및 저장
        Fridge fridge = Fridge.builder()
                .name(request.getName())
                .type(request.getType())
                .build();
        fridgeRepository.save(fridge);

        // 3. 냉장고 멤버(소유주) 등록
        FridgeMember fridgeMember = FridgeMember.builder()
                .user(user)
                .fridge(fridge)
                .role(MemberRole.OWNER)
                .build();
        fridgeMemberRepository.save(fridgeMember);

        // 4. 냉장고 타입별 기본 구획(Compartments) 생성
        createDefaultCompartments(fridge);

        return FridgeResponse.builder()
                .id(fridge.getId())
                .name(fridge.getName())
                .type(fridge.getType())
                .role(MemberRole.OWNER)
                .build();
    }

    private void createDefaultCompartments(Fridge fridge) {
        List<Compartment> compartments = new ArrayList<>();
        if (fridge.getType() == FridgeType.FOUR_DOOR) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("상단 좌측 냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("상단 우측 냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("하단 좌측 냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(3)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("하단 우측 냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(4)
                    .build());
        } else if (fridge.getType() == FridgeType.SIDE_BY_SIDE) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
        } else if (fridge.getType() == FridgeType.TWO_DOOR) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
        }

        compartmentRepository.saveAll(compartments);
    }
}
