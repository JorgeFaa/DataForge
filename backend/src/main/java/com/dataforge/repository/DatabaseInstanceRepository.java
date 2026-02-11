package com.dataforge.repository;

import com.dataforge.model.DatabaseInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatabaseInstanceRepository extends JpaRepository<DatabaseInstance, Long> {
    // Add this method to find an instance by its container ID
    Optional<DatabaseInstance> findByContainerId(String containerId);
}
