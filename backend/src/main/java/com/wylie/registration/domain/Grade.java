package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "grade")
@Getter
@Setter
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long offeringId;

    @Column(nullable = false)
    private Long studentId;

    /** 录入教授 */
    private Long professorId;

    /** A/B/C/D/F/I，null 表示未录入 */
    @Column(length = 1)
    private String gradeValue;

    private LocalDateTime updatedAt;
}