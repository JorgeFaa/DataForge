package com.dataforge.dto;

import java.util.Map;

/**
 * Represents a PostgreSQL container found on the user's system
 * that is not yet managed by DataForge.
 */
public record DiscoveredContainerInfo(
    String containerId,
    String containerName,
    String image,
    String status,
    // Extracted from environment variables
    String dbName,
    String dbUser,
    String dbPassword,
    // Port mapping
    int hostPort
) {}
