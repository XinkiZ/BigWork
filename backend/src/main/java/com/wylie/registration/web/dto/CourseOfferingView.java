package com.wylie.registration.web.dto;

import java.util.List;

/** 课程班视图（供选课目录/授课列表复用） */
public record CourseOfferingView(
        Long offeringId,
        String courseNo,
        String title,
        int credits,
        String department,
        Long professorId,
        String professorName,
        int dayOfWeek,
        String dayText,
        String startTime,
        String endTime,
        String room,
        int capacity,
        long enrolledCount,
        boolean full,
        String status,
        List<PrerequisiteView> prerequisites,
        String timeText) {
}