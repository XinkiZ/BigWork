package com.wylie.registration.legacy;

/** 遗留课程目录中的课程（只读） */
public record LegacyCourse(String courseNo, String title, int credits, String department) {
}