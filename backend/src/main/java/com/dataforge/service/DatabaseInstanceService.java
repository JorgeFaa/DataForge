package com.dataforge.service;

import com.dataforge.dto.DiscoveredContainerInfo;
import com.dataforge.exception.ResourceNotFoundException;
import com.dataforge.model.DatabaseInstance;
import com.dataforge.repository.DatabaseInstanceRepository;
import com.dataforge.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Service
public class DatabaseInstanceService {

    private final DatabaseInstanceRepository repository;
    private final DockerService dockerService;
    private final EncryptionUtil encryptionUtil;

    @Autowired
    public DatabaseInstanceService(DatabaseInstanceRepository repository, DockerService dockerService, EncryptionUtil encryptionUtil) {
        this.repository = repository;
        this.dockerService = dockerService;
        this.encryptionUtil = encryptionUtil;
    }

    public List<DatabaseInstance> getAllInstances() {
        return repository.findAll();
    }

    public DatabaseInstance getInstanceById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Database instance not found with id: " + id));
    }

    @Transactional
    public DatabaseInstance adoptInstance(DiscoveredContainerInfo discoveredInfo) {
        if (repository.findByContainerId(discoveredInfo.containerId()).isPresent()) {
            throw new IllegalStateException("Container with ID " + discoveredInfo.containerId() + " is already managed.");
        }

        DatabaseInstance newInstance = new DatabaseInstance();
        newInstance.setContainerId(discoveredInfo.containerId());
        newInstance.setDbName(discoveredInfo.dbName());
        newInstance.setDbUser(discoveredInfo.dbUser());
        newInstance.setDbPassword(encryptionUtil.encrypt(discoveredInfo.dbPassword()));
        newInstance.setHost("localhost");
        newInstance.setPort(discoveredInfo.hostPort());

        return repository.save(newInstance);
    }

    @Transactional
    public void updateInstancePort(Long id, int newPort) {
        DatabaseInstance instance = getInstanceById(id);
        instance.setPort(newPort);
        repository.save(instance);
        System.out.println("Updated port for instance " + id + " to " + newPort);
    }

    @Transactional
    public void deleteInstance(Long id, boolean deleteDockerContainer) {
        DatabaseInstance instance = getInstanceById(id);

        if (deleteDockerContainer) {
            System.out.println("Deleting container " + instance.getContainerId() + " as requested.");
            dockerService.removeContainer(instance.getContainerId());
        } else {
            System.out.println("Un-managing instance " + id + ". The Docker container will not be deleted.");
        }
        
        repository.deleteById(id);
    }

    public boolean testConnection(Long dbId) {
        DatabaseInstance instance = getInstanceById(dbId);

        String url = String.format("jdbc:postgresql://%s:%d/%s", instance.getHost(), instance.getPort(), instance.getDbName());
        String user = instance.getDbUser();
        String decryptedPassword = encryptionUtil.decrypt(instance.getDbPassword());

        try (Connection conn = DriverManager.getConnection(url, user, decryptedPassword)) {
            return conn.isValid(5);
        } catch (SQLException e) {
            System.err.println("Failed to test connection for DB ID " + dbId + ": " + e.getMessage());
            return false;
        }
    }
}
