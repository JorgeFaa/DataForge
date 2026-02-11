package com.dataforge.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDatabaseRequest(
    @NotBlank(message = "Database name cannot be empty")
    String dbName,
    
    @NotBlank(message = "User cannot be empty")
    String user,
    
    @NotBlank(message = "Password cannot be empty")
    String password,
    
    // Optional: The user can specify a PostgreSQL version tag (e.g., "13", "16", "latest")
    String imageTag
) {
    // The isValid() method is kept for legacy reasons but validation is now annotation-driven.
    public boolean isValid() {
        return dbName != null && !dbName.isBlank() &&
               user != null && !user.isBlank() &&
               password != null && !password.isBlank();
    }
}
