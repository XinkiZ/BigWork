package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "schedule_item")
@Getter
@Setter
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private Long offeringId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleItemType itemType;

    /** 主选 1-4；备选 1-2（按顺序启用） */
    @Column(nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleItemStatus status = ScheduleItemStatus.SELECTED;

    /** 是否由备选补位启用 */
    @Column(nullable = false)
    private Boolean promoted = false;
}