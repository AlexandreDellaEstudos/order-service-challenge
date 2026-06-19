package br.com.meli.order.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

public abstract class WireMockContainerBase {

    protected static final GenericContainer<?> WIREMOCK =
            new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.3.1"))
                    .withExposedPorts(8080)
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(Paths.get("..", "wiremock", "mappings").toAbsolutePath().toString()),
                            "/home/wiremock/mappings")
                    .waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));

    static {
        WIREMOCK.start();
    }

    protected static String wireMockUrl() {
        return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
    }
}
