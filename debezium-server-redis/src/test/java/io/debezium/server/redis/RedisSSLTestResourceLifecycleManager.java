/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.server.redis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;

import io.debezium.server.Images;
import io.debezium.server.TestConfigSource;
import io.debezium.util.Testing;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class RedisSSLTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

    public static final int REDIS_PORT = 6379;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final GenericContainer<?> container = new GenericContainer<>(Images.REDIS_IMAGE)
            .withClasspathResourceMapping("ssl", "/etc/certificates", BindMode.READ_ONLY)
            .withCommand(
                    "redis-server --tls-port 6379 --port 0 --tls-cert-file /etc/certificates/redis.crt --tls-key-file /etc/certificates/redis.key --tls-ca-cert-file /etc/certificates/ca.crt")
            .withExposedPorts(REDIS_PORT);

    private static synchronized void start(boolean ignored) {
        if (!running.get()) {
            container.start();
            TestUtils.waitBoolean(() -> container.getLogs().contains(RedisTestResourceLifecycleManager.READY_MESSAGE));
            running.set(true);
        }
    }

    @Override
    public Map<String, String> start() {
        start(true);
        Testing.Files.delete(TestConfigSource.OFFSET_STORE_PATH);
        Testing.Files.createTestingFile(TestConfigSource.OFFSET_STORE_PATH);

        Map<String, String> params = new ConcurrentHashMap<>();
        params.put("debezium.sink.type", "redis");
        params.put("debezium.source.offset.storage.redis.address", RedisSSLTestResourceLifecycleManager.getRedisContainerAddress());
        params.put("debezium.source.offset.storage.redis.ssl.enabled", "true");
        params.put("debezium.sink.redis.address", RedisSSLTestResourceLifecycleManager.getRedisContainerAddress());
        params.put("debezium.sink.redis.ssl.enabled", "true");
        params.put("debezium.source.connector.class", "io.debezium.connector.postgresql.PostgresConnector");
        params.put("debezium.source.offset.flush.interval.ms", "0");
        params.put("debezium.source.topic.prefix", "testc");
        params.put("debezium.source.schema.include.list", "inventory");
        params.put("debezium.source.table.include.list", "inventory.customers,inventory.redis_test,inventory.redis_test2");

        return params;
    }

    @Override
    public void stop() {
        try {
            container.stop();
        }
        catch (Exception e) {
            // ignored
        }
        running.set(false);
    }

    public static void pause() {
        container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec();
    }

    public static void unpause() {
        container.getDockerClient().unpauseContainerCmd(container.getContainerId()).exec();
    }

    public static String getRedisContainerAddress() {
        start(true);

        return String.format("%s:%d", container.getContainerIpAddress(), container.getFirstMappedPort());
    }
}
