package com.example.fresh_keep.domain.fridge.controller;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.FridgeResponse;
import com.example.fresh_keep.domain.fridge.service.FridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
