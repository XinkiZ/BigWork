package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "semester")
@Getter
@Setter
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RegistrationStatus registrationStatus = RegistrationStatus.OPEN;

    /** 选课调整期开始日期 */
    private LocalDate adjustmentStart;

    /** 选课调整期结束日期 */
    private LocalDate adjustmentEnd;

    /** 是否当前学期 */
    private Boolean currentFlag = false;
}