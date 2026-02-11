package com.dataforge.controller;

import com.dataforge.dto.MasterPasswordRequest;
import com.dataforge.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired
    private SettingsService settingsService;

    @GetMapping("/master-password/is-set")
    public ResponseEntity<Map<String, Boolean>> isMasterPasswordSet() {
        return ResponseEntity.ok(Map.of("isSet", settingsService.isMasterPasswordSet()));
    }

    @PostMapping("/master-password")
    public ResponseEntity<String> setMasterPassword(@Valid @RequestBody MasterPasswordRequest request) {
        // The validation is now handled by the @ValidPassword annotation and GlobalExceptionHandler
        
        if (settingsService.isMasterPasswordSet()) {
            return ResponseEntity.status(409).body("Master password has already been set."); // 409 Conflict
        }

        settingsService.setMasterPassword(request.password());
        return ResponseEntity.status(201).body("Master password has been set successfully.");
    }
}
