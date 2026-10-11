package com.wylie.registration.web.dto;

/** 课表提交行（主选/备选） */
public record ScheduleLine(Long offeringId, String itemType) {
}