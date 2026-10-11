package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.domain.Professor;
import com.wylie.registration.domain.Student;
import com.wylie.registration.repo.GradeRepository;
import com.wylie.registration.repo.ProfessorRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.web.dto.ProfessorDto;
import com.wylie.registration.web.dto.ProfessorUpsertRequest;
import com.wylie.registration.web.dto.StudentDto;
import com.wylie.registration.web.dto.StudentUpsertRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 注册管理员侧用例：维护学生信息、维护教授信息（唯一 ID，新增时由系统生成） */
@Service
public class RegistrarService {

    /** 新建账号的初始口令 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final StudentRepository studentRepo;
    private final ProfessorRepository professorRepo;
    private final GradeRepository gradeRepo;
    private final PasswordEncoder passwordEncoder;

    public RegistrarService(StudentRepository studentRepo, ProfessorRepository professorRepo,
                            GradeRepository gradeRepo, PasswordEncoder passwordEncoder) {
        this.studentRepo = studentRepo;
        this.professorRepo = professorRepo;
        this.gradeRepo = gradeRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------
    // 学生信息
    // ------------------------------------------------------------------

    public List<StudentDto> listStudents(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return studentRepo.findAll().stream()
                .filter(s -> kw.isEmpty()
                        || s.getStudentNo().toLowerCase(Locale.ROOT).contains(kw)
                        || s.getName().toLowerCase(Locale.ROOT).contains(kw))
                .sorted(Comparator.comparing(Student::getStudentNo))
                .map(this::toDto).toList();
    }

    @Transactional
    public StudentDto createStudent(StudentUpsertRequest request) {
        Integer max = studentRepo.findMaxStudentNo();
        long next = max == null ? 2026001L : max + 1L;
        Student student = new Student();
        student.setStudentNo(String.format("%07d", next));
        apply(student, request);
        student.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        return toDto(studentRepo.save(student));
    }

    @Transactional
    public StudentDto updateStudent(Long id, StudentUpsertRequest request) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学生不存在，请检查学生ID"));
        apply(student, request);
        return toDto(studentRepo.save(student));
    }

    @Transactional
    public void deleteStudent(Long id) {
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "学生不存在，请检查学生ID"));
        if (gradeRepo.existsByStudentId(id)) {
            throw new BusinessException(ErrorCodes.STATE_ERROR,
                    "学生【" + student.getName() + "】存在历史成绩记录，不能删除，可改为将其状态设为 GRADUATED/LEAVE");
        }
        studentRepo.delete(student);
    }

    // ------------------------------------------------------------------
    // 教授信息
    // ------------------------------------------------------------------

    public List<ProfessorDto> listProfessors(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return professorRepo.findAll().stream()
                .filter(p -> kw.isEmpty()
                        || p.getProfessorNo().toLowerCase(Locale.ROOT).contains(kw)
                        || p.getName().toLowerCase(Locale.ROOT).contains(kw))
                .sorted(Comparator.comparing(Professor::getProfessorNo))
                .map(this::toDto).toList();
    }

    @Transactional
    public ProfessorDto createProfessor(ProfessorUpsertRequest request) {
        int next = professorRepo.findAll().stream()
                .map(Professor::getProfessorNo)
                .mapToInt(no -> {
                    try {
                        return Integer.parseInt(no.replaceAll("\\D", ""));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max().orElse(0) + 1;
        Professor professor = new Professor();
        professor.setProfessorNo(String.format("P%03d", next));
        apply(professor, request);
        professor.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        return toDto(professorRepo.save(professor));
    }

    @Transactional
    public ProfessorDto updateProfessor(Long id, ProfessorUpsertRequest request) {
        Professor professor = professorRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "教授不存在，请检查教授ID"));
        apply(professor, request);
        return toDto(professorRepo.save(professor));
    }

    @Transactional
    public void deleteProfessor(Long id) {
        Professor professor = professorRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "教授不存在，请检查教授ID"));
        professorRepo.delete(professor);
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void apply(Student student, StudentUpsertRequest request) {
        student.setName(request.name().trim());
        student.setBirthDate(request.birthDate());
        student.setSsn(request.ssn());
        student.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT));
        student.setGraduationDate(request.graduationDate());
    }

    private void apply(Professor professor, ProfessorUpsertRequest request) {
        professor.setName(request.name().trim());
        professor.setBirthDate(request.birthDate());
        professor.setSsn(request.ssn());
        professor.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase(Locale.ROOT));
        professor.setDepartment(request.department());
    }

    private StudentDto toDto(Student s) {
        return new StudentDto(s.getId(), s.getStudentNo(), s.getName(), s.getBirthDate(), s.getSsn(),
                s.getStatus(), s.getGraduationDate());
    }

    private ProfessorDto toDto(Professor p) {
        return new ProfessorDto(p.getId(), p.getProfessorNo(), p.getName(), p.getBirthDate(), p.getSsn(),
                p.getStatus(), p.getDepartment());
    }
}