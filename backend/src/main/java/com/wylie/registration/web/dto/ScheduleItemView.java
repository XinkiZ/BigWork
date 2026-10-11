package com.wylie.registration.web.dto;

public record ScheduleItemView(Long itemId, String itemType, int sortOrder, String status, boolean promoted,
                               CourseOfferingView offering) {
}