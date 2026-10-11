package com.wylie.registration.service;

import com.wylie.registration.domain.CourseOffering;
import com.wylie.registration.legacy.CatalogSnapshot;
import com.wylie.registration.legacy.LegacyCourse;
import com.wylie.registration.web.dto.CourseOfferingView;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** 视图装配工具 */
@Component
public class ViewAssembler {

    private static final String[] DAYS = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static String dayText(int dayOfWeek) {
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? DAYS[dayOfWeek] : "未知";
    }

    public CourseOfferingView offering(CourseOffering o, long enrolledCount, String professorName,
                                       CatalogSnapshot snapshot) {
        List<String> prereqTitles = List.of();
        if (snapshot != null) {
            prereqTitles = snapshot.prerequisitesOf(o.getCourseNo()).stream()
                    .map(no -> {
                        LegacyCourse c = snapshot.courses().get(no);
                        return c == null ? no : no + " " + c.title();
                    })
                    .sorted()
                    .toList();
        }
        String start = o.getStartTime().format(TIME);
        String end = o.getEndTime().format(TIME);
        boolean full = enrolledCount >= o.getCapacity();
        return new CourseOfferingView(
                o.getId(), o.getCourseNo(), o.getTitle(), o.getCredits(), o.getDepartment(),
                o.getProfessorId(), professorName, o.getDayOfWeek(), dayText(o.getDayOfWeek()),
                start, end, o.getRoom(), o.getCapacity(), enrolledCount, full, o.getStatus().name(),
                prereqTitles, dayText(o.getDayOfWeek()) + " " + start + "-" + end);
    }
}