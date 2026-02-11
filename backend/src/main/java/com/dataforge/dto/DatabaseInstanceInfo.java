package com.dataforge.dto;

public record DatabaseInstanceInfo(
    Long id,
    String dbName,
    String user,
    String host,
    int port,
    String status // e.g., "running", "exited", "unknown"
) {}
