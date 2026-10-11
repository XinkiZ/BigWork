package com.wylie.registration.web.dto;

/** 先修课程视图：passed 仅学生场景填充（true=已通过，null=不展示达成情况） */
public record PrerequisiteView(String courseNo, String title, Boolean passed) {
}