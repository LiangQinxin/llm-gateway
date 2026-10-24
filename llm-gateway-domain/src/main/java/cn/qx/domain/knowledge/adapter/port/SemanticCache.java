package cn.qx.domain.knowledge.adapter.port;

import java.util.Optional;

/**
 * 语义缓存端口占位（ADR-0004）。本期不接入 Redis 实现。
 */
public interface SemanticCache {

    /** 读取缓存值，未命中返回 Optional.empty()。 */
    Optional<String> get(String key);

    /** 写入缓存并设置过期时间（秒）。 */
    void put(String key, String value, long ttlSeconds);

    /** 淘汰指定 key 的缓存。 */
    void evict(String key);
}
