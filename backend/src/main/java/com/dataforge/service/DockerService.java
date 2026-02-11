package com.dataforge.service;

import com.dataforge.dto.DiscoveredContainerInfo;
import com.dataforge.model.DatabaseInstance;
import com.dataforge.repository.DatabaseInstanceRepository;
import com.dataforge.util.EncryptionUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DockerService {

    private final DatabaseInstanceRepository repository;
    private final EncryptionUtil encryptionUtil;
    
    private DockerClient dockerClient;
    private boolean isDockerConnected = false;

    private static final String DEFAULT_POSTGRES_TAG = "13";

    @Autowired
    public DockerService(DatabaseInstanceRepository repository, EncryptionUtil encryptionUtil) {
        this.repository = repository;
        this.encryptionUtil = encryptionUtil;
    }

    @PostConstruct
    public void init() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();
            this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
            this.dockerClient.pingCmd().exec();
            this.isDockerConnected = true;
            System.out.println("Successfully connected to Docker daemon.");
        } catch (Exception e) {
            this.isDockerConnected = false;
            System.err.println("!!! FAILED TO CONNECT TO DOCKER DAEMON !!!");
            System.err.println("Please ensure Docker is installed and running.");
            System.err.println("Error: " + e.getMessage());
        }
    }

    public boolean isDockerConnected() {
        return this.isDockerConnected;
    }

    public List<DiscoveredContainerInfo> discoverPgContainers() {
        if (!isDockerConnected) {
            throw new IllegalStateException("Cannot discover containers: Not connected to Docker daemon.");
        }

        List<DiscoveredContainerInfo> discovered = new ArrayList<>();
        List<Container> allContainers = dockerClient.listContainersCmd().withShowAll(true).exec();

        for (Container container : allContainers) {
            if (container.getImage().contains("postgres")) {
                try {
                    InspectContainerResponse details = dockerClient.inspectContainerCmd(container.getId()).exec();
                    Map<String, String> envMap = parseEnv(details.getConfig().getEnv());

                    String dbName = envMap.getOrDefault("POSTGRES_DB", "N/A");
                    String dbUser = envMap.getOrDefault("POSTGRES_USER", "N/A");
                    String dbPassword = envMap.getOrDefault("POSTGRES_PASSWORD", "N/A");

                    int hostPort = getHostPort(details);

                    if (hostPort != 0) {
                        discovered.add(new DiscoveredContainerInfo(
                            container.getId(),
                            details.getName().substring(1),
                            container.getImage(),
                            container.getStatus(),
                            dbName,
                            dbUser,
                            dbPassword,
                            hostPort
                        ));
                    }
                } catch (Exception e) {
                    System.err.println("Could not inspect container " + container.getId() + ": " + e.getMessage());
                }
            }
        }
        return discovered;
    }

    public String getContainerStatus(String containerId) {
        if (!isDockerConnected || containerId == null) {
            return "unknown";
        }
        try {
            InspectContainerResponse response = dockerClient.inspectContainerCmd(containerId).exec();
            return response.getState().getStatus();
        } catch (NotFoundException e) {
            return "not_found";
        }
    }

    public int startContainer(String containerId) {
        if (!isDockerConnected) {
            throw new IllegalStateException("Cannot start container: Not connected to Docker daemon.");
        }
        System.out.println("Starting container: " + containerId);
        dockerClient.startContainerCmd(containerId).exec();

        InspectContainerResponse details = dockerClient.inspectContainerCmd(containerId).exec();
        return getHostPort(details);
    }

    public void stopContainer(String containerId) {
        if (!isDockerConnected) {
            throw new IllegalStateException("Cannot stop container: Not connected to Docker daemon.");
        }
        System.out.println("Stopping container: " + containerId);
        dockerClient.stopContainerCmd(containerId).exec();
    }

    public DatabaseInstance createPostgresContainer(String dbName, String user, String password, String imageTag) {
        if (!isDockerConnected) {
            throw new IllegalStateException("Cannot create container: Not connected to Docker daemon.");
        }

        final String tagToUse = StringUtils.hasText(imageTag) ? imageTag : DEFAULT_POSTGRES_TAG;
        final String imageName = "postgres:" + tagToUse;

        System.out.println("Ensuring " + imageName + " image exists locally...");
        try {
            dockerClient.pullImageCmd("postgres").withTag(tagToUse).start().awaitCompletion();
            System.out.println(imageName + " image is ready.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed while waiting for " + imageName + " image to pull", e);
        }

        ExposedPort exposedPort = ExposedPort.tcp(5432);
        PortBinding portBinding = new PortBinding(Ports.Binding.empty(), exposedPort);
        HostConfig hostConfig = new HostConfig().withPortBindings(portBinding);

        CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                .withEnv("POSTGRES_DB=" + dbName, "POSTGRES_USER=" + user, "POSTGRES_PASSWORD=" + password)
                .withExposedPorts(exposedPort)
                .withHostConfig(hostConfig)
                .exec();

        String containerId = container.getId();
        dockerClient.startContainerCmd(containerId).exec();

        InspectContainerResponse details = dockerClient.inspectContainerCmd(containerId).exec();
        int hostPort = getHostPort(details);

        DatabaseInstance instance = new DatabaseInstance();
        instance.setContainerId(containerId);
        instance.setDbName(dbName);
        instance.setDbUser(user);
        instance.setDbPassword(encryptionUtil.encrypt(password));
        instance.setHost("localhost"); 
        instance.setPort(hostPort);

        return repository.save(instance);
    }

    public void removeContainer(String containerId) {
        if (!isDockerConnected) {
            throw new IllegalStateException("Cannot remove container: Not connected to Docker daemon.");
        }
        try {
            stopContainer(containerId);
        } catch (Exception e) {
            System.out.println("Container " + containerId + " could not be stopped, maybe already stopped.");
        }

        try {
            dockerClient.removeContainerCmd(containerId).exec();
            System.out.println("Container " + containerId + " removed successfully.");
        } catch (NotFoundException e) {
            System.out.println("Container " + containerId + " not found, maybe already removed.");
        }
    }

    private Map<String, String> parseEnv(String[] env) {
        if (env == null) {
            return Map.of();
        }
        return Arrays.stream(env)
                .map(e -> e.split("=", 2))
                .collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));
    }

    private int getHostPort(InspectContainerResponse details) {
        try {
            Ports.Binding[] bindings = details.getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432));
            if (bindings != null && bindings.length > 0) {
                return Integer.parseInt(bindings[0].getHostPortSpec());
            }
        } catch (Exception e) {
            System.err.println("Could not determine host port for container " + details.getId());
        }
        return 0;
    }
}
