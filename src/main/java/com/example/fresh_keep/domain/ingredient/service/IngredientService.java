package com.example.fresh_keep.domain.ingredient.service;

import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.ingredient.dto.AddIngredientRequest;
import com.example.fresh_keep.domain.ingredient.dto.IngredientDetailResponse;
import com.example.fresh_keep.domain.ingredient.dto.IngredientHistoryResponse;
import com.example.fresh_keep.domain.ingredient.dto.UpdateIngredientRequest;
import com.example.fresh_keep.domain.ingredient.entity.HistoryActionType;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.entity.IngredientHistory;
import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import com.example.fresh_keep.domain.ingredient.repository.IngredientHistoryRepository;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.notification.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientHistoryRepository ingredientHistoryRepository;
    private final CompartmentRepository compartmentRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 프론트엔드 CompartmentDetail.tsx의 CATEGORIES와 동일한 매핑 (이력 요약을 한글로 보여주기 위함)
    private static final Map<String, String> CATEGORY_LABELS = Map.ofEntries(
            Map.entry("vegetable", "채소"),
            Map.entry("meat", "육류"),
            Map.entry("seafood", "해물"),
            Map.entry("dairy", "유제품"),
            Map.entry("fruit", "과일"),
            Map.entry("frozen", "냉동식품"),
            Map.entry("bakery", "빵류"),
            Map.entry("drink", "음료"),
            Map.entry("sauce", "소스/조미료"),
            Map.entry("etc", "기타")
    );

    @Transactional
    public IngredientDetailResponse addIngredient(AddIngredientRequest request, Long userId) {
        // 1. 구획 조회 (없으면 "위치 미정" 등록)
        Compartment compartment = null;
        Long fridgeId = request.getFridgeId();
        if (request.getCompartmentId() != null) {
            compartment = compartmentRepository.findById(request.getCompartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보관 구획입니다."));
            if (!compartment.getFridge().getId().equals(fridgeId)) {
                throw new IllegalArgumentException("지정한 구획이 해당 냉장고 소속이 아닙니다.");
            }
        }

        // 2. 권한 검증 (해당 냉장고의 멤버인지)
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 식재료를 추가할 권한이 없습니다.");
        }

        // 3. 식재료 생성 및 저장
        Ingredient ingredient = Ingredient.builder()
                .compartment(compartment)
                .fridgeId(fridgeId)
                .name(request.getName())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .expirationDate(request.getExpirationDate())
                .expirationType(request.getExpirationType())
                .memo(request.getMemo())
                .createdBy(userId)
                .build();
        ingredientRepository.save(ingredient);

        // 4. 이력 기록
        saveHistory(fridgeId, ingredient.getName(), HistoryActionType.CREATED, userId,
                withEulReul(ingredient.getName()) + " 등록했어요.");

        // 5. 캐시 무효화
        evictLayoutCache(fridgeId);

        return mapToResponse(ingredient);
    }

    @Transactional
    public IngredientDetailResponse updateIngredient(Long ingredientId, UpdateIngredientRequest request, Long userId) {
        // 1. 식재료 조회
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 식재료입니다."));

        // 2. 권한 검증
        Long fridgeId = ingredient.getFridgeId();
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 식재료를 수정할 권한이 없습니다.");
        }

        // 3. 이력 요약을 위해 변경 전 값을 먼저 확보
        String oldName = ingredient.getName();
        Double oldQuantity = ingredient.getQuantity();
        String oldUnit = ingredient.getUnit();
        LocalDate oldExpirationDate = ingredient.getExpirationDate();
        String oldMemo = ingredient.getMemo();
        Compartment oldCompartment = ingredient.getCompartment();

        // 4. 식재료 필드 업데이트
        String newName = request.getName() != null ? request.getName() : ingredient.getName();
        Double newQuantity = request.getQuantity() != null ? request.getQuantity() : ingredient.getQuantity();
        String newUnit = request.getUnit() != null ? request.getUnit() : ingredient.getUnit();
        LocalDate newExpirationDate = request.getExpirationDate() != null ? request.getExpirationDate() : ingredient.getExpirationDate();
        ExpirationType newExpirationType = request.getExpirationType() != null ? request.getExpirationType() : ingredient.getExpirationType();
        String newMemo = request.getMemo() != null ? request.getMemo() : ingredient.getMemo();

        ingredient.update(newName, newQuantity, newUnit, newExpirationDate, newExpirationType, newMemo, userId);

        // 구획 이동 처리
        if (request.getCompartmentId() != null) {
            Compartment newCompartment = compartmentRepository.findById(request.getCompartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보관 구획입니다."));

            // 이동할 구획이 동일한 냉장고 내의 구획인지 검증
            if (!newCompartment.getFridge().getId().equals(fridgeId)) {
                throw new IllegalArgumentException("동일한 냉장고 내의 구획으로만 이동할 수 있습니다.");
            }

            ingredient.updateCompartment(newCompartment);
        }

        ingredientRepository.save(ingredient);

        // 5. 이력 기록 (실제로 바뀐 값이 있을 때만 기록 — 변경 없이 저장만 누른 경우 빈 이력 row를 남기지 않는다)
        String summary = buildUpdateSummary(
                oldName, newName,
                oldQuantity, oldUnit, newQuantity, newUnit,
                oldExpirationDate, newExpirationDate,
                oldMemo, newMemo,
                oldCompartment, ingredient.getCompartment()
        );
        if (summary != null) {
            saveHistory(fridgeId, newName, HistoryActionType.UPDATED, userId, summary);
        }

        // 6. 캐시 무효화
        evictLayoutCache(fridgeId);

        return mapToResponse(ingredient);
    }

    @Transactional
    public void deleteIngredient(Long ingredientId, Long userId) {
        // 1. 식재료 조회
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 식재료입니다."));

        // 2. 권한 검증
        Long fridgeId = ingredient.getFridgeId();
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 식재료를 삭제할 권한이 없습니다.");
        }

        ingredientRepository.delete(ingredient);

        // 3. 이력 기록 (삭제 후에는 이름을 조회할 수 없으므로 미리 확보해둔 값을 스냅샷으로 남긴다)
        saveHistory(fridgeId, ingredient.getName(), HistoryActionType.DELETED, userId,
                withEulReul(ingredient.getName()) + " 삭제했어요.");

        // 4. 캐시 무효화
        evictLayoutCache(fridgeId);
    }

    private void evictLayoutCache(Long fridgeId) {
        if (fridgeId != null) {
            Cache cache = cacheManager.getCache("fridgeLayout");
            if (cache != null) {
                cache.evict(fridgeId);
            }
        }
    }

    private IngredientDetailResponse mapToResponse(Ingredient ingredient) {
        LocalDate now = LocalDate.now();

        // createdBy/updatedBy가 같은 사용자인 경우가 많아, 별도 쿼리 두 번 대신 한 번에 배치 조회한다.
        List<Long> userIds = java.util.stream.Stream.of(ingredient.getCreatedBy(), ingredient.getUpdatedBy())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> userNamesById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        String createdByName = userNamesById.get(ingredient.getCreatedBy());
        String updatedByName = ingredient.getUpdatedBy() != null ? userNamesById.get(ingredient.getUpdatedBy()) : null;

        return IngredientDetailResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .quantity(ingredient.getQuantity())
                .unit(ingredient.getUnit())
                .expirationDate(ingredient.getExpirationDate())
                .expirationType(ingredient.getExpirationType() != null ? ingredient.getExpirationType() : ExpirationType.SELL_BY)
                .dday(ChronoUnit.DAYS.between(now, ingredient.getExpirationDate()))
                .memo(ingredient.getMemo())
                .createdByName(createdByName)
                .createdAt(ingredient.getCreatedAt())
                .updatedByName(updatedByName)
                .updatedAt(ingredient.getUpdatedBy() != null ? ingredient.getUpdatedAt() : null)
                .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    // 식재료 등록/수정뿐 아니라 냉장고 이름·타입 변경(FridgeService)에서도 재사용한다.
    // 클래스 기본이 readOnly라 외부(FridgeService)에서 호출할 때도 쓰기 트랜잭션이 되도록 명시한다.
    @Transactional
    public void saveHistory(Long fridgeId, String subjectName, HistoryActionType actionType, Long actorUserId, String summary) {
        IngredientHistory history = IngredientHistory.builder()
                .fridgeId(fridgeId)
                .ingredientName(subjectName)
                .actionType(actionType)
                .actorUserId(actorUserId)
                .actorName(resolveUserName(actorUserId))
                .summary(summary)
                .build();
        ingredientHistoryRepository.save(history);

        notifyOtherMembers(fridgeId, actorUserId, actionType, summary);
    }

    // 식재료 등록/수정/삭제, 냉장고 이름/타입 변경 등 "기록 이력"에 남는 이벤트를 이 냉장고의
    // 다른 멤버들(본인 제외)에게 푸시로 알린다. summary는 이미 자연어 문장이므로 그대로 재사용한다.
    // 단, 위치 이동만 있었던 수정은 알림이 너무 잦아지므로 푸시에서는 제외한다 (기록 이력에는 그대로 남는다).
    private void notifyOtherMembers(Long fridgeId, Long actorUserId, HistoryActionType actionType, String summary) {
        if (summary == null) return;

        String pushSummary = java.util.Arrays.stream(summary.split("\n"))
                .filter(line -> !line.contains("위치를"))
                .collect(Collectors.joining("\n"));
        if (pushSummary.isBlank()) return;

        List<FridgeMember> others = fridgeMemberRepository.findByFridgeId(fridgeId).stream()
                .filter(m -> !m.getUser().getId().equals(actorUserId))
                .collect(Collectors.toList());
        if (others.isEmpty()) return;

        String title = switch (actionType) {
            case CREATED -> "식재료 등록";
            case UPDATED -> "식재료 수정";
            case DELETED -> "식재료 삭제";
            case NAME_CHANGED -> "냉장고 이름 변경";
            case TYPE_CHANGED -> "냉장고 타입 변경";
        };
        String actorName = resolveUserName(actorUserId);
        String body = (actorName != null ? actorName + "님이 " : "") + pushSummary.replace("\n", " ");

        others.forEach(m -> pushNotificationService.send(m.getUser().getExpoPushToken(), title, body));
    }

    // 바뀐 필드마다 자연스러운 한국어 문장을 하나씩 만들어 줄바꿈으로 모은다. 바뀐 게 없으면 null.
    private String buildUpdateSummary(
            String oldName, String newName,
            Double oldQuantity, String oldUnit, Double newQuantity, String newUnit,
            LocalDate oldExpirationDate, LocalDate newExpirationDate,
            String oldMemo, String newMemo,
            Compartment oldCompartment, Compartment newCompartment
    ) {
        List<String> changes = new ArrayList<>();
        // 이름이 막 바뀐 경우엔 새 이름을, 아니면 원래 이름을 문장의 주어로 쓴다.
        String subject = newName != null ? newName : oldName;

        if (!Objects.equals(oldName, newName)) {
            changes.add(withEulReul(oldName) + " " + withEuroRo(newName) + " 변경했어요.");
        }
        if (!Objects.equals(oldQuantity, newQuantity) || !Objects.equals(oldUnit, newUnit)) {
            String newQtyText = formatQuantity(newQuantity, newUnit);
            changes.add(subject + "의 갯수를 " + formatQuantity(oldQuantity, oldUnit) + "에서 " + withEuroRo(newQtyText) + " 변경했어요.");
        }
        if (!Objects.equals(oldExpirationDate, newExpirationDate)) {
            changes.add(subject + "의 유통기한을 " + oldExpirationDate + "에서 " + withEuroRo(String.valueOf(newExpirationDate)) + " 변경했어요.");
        }
        if (!Objects.equals(oldMemo, newMemo)) {
            // 프론트엔드가 category/subLocation/memo를 memo 필드에 JSON으로 얹어 보내므로,
            // 파싱이 되면 필드별로 사람이 읽을 수 있게 나누고, 안 되면(레거시 평문 메모) 통째로 보여준다.
            JsonNode oldPayload = parseMemoPayload(oldMemo);
            JsonNode newPayload = parseMemoPayload(newMemo);

            if (oldPayload != null && newPayload != null) {
                String oldCategory = oldPayload.path("category").asText(null);
                String newCategory = newPayload.path("category").asText(null);
                if (!Objects.equals(oldCategory, newCategory)) {
                    changes.add(subject + "의 카테고리를 " + resolveCategoryLabel(oldCategory)
                            + "에서 " + withEuroRo(resolveCategoryLabel(newCategory)) + " 변경했어요.");
                }

                String oldSubLocation = oldPayload.path("subLocation").asText(null);
                String newSubLocation = newPayload.path("subLocation").asText(null);
                if (!Objects.equals(oldSubLocation, newSubLocation)) {
                    Compartment shelfLookupCompartment = newCompartment != null ? newCompartment : oldCompartment;
                    String newShelfLabel = resolveShelfLabel(shelfLookupCompartment, newSubLocation);
                    changes.add(subject + "의 보관 위치를 " + resolveShelfLabel(shelfLookupCompartment, oldSubLocation)
                            + "에서 " + withEuroRo(newShelfLabel) + " 이동시켰어요.");
                }

                String oldFreeMemo = oldPayload.path("memo").asText("");
                String newFreeMemo = newPayload.path("memo").asText("");
                if (!Objects.equals(oldFreeMemo, newFreeMemo)) {
                    String newMemoText = newFreeMemo.isBlank() ? "(없음)" : newFreeMemo;
                    changes.add(subject + "의 메모를 " + (oldFreeMemo.isBlank() ? "(없음)" : oldFreeMemo)
                            + "에서 " + withEuroRo(newMemoText) + " 변경했어요.");
                }
            } else {
                String newMemoText = (newMemo == null || newMemo.isBlank()) ? "(없음)" : newMemo;
                changes.add(subject + "의 메모를 " + (oldMemo == null || oldMemo.isBlank() ? "(없음)" : oldMemo)
                        + "에서 " + withEuroRo(newMemoText) + " 변경했어요.");
            }
        }
        if (oldCompartment != null && newCompartment != null && !Objects.equals(oldCompartment.getId(), newCompartment.getId())) {
            changes.add(subject + "의 위치를 " + oldCompartment.getName() + "에서 " + withEuroRo(newCompartment.getName()) + " 이동시켰어요.");
        }

        return changes.isEmpty() ? null : String.join("\n", changes);
    }

    // 한글 종성(받침) 유무에 따라 "을/를", "으로/로" 중 맞는 조사를 붙인다.
    // 받침을 판단할 수 없는 문자(공백/괄호/따옴표 등)는 건너뛰고 그 앞의 실질 문자를 본다.
    private boolean hasBatchim(String word) {
        if (word == null) return false;
        for (int i = word.length() - 1; i >= 0; i--) {
            char c = word.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                return (c - 0xAC00) % 28 != 0;
            }
            if (Character.isDigit(c)) {
                return "013678".indexOf(c) >= 0; // 영/일/삼/육/칠/팔은 받침 있음, 이/사/오/구는 없음
            }
            if (Character.isLetter(c)) {
                return false; // 영문 단위(kg, ml 등)는 받침 없는 것으로 간주
            }
        }
        return false;
    }

    private String withEulReul(String word) {
        return word + (hasBatchim(word) ? "을" : "를");
    }

    private String withEuroRo(String word) {
        return word + (hasBatchim(word) ? "으로" : "로");
    }

    // memo 필드가 프론트엔드의 {category, subLocation, memo} JSON 형식인 경우에만 파싱해서 반환, 아니면 null
    private JsonNode parseMemoPayload(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(memo);
            return (node.has("category") || node.has("subLocation")) ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCategoryLabel(String category) {
        if (category == null) {
            return "(없음)";
        }
        return CATEGORY_LABELS.getOrDefault(category, category);
    }

    // 구획의 insideShelves/doorShelves JSON({id, label} 배열)에서 선반 ID에 해당하는 라벨을 찾는다
    private String resolveShelfLabel(Compartment compartment, String shelfId) {
        if (shelfId == null) {
            return "(없음)";
        }
        if (compartment == null) {
            return shelfId;
        }
        String label = findShelfLabel(compartment.getInsideShelves(), shelfId);
        if (label == null) {
            label = findShelfLabel(compartment.getDoorShelves(), shelfId);
        }
        return label != null ? label : shelfId;
    }

    private String findShelfLabel(String shelvesJson, String shelfId) {
        if (shelvesJson == null) {
            return null;
        }
        try {
            JsonNode shelves = objectMapper.readTree(shelvesJson);
            for (JsonNode shelf : shelves) {
                if (shelfId.equals(shelf.path("id").asText())) {
                    return shelf.path("label").asText(shelfId);
                }
            }
        } catch (Exception e) {
            // 파싱 실패 시 raw id로 폴백
        }
        return null;
    }

    private String formatQuantity(Double quantity, String unit) {
        return quantity + (unit != null ? unit : "");
    }

    public List<IngredientHistoryResponse> getHistory(Long fridgeId, Long userId) {
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 대한 접근 권한이 없습니다.");
        }

        return ingredientHistoryRepository.findTop200ByFridgeIdOrderByCreatedAtDesc(fridgeId).stream()
                .map(h -> IngredientHistoryResponse.builder()
                        .id(h.getId())
                        .actionType(h.getActionType())
                        .ingredientName(h.getIngredientName())
                        .actorName(h.getActorName())
                        .summary(h.getSummary())
                        .occurredAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
