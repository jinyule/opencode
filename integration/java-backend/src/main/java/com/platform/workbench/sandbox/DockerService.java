package com.platform.workbench.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.platform.workbench.domain.entity.SandboxInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker 容器操作封装。
 * 底层使用 docker-java 客户端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private final DockerClient dockerClient;

    @Value("${workbench.opencode.image:opencode-sandbox:latest}")
    private String sandboxImage;

    @Value("${workbench.docker.network:workbench-sandbox-net}")
    private String sandboxNetwork;

    @Value("${workbench.sandbox.resources.cpu-count:2}")
    private long cpuCount;

    @Value("${workbench.sandbox.resources.memory-bytes:4294967296}")
    private long memoryBytes;

    @Value("${workbench.opencode.llm-keys.anthropic:}")
    private String anthropicKey;

    @Value("${workbench.opencode.llm-keys.openai:}")
    private String openaiKey;

    @Value("${workbench.opencode.llm-keys.google:}")
    private String googleKey;

    @Value("${workbench.opencode.llm-keys.openrouter:}")
    private String openrouterKey;

    public record ContainerInfo(String containerId, String host) {}

    /**
     * 创建并启动沙箱容器，返回容器 ID 和内网 IP。
     */
    public ContainerInfo startSandboxContainer(SandboxInstance sandbox) {
        // 环境变量
        List<String> env = buildEnv(sandbox);

        // Volume 挂载
        List<Bind> binds = List.of(
                new Bind(sandbox.getWorkspaceVolume(),
                        new Volume("/workspace")),
                new Bind(sandbox.getDataVolume(),
                        new Volume("/root/.local/share/opencode"))
        );

        // 资源限制
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(binds)
                .withNetworkMode(sandboxNetwork)
                .withCpuCount(cpuCount)
                .withMemory(memoryBytes)
                .withRestartPolicy(RestartPolicy.noRestart());

        // 创建容器
        CreateContainerResponse container = dockerClient.createContainerCmd(sandboxImage)
                .withEnv(env)
                .withHostConfig(hostConfig)
                .withLabels(java.util.Map.of(
                        "workbench.sandbox.id", String.valueOf(sandbox.getId()),
                        "workbench.user.id", String.valueOf(sandbox.getUserId())
                ))
                .exec();

        String containerId = container.getId();
        log.info("Created container {} for sandbox {}", containerId, sandbox.getId());

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 获取容器 IP
        String ip = dockerClient.inspectContainerCmd(containerId)
                .exec()
                .getNetworkSettings()
                .getNetworks()
                .get(sandboxNetwork)
                .getIpAddress();

        log.info("Container {} started, IP={}", containerId, ip);
        return new ContainerInfo(containerId, ip);
    }

    public void stopContainer(String containerId) {
        if (containerId == null) return;
        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.info("Stopped container {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
        }
    }

    public void removeContainer(String containerId) {
        if (containerId == null) return;
        try {
            stopContainer(containerId);
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            log.info("Removed container {}", containerId);
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    public void removeVolume(String volumeName) {
        if (volumeName == null) return;
        try {
            dockerClient.removeVolumeCmd(volumeName).exec();
            log.info("Removed volume {}", volumeName);
        } catch (Exception e) {
            log.warn("Failed to remove volume {}: {}", volumeName, e.getMessage());
        }
    }

    private List<String> buildEnv(SandboxInstance sandbox) {
        List<String> env = new ArrayList<>();
        env.add("OPENCODE_SERVER_PASSWORD=" + sandbox.getPassword());
        if (!anthropicKey.isBlank()) env.add("ANTHROPIC_API_KEY=" + anthropicKey);
        if (!openaiKey.isBlank())    env.add("OPENAI_API_KEY=" + openaiKey);
        if (!googleKey.isBlank())    env.add("GOOGLE_GENERATIVE_AI_API_KEY=" + googleKey);
        if (!openrouterKey.isBlank()) env.add("OPENROUTER_API_KEY=" + openrouterKey);
        return env;
    }
}
