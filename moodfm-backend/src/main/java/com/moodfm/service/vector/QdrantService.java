package com.moodfm.service.vector;

import com.moodfm.service.embedding.EmbeddingService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QdrantService {

    @Autowired(required = false)
    private QdrantClient qdrantClient;

    /** EmbeddingService injected for startup dimension validation (only used when Qdrant is enabled). */
    @Autowired
    private EmbeddingService embeddingService;

    private static final String COLLECTION = "songs";
    private static final int SEARCH_DEFAULT_LIMIT = 20;

    /** Configurable embedding dimension; default 2048 to preserve existing behavior. */
    @Value("${embedding.dim:2048}")
    private int vectorDim;

    @PostConstruct
    public void init() {
        if (qdrantClient == null) {
            log.warn("Qdrant client is null. Vector search will be unavailable.");
            return;
        }
        try {
            // Startup dimension validation: probe actual embedding output vs configured dim.
            // Only runs when Qdrant is enabled to avoid spurious API calls in disabled mode.
            int actual = embeddingService.embed("维度校验探针").length;
            if (actual != vectorDim) {
                log.error(
                        "Embedding dimension mismatch: configured embedding.dim={} but actual model output dim={}. " +
                        "Update embedding.dim to {} or switch to a {}-dim model.",
                        vectorDim, actual, actual, vectorDim);
                throw new IllegalStateException(
                        "Embedding dimension mismatch: configured=" + vectorDim + ", actual=" + actual);
            }
            log.info("Embedding dimension validated: dim={}", vectorDim);

            ensureCollection();
        } catch (IllegalStateException e) {
            throw e; // re-throw fail-fast error
        } catch (Exception e) {
            log.warn("Qdrant not available at startup ({}). Vector recall will be skipped until it comes online.",
                    e.getMessage());
        }
    }

    /**
     * Ensure the "songs" collection exists. Creates it with cosine distance if missing.
     */
    private void ensureCollection() throws Exception {
        // Check existence via collectionExistsAsync
        boolean exists = qdrantClient.collectionExistsAsync(COLLECTION).get();

        if (!exists) {
            VectorParams vectorParams = VectorParams.newBuilder()
                    .setDistance(Distance.Cosine)
                    .setSize(vectorDim)
                    .build();

            qdrantClient.createCollectionAsync(COLLECTION, vectorParams).get();

            log.info("Created Qdrant collection '{}' (dim={}, distance=cosine)", COLLECTION, vectorDim);
        }
    }

    /**
     * Upsert a song vector with metadata into Qdrant.
     * Uses a deterministic UUID derived from the song ID.
     */
    public void upsertSong(Long songId, float[] embedding, Map<String, Object> metadata) {
        if (qdrantClient == null) {
            log.debug("Qdrant client unavailable, skipping upsert for song {}", songId);
            return;
        }
        try {
            PointId pointId = PointId.newBuilder()
                    .setUuid(songIdToUuid(songId).toString())
                    .build();

            // Build vector
            Vector vector = Vector.newBuilder()
                    .addAllData(toFloatList(embedding))
                    .build();

            Vectors vectors = Vectors.newBuilder()
                    .setVector(vector)
                    .build();

            // Build payload
            Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    io.qdrant.client.grpc.JsonWithInt.Value val = toQdrantValue(entry.getValue());
                    if (val != null) {
                        payload.put(entry.getKey(), val);
                    }
                }
            }

            PointStruct point = PointStruct.newBuilder()
                    .setId(pointId)
                    .setVectors(vectors)
                    .putAllPayload(payload)
                    .build();

            qdrantClient.upsertAsync(COLLECTION, List.of(point)).get();
            log.debug("Upserted song {} to Qdrant", songId);
        } catch (Exception e) {
            log.warn("Failed to upsert song {} to Qdrant: {}", songId, e.getMessage());
        }
    }

    /**
     * Search for similar songs by vector similarity.
     * Returns a list of song IDs, ordered by descending similarity.
     * Falls back to an empty list if Qdrant is unavailable.
     */
    public List<Long> searchSimilar(float[] queryVector, int limit) {
        return searchSimilar(queryVector, limit, null);
    }

    /**
     * Search for similar songs with optional filter (e.g., exclude certain IDs).
     */
    public List<Long> searchSimilar(float[] queryVector, int limit, Filter filter) {
        if (qdrantClient == null) {
            log.debug("Qdrant client unavailable, returning empty results");
            return List.of();
        }
        try {
            SearchPoints.Builder searchBuilder = SearchPoints.newBuilder()
                    .setCollectionName(COLLECTION)
                    .addAllVector(toFloatList(queryVector))
                    .setLimit(limit > 0 ? limit : SEARCH_DEFAULT_LIMIT)
                    .setWithPayload(
                            WithPayloadSelector.newBuilder()
                                    .setEnable(true)
                                    .build()
                    );

            if (filter != null) {
                searchBuilder.setFilter(filter);
            }

            List<ScoredPoint> results = qdrantClient
                    .searchAsync(searchBuilder.build())
                    .get();

            List<Long> songIds = new ArrayList<>();
            for (ScoredPoint point : results) {
                Long songId = extractSongIdFromPayload(point);
                if (songId != null) {
                    songIds.add(songId);
                }
            }

            // Deduplicate (shouldn't happen, but safe)
            return songIds.stream().distinct().collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Qdrant search failed, returning empty results: {}", e.getMessage());
            return List.of();
        }
    }

    // ===================== Helpers =====================

    /**
     * Deterministic UUID from song ID (uses nameUUIDFromBytes for consistency).
     */
    private UUID songIdToUuid(Long songId) {
        byte[] bytes = new byte[16];
        long id = songId;
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (id & 0xFF);
            id >>= 8;
        }
        // Fill remaining bytes with a pattern derived from the ID
        long hash = songId * 2654435761L; // Knuth multiplicative hash
        for (int i = 8; i < 16; i++) {
            bytes[i] = (byte) (hash & 0xFF);
            hash >>= 8;
        }
        return UUID.nameUUIDFromBytes(bytes);
    }

    /**
     * Extract the song ID from a scored point's payload.
     */
    private Long extractSongIdFromPayload(ScoredPoint point) {
        try {
            io.qdrant.client.grpc.JsonWithInt.Value songIdVal = point.getPayloadMap().get("songId");
            if (songIdVal != null && songIdVal.hasIntegerValue()) {
                return songIdVal.getIntegerValue();
            }
        } catch (Exception e) {
            log.debug("Could not extract songId from Qdrant point", e);
        }
        return null;
    }

    /**
     * Convert float[] to List<Float> for the gRPC protobuf API.
     */
    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    /**
     * Convert a Java Object to a Qdrant Value.
     */
    private io.qdrant.client.grpc.JsonWithInt.Value toQdrantValue(Object obj) {
        if (obj instanceof String s) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setStringValue(s).build();
        } else if (obj instanceof Long l) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setIntegerValue(l).build();
        } else if (obj instanceof Integer i) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setIntegerValue(i).build();
        } else if (obj instanceof Number n) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setDoubleValue(n.doubleValue()).build();
        } else if (obj instanceof Boolean b) {
            return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setBoolValue(b).build();
        }
        // Fallback: convert to string
        return io.qdrant.client.grpc.JsonWithInt.Value.newBuilder().setStringValue(String.valueOf(obj)).build();
    }
}
