package com.example.fresh_keep.domain.memo.controller;

import com.example.fresh_keep.domain.memo.dto.CreateMemoRequest;
import com.example.fresh_keep.domain.memo.dto.MemoResponse;
import com.example.fresh_keep.domain.memo.dto.UpdateMemoRequest;
import com.example.fresh_keep.domain.memo.service.MemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fridges/{fridgeId}/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    @GetMapping
    public ResponseEntity<List<MemoResponse>> list(
            @PathVariable("fridgeId") Long fridgeId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(memoService.list(fridgeId, userId));
    }

    @PostMapping
    public ResponseEntity<MemoResponse> create(
            @PathVariable("fridgeId") Long fridgeId,
            @Valid @RequestBody CreateMemoRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        MemoResponse response = memoService.create(fridgeId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{memoId}")
    public ResponseEntity<MemoResponse> update(
            @PathVariable("fridgeId") Long fridgeId,
            @PathVariable("memoId") Long memoId,
            @Valid @RequestBody UpdateMemoRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(memoService.update(fridgeId, memoId, userId, request));
    }

    @DeleteMapping("/{memoId}")
    public ResponseEntity<Void> delete(
            @PathVariable("fridgeId") Long fridgeId,
            @PathVariable("memoId") Long memoId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        memoService.delete(fridgeId, memoId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{memoId}/items/{itemId}/toggle")
    public ResponseEntity<MemoResponse> toggleItem(
            @PathVariable("fridgeId") Long fridgeId,
            @PathVariable("memoId") Long memoId,
            @PathVariable("itemId") String itemId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(memoService.toggleItem(fridgeId, memoId, itemId, userId));
    }

    @PostMapping("/read")
    public ResponseEntity<Void> markRead(
            @PathVariable("fridgeId") Long fridgeId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        memoService.markRead(fridgeId, userId);
        return ResponseEntity.ok().build();
    }
}
