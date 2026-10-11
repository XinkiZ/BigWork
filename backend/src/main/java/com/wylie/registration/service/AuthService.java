package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.Professor;
import com.wylie.registration.domain.Registrar;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.domain.UserRole;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.repo.RegistrarRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.security.AuthUser;
import com.wylie.registration.security.JwtService;
import com.wylie.registration.web.dto.LoginRequest;
import com.wylie.registration.web.dto.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 登录认证（三类用户自动识别：学号 / 教授编号 / 管理员用户名） */
@Service
public class AuthService {

    private final StudentRepository studentRepo;
    private final ProfessorRepository professorRepo;
    private final RegistrarRepository registrarRepo;
    private final SemesterRepository semesterRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(StudentRepository studentRepo, ProfessorRepository professorRepo,
                       RegistrarRepository registrarRepo, SemesterRepository semesterRepo,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.studentRepo = studentRepo;
        this.professorRepo = professorRepo;
        this.registrarRepo = registrarRepo;
        this.semesterRepo = semesterRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        AuthUser user = resolve(username, request.password());

        Semester current = semesterRepo.findByCurrentFlagTrue().orElse(null);
        return new LoginResponse(
                jwtService.generate(user), user.role().name(), user.id(), user.name(), user.username(),
                current == null ? null : current.getId(),
                current == null ? null : current.getName(),
                current == null ? null : current.getRegistrationStatus().name());
    }

    private AuthUser resolve(String username, String rawPassword) {
        Optional<Student> student = studentRepo.findByStudentNo(username);
        if (student.isPresent()) {
            return verify(student.get().getPasswordHash(), rawPassword,
                    new AuthUser(student.get().getId(), UserRole.STUDENT, student.get().getName(), username));
        }
        Optional<Professor> professor = professorRepo.findByProfessorNo(username);
        if (professor.isPresent()) {
            return verify(professor.get().getPasswordHash(), rawPassword,
                    new AuthUser(professor.get().getId(), UserRole.PROFESSOR, professor.get().getName(), username));
        }
        Optional<Registrar> registrar = registrarRepo.findByUsername(username);
        if (registrar.isPresent()) {
            return verify(registrar.get().getPasswordHash(), rawPassword,
                    new AuthUser(registrar.get().getId(), UserRole.REGISTRAR, registrar.get().getName(), username));
        }
        throw badCredentials();
    }

    private AuthUser verify(String hash, String rawPassword, AuthUser user) {
        if (!passwordEncoder.matches(rawPassword, hash)) {
            throw badCredentials();
        }
        return user;
    }

    private BusinessException badCredentials() {
        return new BusinessException(ErrorCodes.UNAUTHORIZED, "用户名或密码错误，请重新输入", HttpStatus.UNAUTHORIZED);
    }
}