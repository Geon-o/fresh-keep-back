# Lessons Learned

This file tracks user corrections, lessons, and patterns to avoid repeating mistakes.

## Patterns and Lessons
- **Git Push Constraint (CRITICAL)**: 절대 어떠한 상황에서도 사용자가 직접 "push해줘", "push 해줘", "깃허브에 올려줘", 또는 "깃에 올려줘"라는 명확한 명령어를 직접 발화했을 때에만 `git push`를 수행합니다. "진행해", "해결해줘" 등의 우회적인 지시나 이전 턴의 승인 이력이 있더라도 절대 자동으로 push를 수행하지 않습니다.
- **Implementation Plan Pre-approval**: Always present a detailed implementation plan (`implementation_plan.md`) to the user and obtain explicit approval BEFORE modifying any source code files. Never modify backend/frontend source code directly without obtaining plan approval first.
- **Logout Race Condition & State Reset**:
  - 자발적 로그아웃 시 토큰이 먼저 파기되면서 렌더링 프레임의 일시적 차이로 호출된 API가 401 에러를 반환할 수 있습니다. 이로 인해 '세션 만료' 팝업이 부적절하게 뜨는 현상을 막기 위해 자발적 로그아웃 플래그(`isVoluntaryLogout`)를 두어 팝업 노출을 제어해야 합니다.
  - 로그인 상태 변동 등으로 냉장고 목록이 급격히 줄어들 때(예: 서버 데이터 3개 -> 로컬 데이터 0개), 캐러셀의 `activeIndex`가 이전 인덱스에 머물러 빈 화면(Out of Bounds)이 노출되지 않도록 데이터 크기 변경 시 `activeIndex`를 즉시 `0`으로 초기화해 주어야 합니다.
- **LLM JSON Response Pre-processing**: Gemini와 같은 LLM은 프롬프트에서 순수한 JSON 출력을 요청하더라도 마크다운 코드 블록(```json ... ```)으로 묶어서 응답하는 경우가 빈번히 발생합니다. Jackson 등으로 응답 데이터를 파싱하기 전 반드시 백틱과 포맷 식별자를 제거하는 전처리 로직을 적용하여 파싱 오류(JsonParseException)를 사전 방지해야 합니다.
- **Cache Match Loosening (Substring Containment)**: 단순 오타 검사(Levenshtein Distance)만으로는 "말린베트남고추", "건베트남고추"와 같이 접두사/접미사 수식어가 붙어 원본("베트남고추")과 글자수 차이가 많이 나는 캐시를 매칭하지 못하고 매번 AI를 호출하는 비효율이 발생합니다. 최소 글자수(예: 2자 이상) 제한을 둔 상태에서 상호 포함 관계(Substring match) 검사를 사전에 거쳐 기존 캐시가 존재할 시 즉각 재사용함으로써 인프라 호출 비용을 낮추고 성능을 극대화할 수 있습니다.
