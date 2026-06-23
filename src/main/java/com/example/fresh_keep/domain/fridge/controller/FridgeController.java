package com.example.fresh_keep.domain.fridge.controller;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.FridgeResponse;
import com.example.fresh_keep.domain.fridge.dto.UpdateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.UpdateShelvesRequest;
import com.example.fresh_keep.domain.fridge.service.FridgeService;
import com.example.fresh_keep.domain.fridge.dto.FridgeLayoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PatchMapping("/{fridgeId}")
    public ResponseEntity<FridgeResponse> updateFridge(
            @PathVariable("fridgeId") Long fridgeId,
            @Valid @RequestBody UpdateFridgeRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        FridgeResponse response = fridgeService.updateFridge(fridgeId, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{fridgeId}")
    public ResponseEntity<Void> deleteFridge(
            @PathVariable("fridgeId") Long fridgeId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        fridgeService.deleteFridge(fridgeId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{fridgeId}/compartments/{compartmentId}/shelves")
    public ResponseEntity<Void> updateCompartmentShelves(
            @PathVariable("fridgeId") Long fridgeId,
            @PathVariable("compartmentId") Long compartmentId,
            @Valid @RequestBody UpdateShelvesRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        fridgeService.updateCompartmentShelves(fridgeId, compartmentId, request, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/share")
    public ResponseEntity<?> shareFridge(
            @RequestBody ShareFridgeRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.getFridgeUuid() == null || request.getFridgeUuid().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Fridge UUID is required."));
        }

        try {
            FridgeResponse response = fridgeService.shareFridge(request.getFridgeUuid().trim(), userId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @lombok.Data
    public static class ShareFridgeRequest {
        private String fridgeUuid;
    }
}
