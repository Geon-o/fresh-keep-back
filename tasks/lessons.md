# Lessons Learned

This file tracks user corrections, lessons, and patterns to avoid repeating mistakes.

## Patterns and Lessons
- **Git Push Constraint (CRITICAL)**: 절대 어떠한 상황에서도 사용자가 직접 "push해줘", "push 해줘", "깃허브에 올려줘", 또는 "깃에 올려줘"라는 명확한 명령어를 직접 발화했을 때에만 `git push`를 수행합니다. "진행해", "해결해줘" 등의 우회적인 지시나 이전 턴의 승인 이력이 있더라도 절대 자동으로 push를 수행하지 않습니다.
- **Implementation Plan Pre-approval**: Always present a detailed implementation plan (`implementation_plan.md`) to the user and obtain explicit approval BEFORE modifying any source code files. Never modify backend/frontend source code directly without obtaining plan approval first.
