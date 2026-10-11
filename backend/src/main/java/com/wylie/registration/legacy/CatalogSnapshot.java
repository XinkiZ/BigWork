package com.wylie.registration.legacy;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 中间层缓存的一次课程目录快照。
 *
 * @param courses        课程号 -> 课程
 * @param prerequisites  课程号 -> 先修课程号集合
 * @param qualifications 教授编号 -> 具备授课资格的课程号集合
 * @param fetchedAt      快照拉取时间
 * @param loadMillis     拉取耗时（毫秒，含模拟延迟）
 */
public record CatalogSnapshot(
        Map<String, LegacyCourse> courses,
        Map<String, Set<String>> prerequisites,
        Map<String, Set<String>> qualifications,
        Instant fetchedAt,
        long loadMillis) {

    public Set<String> prerequisitesOf(String courseNo) {
        return prerequisites.getOrDefault(courseNo, Collections.emptySet());
    }

    public Set<String> coursesQualifiedFor(String professorNo) {
        return qualifications.getOrDefault(professorNo, Collections.emptySet());
    }

    public Set<String> professorsQualifiedFor(String courseNo) {
        return qualifications.entrySet().stream()
                .filter(e -> e.getValue().contains(courseNo))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }
}