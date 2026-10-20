package cn.qx.domain.knowledge.adapter.port;

import java.util.Optional;

/**
 * 语义缓存端口占位（ADR-0004）。本期不接入 Redis 实现。
 */
public interface SemanticCache {

    Optional<String> get(String key);

    void put(String key, String value, long ttlSeconds);

    void evict(String key);
}
