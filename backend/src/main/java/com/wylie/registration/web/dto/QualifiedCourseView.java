package com.wylie.registration.web.dto;

import java.util.List;

/** 教授选授课页面：具备授课资格的课程及其本学期课程班 */
public record QualifiedCourseView(String courseNo, String title, int credits, String department,
                                  List<String> prerequisiteTitles, List<CourseOfferingView> offerings) {
}