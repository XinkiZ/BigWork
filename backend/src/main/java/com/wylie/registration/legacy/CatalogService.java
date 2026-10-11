package com.wylie.registration.legacy;

import com.wylie.registration.config.CacheConfig;
import com.wylie.registration.config.LegacyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 课程目录中间层服务（方案 A 核心）。
 *
 * <ul>
 *   <li>direct 模式：每次请求直连遗留库（约 8 秒，满足"≤10 秒"底线）</li>
 *   <li>cached 模式：优先读取 Caffeine 缓存（毫秒级），未命中才回源遗留库</li>
 * </ul>
 * 提供运行期模式切换、缓存刷新与状态查询，供演示与性能对比使用。
 */
@Service
public class CatalogService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final String CACHE_KEY = "snapshot";

    private final LegacyCatalogAdapter adapter;
    private final LegacyProperties props;
    private final Cache cache;

    public CatalogService(LegacyCatalogAdapter adapter, LegacyProperties props, CacheManager cacheManager) {
        this.adapter = adapter;
        this.props = props;
        this.cache = cacheManager.getCache(CacheConfig.CATALOG_CACHE);
    }

    /** 启动预热：cached 模式下预先拉取一次目录，避免首次访问等待遗留库 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            if (isCached() && props.isAvailable()) {
                CatalogSnapshot snapshot = loadAndStore();
                log.info("课程目录缓存预热完成，共 {} 门课程，耗时 {} ms",
                        snapshot.courses().size(), snapshot.loadMillis());
            }
        } catch (Exception e) {
            log.warn("课程目录缓存预热失败（不阻塞启动）：{}", e.getMessage());
        }
    }

    public boolean isCached() {
        return "cached".equalsIgnoreCase(props.getMode());
    }

    /** 获取目录快照：cached 模式优先走缓存；direct 模式每次直连遗留库 */
    public CatalogSnapshot getCatalog() {
        if (!props.isAvailable()) {
            throw new LegacyUnavailableException();
        }
        if (!isCached()) {
            return adapter.fetchSnapshot();
        }
        CatalogSnapshot snapshot = cache.get(CACHE_KEY, CatalogSnapshot.class);
        if (snapshot != null) {
            return snapshot;
        }
        return loadAndStore();
    }

    /** 强制刷新缓存（清空后回源遗留库） */
    public CatalogSnapshot refresh() {
        if (!props.isAvailable()) {
            throw new LegacyUnavailableException();
        }
        cache.clear();
        return loadAndStore();
    }

    /** 缓存状态（供演示面板展示） */
    public Map<String, Object> status() {
        CatalogSnapshot snapshot = cache.get(CACHE_KEY, CatalogSnapshot.class);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", props.getMode());
        status.put("available", props.isAvailable());
        status.put("simulatedLatencyMs", props.getSimulatedLatencyMs());
        status.put("cacheTtlSeconds", props.getCacheTtlSeconds());
        status.put("cached", snapshot != null);
        status.put("courseCount", snapshot == null ? 0 : snapshot.courses().size());
        status.put("fetchedAt", snapshot == null ? null : snapshot.fetchedAt().toString());
        status.put("loadMillis", snapshot == null ? null : snapshot.loadMillis());
        return status;
    }

    private CatalogSnapshot loadAndStore() {
        CatalogSnapshot snapshot = adapter.fetchSnapshot();
        cache.put(CACHE_KEY, snapshot);
        return snapshot;
    }
}