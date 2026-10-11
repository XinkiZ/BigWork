package com.wylie.registration.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "professor")
@Getter
@Setter
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String professorNo;

    @Column(nullable = false, length = 32)
    private String name;

    private LocalDate birthDate;

    private String ssn;

    /** ACTIVE / LEAVE / RETIRED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    private String department;

    @Column(nullable = false, length = 100)
    private String passwordHash;
}