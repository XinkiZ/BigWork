package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "student")
@Getter
@Setter
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String studentNo;

    @Column(nullable = false, length = 32)
    private String name;

    private LocalDate birthDate;

    /** 社保号 */
    private String ssn;

    /** ACTIVE / LEAVE / GRADUATED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    private LocalDate graduationDate;

    @Column(nullable = false, length = 100)
    private String passwordHash;
}