package com.wylie.registration.legacy;

import com.wylie.registration.config.LegacyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 遗留课程目录适配器。
 *
 * <p>模拟通过 Unix 服务器上的开放 SQL 接口访问 DEC VAX/Ingres 遗留库：
 * 延迟高、仅可读取（使用只读账号 legacy_ro）。中间层缓存通过
 * {@link CatalogService} 提供，本适配器只负责"直连遗留库"这一最慢路径。
 */
@Component
public class LegacyCatalogAdapter {

    private static final Logger log = LoggerFactory.getLogger(LegacyCatalogAdapter.class);

    private final JdbcTemplate jdbcTemplate;
    private final LegacyProperties props;

    public LegacyCatalogAdapter(@Qualifier("legacyJdbcTemplate") JdbcTemplate jdbcTemplate,
                                LegacyProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    /** 直连遗留库拉取完整目录快照（含模拟的高延迟） */
    public CatalogSnapshot fetchSnapshot() {
        if (!props.isAvailable()) {
            throw new LegacyUnavailableException();
        }
        long start = System.currentTimeMillis();
        simulateLegacyLatency();

        Map<String, LegacyCourse> courses = new HashMap<>();
        jdbcTemplate.query(
                "SELECT COURSE_NO, COURSE_TITLE, CREDIT_HRS, DEPT_CODE FROM COURSE_TBL",
                rs -> {
                    courses.put(rs.getString("COURSE_NO"),
                            new LegacyCourse(rs.getString("COURSE_NO"),
                                    rs.getString("COURSE_TITLE"),
                                    rs.getInt("CREDIT_HRS"),
                                    rs.getString("DEPT_CODE")));
                });

        Map<String, Set<String>> prereqs = new HashMap<>();
        jdbcTemplate.query("SELECT COURSE_NO, PREREQ_NO FROM PREREQ_TBL",
                rs -> {
                    prereqs.computeIfAbsent(rs.getString("COURSE_NO"), k -> new HashSet<>())
                            .add(rs.getString("PREREQ_NO"));
                });

        Map<String, Set<String>> qualifications = new HashMap<>();
        jdbcTemplate.query("SELECT PROF_NO, COURSE_NO FROM QUALIFY_TBL",
                rs -> {
                    qualifications.computeIfAbsent(rs.getString("PROF_NO"), k -> new HashSet<>())
                            .add(rs.getString("COURSE_NO"));
                });

        long cost = System.currentTimeMillis() - start;
        log.info("已从遗留课程目录库拉取快照：课程 {} 门，耗时 {} ms", courses.size(), cost);
        return new CatalogSnapshot(courses, prereqs, qualifications, Instant.now(), cost);
    }

    private void simulateLegacyLatency() {
        long latency = props.getSimulatedLatencyMs();
        if (latency <= 0) {
            return;
        }
        try {
            Thread.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}