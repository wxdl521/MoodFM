package com.moodfm.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.port:6334}")
    private int port;

    @Value("${qdrant.use-tls:false}")
    private boolean useTls;

    @Value("${qdrant.enabled:true}")
    private boolean enabled;

    @Bean
    public QdrantClient qdrantClient() {
        if (!enabled) {
            log.info("Qdrant disabled (qdrant.enabled=false). Vector search will be unavailable.");
            return null;
        }
        try {
            QdrantGrpcClient grpcClient = QdrantGrpcClient.newBuilder(host, port, useTls).build();
            QdrantClient client = new QdrantClient(grpcClient);
            log.info("Qdrant client initialized ({}:{})", host, port);
            return client;
        } catch (Exception e) {
            log.warn("Failed to create Qdrant client ({}:{}): {}. Vector search will be unavailable.",
                    host, port, e.getMessage());
            return null;
        }
    }
}
