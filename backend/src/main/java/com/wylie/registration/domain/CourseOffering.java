package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "course_offering")
@Getter
@Setter
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long semesterId;

    /** 课程号（来自遗留课程目录，只读引用） */
    @Column(nullable = false, length = 16)
    private String courseNo;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(nullable = false)
    private Integer credits;

    private String department;

    /** 授课教授，null 表示尚未分配 */
    private Long professorId;

    /** 1=周一 … 7=周日 */
    @Column(nullable = false)
    private Integer dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private String room;

    /** 容量上限（题目规则：最多 10 人） */
    @Column(nullable = false)
    private Integer capacity = 10;

    /** 最少开课人数（题目规则：不足 3 人取消） */
    @Column(nullable = false)
    private Integer minStudents = 3;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferingStatus status = OfferingStatus.PROPOSED;
}