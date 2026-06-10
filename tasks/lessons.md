# Lessons Learned

This file tracks user corrections, lessons, and patterns to avoid repeating mistakes.

## Patterns and Lessons
- **Git Push Constraint**: Never execute `git push` automatically after completing a code change or build verification unless the user explicitly requests to "push it to GitHub" in the immediate turn. Always ask for confirmation before pushing code to the remote repository.
- **Implementation Plan Pre-approval**: Always present a detailed implementation plan (`implementation_plan.md`) to the user and obtain explicit approval BEFORE modifying any source code files. Never modify backend/frontend source code directly without obtaining plan approval first.
