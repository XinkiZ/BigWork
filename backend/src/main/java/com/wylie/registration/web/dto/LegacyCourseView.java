package com.wylie.registration.web.dto;

import java.util.List;

/** 遗留课程目录条目（供注册管理员开班选择） */
public record LegacyCourseView(String courseNo, String title, int credits, String department,
                               List<String> prerequisiteTitles) {
}