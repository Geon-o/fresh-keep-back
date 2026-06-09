# FreshKeep API Specification (Draft)

이 문서는 냉장고 식재료 관리 앱(FreshKeep)의 백엔드 API 명세서입니다. 프론트엔드 개발 및 AI 에이전트 연동을 위해 작성되었습니다.

---

## 1. 공통 데이터 모델 (Data Models)

### Refrigerator (냉장고)
```json
{
  "id": 1,
  "name": "우리집 냉장고",
  "type": "FOUR_DOOR" // FOUR_DOOR (4도어), SIDE_BY_SIDE (양문형), TOP_BOTTOM (위아래)
}
```

### Compartment (냉장고 칸/구획)
냉장고 형태(`type`)에 따라 구획 리스트가 정렬되어 전달됩니다.
```json
{
  "id": 12,
  "fridgeId": 1,
  "name": "상단 좌측 냉장실",
  "storageType": "REFRIGERATED", // REFRIGERATED, FROZEN, ROOM_TEMP, SPECIAL
  "sequenceOrder": 1
}
```

### Ingredient (식재료)
```json
{
  "id": 105,
  "compartmentId": 12,
  "name": "우유",
  "quantity": 1.0,
  "unit": "개", // 개, g, kg, ml 등
  "expirationDate": "2026-06-15", // YYYY-MM-DD
  "memo": "개봉 후 가급적 빨리 마시기",
  "dday": 7 // 서버에서 계산하여 내려주는 유통기한 D-Day (유통기한 오늘 기준 남은 일수)
}
```

---

## 2. API 엔드포인트 목록 (API Endpoints)

### 2.1. 냉장고 (Fridge) API

#### 냉장고 생성 (POST `/api/fridges`)
사용자의 새 냉장고 인스턴스를 생성합니다. (기본 구획 자동 생성 포함)
* **Request Body**:
  ```json
  {
    "name": "메인 냉장고",
    "type": "FOUR_DOOR"
  }
  ```
* **Response (201 Created)**:
  ```json
  {
    "id": 1,
    "name": "메인 냉장고",
    "type": "FOUR_DOOR"
  }
  ```

#### 내 냉장고 목록 조회 (GET `/api/fridges`)
현재 로그인한 유저가 참여 중인 냉장고 목록을 반환합니다.
* **Response (200 OK)**:
  ```json
  [
    {
      "id": 1,
      "name": "우리집 냉장고",
      "type": "FOUR_DOOR",
      "role": "OWNER" // OWNER, MEMBER
    }
  ]
  ```

---

### 2.2. 구획 및 레이아웃 (Compartment) API

#### 냉장고 내 구획 목록 및 식재료 조회 (GET `/api/fridges/{fridgeId}/layouts`)
프론트엔드 화면을 그리기 위해 냉장고의 전체 구조(칸)와 그 칸에 담긴 식재료 목록을 일괄 반환합니다.
* **Response (200 OK)**:
  ```json
  {
    "fridgeId": 1,
    "fridgeName": "우리집 냉장고",
    "type": "FOUR_DOOR",
    "compartments": [
      {
        "id": 12,
        "name": "상단 좌측 냉장실",
        "storageType": "REFRIGERATED",
        "sequenceOrder": 1,
        "ingredients": [
          {
            "id": 105,
            "name": "우유",
            "quantity": 1.0,
            "unit": "개",
            "expirationDate": "2026-06-15",
            "dday": 7
          }
        ]
      },
      {
        "id": 13,
        "name": "하단 우측 냉동실",
        "storageType": "FROZEN",
        "sequenceOrder": 2,
        "ingredients": []
      }
    ]
  }
  ```

---

### 2.3. 식재료 (Ingredient) API

#### 식재료 등록 (POST `/api/ingredients`)
특정 구획에 식재료를 추가합니다.
* **Request Body**:
  ```json
  {
    "compartmentId": 12,
    "name": "사과",
    "quantity": 5.0,
    "unit": "개",
    "expirationDate": "2026-06-20",
    "memo": "씻어서 보관"
  }
  ```
* **Response (201 Created)**:
  ```json
  {
    "id": 106,
    "compartmentId": 12,
    "name": "사과",
    "quantity": 5.0,
    "unit": "개",
    "expirationDate": "2026-06-20",
    "memo": "씻어서 보관",
    "dday": 12
  }
  ```

#### 식재료 정보 수정 (PATCH `/api/ingredients/{ingredientId}`)
수량, 메모, 유통기한 변경 또는 다른 칸으로 이동(`compartmentId` 변경) 시 사용합니다.
* **Request Body**:
  ```json
  {
    "compartmentId": 12, // (선택) 다른 칸으로 이동할 때 지정
    "name": "사과",
    "quantity": 4.0, // 개수 차감
    "expirationDate": "2026-06-20",
    "memo": "맛있는 사과"
  }
  ```
* **Response (200 OK)**: (수정된 식재료 정보 반환)

#### 식재료 삭제 (DELETE `/api/ingredients/{ingredientId}`)
식재료를 소비했거나 폐기하여 냉장고에서 제거합니다.
* **Response (204 No Content)**

---

### 2.4. 유통기한 임박 조회 API

#### 유통기한 임박 식재료 조회 (GET `/api/ingredients/urgent?days=3`)
로그인한 사용자의 모든 냉장고에서 유통기한이 남은 일수가 지정한 날짜(기본 3일) 이하인 식재료 목록을 반환합니다.
* **Response (200 OK)**:
  ```json
  [
    {
      "ingredientId": 105,
      "fridgeName": "우리집 냉장고",
      "compartmentName": "상단 좌측 냉장실",
      "name": "우유",
      "expirationDate": "2026-06-10",
      "dday": 2
    }
  ]
  ```
