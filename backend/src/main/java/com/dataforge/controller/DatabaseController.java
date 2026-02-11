package com.dataforge.controller;

import com.dataforge.dto.CreateDatabaseRequest;
import com.dataforge.dto.CreateDatabaseResponse;
import com.dataforge.dto.DatabaseInstanceInfo;
import com.dataforge.dto.DiscoveredContainerInfo;
import com.dataforge.model.DatabaseInstance;
import com.dataforge.service.DatabaseInstanceService;
import com.dataforge.service.DockerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/databases")
public class DatabaseController {

    @Autowired
    private DockerService dockerService;

    @Autowired
    private DatabaseInstanceService instanceService;

    @PostMapping
    public ResponseEntity<CreateDatabaseResponse> createDatabase(@Valid @RequestBody CreateDatabaseRequest request) {
        DatabaseInstance instance = dockerService.createPostgresContainer(
            request.dbName(),
            request.user(),
            request.password(),
            request.imageTag()
        );

        CreateDatabaseResponse response = new CreateDatabaseResponse(
            instance.getContainerId(),
            instance.getDbName(),
            instance.getDbUser(),
            instance.getHost(),
            instance.getPort()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DatabaseInstanceInfo>> getAllDatabases() {
        List<DatabaseInstanceInfo> instances = instanceService.getAllInstances().stream()
                .map(instance -> new DatabaseInstanceInfo(
                        instance.getId(),
                        instance.getDbName(),
                        instance.getDbUser(),
                        instance.getHost(),
                        instance.getPort(),
                        dockerService.getContainerStatus(instance.getContainerId())
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(instances);
    }

    @PostMapping("/discover")
    public ResponseEntity<List<DiscoveredContainerInfo>> discoverDatabases() {
        List<DiscoveredContainerInfo> discovered = dockerService.discoverPgContainers();
        List<String> managedContainerIds = instanceService.getAllInstances().stream()
                .map(DatabaseInstance::getContainerId)
                .collect(Collectors.toList());
        
        List<DiscoveredContainerInfo> unmanaged = discovered.stream()
                .filter(d -> !managedContainerIds.contains(d.containerId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(unmanaged);
    }

    @PostMapping("/adopt")
    public ResponseEntity<DatabaseInstanceInfo> adoptDatabase(@RequestBody DiscoveredContainerInfo discoveredInfo) {
        try {
            DatabaseInstance adoptedInstance = instanceService.adoptInstance(discoveredInfo);
            DatabaseInstanceInfo responseDto = new DatabaseInstanceInfo(
                adoptedInstance.getId(),
                adoptedInstance.getDbName(),
                adoptedInstance.getDbUser(),
                adoptedInstance.getHost(),
                adoptedInstance.getPort(),
                dockerService.getContainerStatus(adoptedInstance.getContainerId())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDatabase(
            @PathVariable Long id,
            @RequestParam(name = "deleteContainer", defaultValue = "false") boolean deleteContainer) {
        
        instanceService.deleteInstance(id, deleteContainer);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<String> startDatabase(@PathVariable Long id) {
        DatabaseInstance instance = instanceService.getInstanceById(id);
        int newPort = dockerService.startContainer(instance.getContainerId());
        
        if (newPort != 0 && newPort != instance.getPort()) {
            instanceService.updateInstancePort(id, newPort);
        }
        
        return ResponseEntity.ok("Database instance " + id + " started successfully on port " + newPort);
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<String> stopDatabase(@PathVariable Long id) {
        DatabaseInstance instance = instanceService.getInstanceById(id);
        dockerService.stopContainer(instance.getContainerId());
        return ResponseEntity.ok("Database instance " + id + " stopped successfully.");
    }

    @GetMapping("/{dbId}/test-connection")
    public ResponseEntity<String> testDatabaseConnection(@PathVariable Long dbId) {
        boolean isConnected = instanceService.testConnection(dbId);
        if (isConnected) {
            return ResponseEntity.ok("Connection to database ID " + dbId + " successful.");
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Failed to connect to database ID " + dbId + ". It might be down or connection details are incorrect.");
        }
    }
}
