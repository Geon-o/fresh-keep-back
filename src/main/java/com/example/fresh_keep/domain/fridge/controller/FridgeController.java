package com.example.fresh_keep.domain.fridge.controller;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.FridgeResponse;
import com.example.fresh_keep.domain.fridge.service.FridgeService;
import com.example.fresh_keep.domain.fridge.dto.FridgeLayoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fridges")
@RequiredArgsConstructor
public class FridgeController {

    private final FridgeService fridgeService;

    @PostMapping
    public ResponseEntity<FridgeResponse> createFridge(
            @Valid @RequestBody CreateFridgeRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FridgeResponse response = fridgeService.createFridge(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FridgeResponse>> getFridges(
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<FridgeResponse> response = fridgeService.getFridges(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{fridgeId}/layouts")
    public ResponseEntity<FridgeLayoutResponse> getFridgeLayout(
            @PathVariable("fridgeId") Long fridgeId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FridgeLayoutResponse response = fridgeService.getFridgeLayout(fridgeId, userId);
        return ResponseEntity.ok(response);
    }
}
