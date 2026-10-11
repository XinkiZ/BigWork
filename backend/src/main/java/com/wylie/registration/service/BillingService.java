package com.wylie.registration.service;

import com.wylie.registration.common.BusinessException;
import com.wylie.registration.common.ErrorCodes;
import com.wylie.registration.config.BillingProperties;
import com.wylie.registration.config.TuitionProperties;
import com.wylie.registration.domain.BillingStatus;
import com.wylie.registration.domain.BillingTransaction;
import com.wylie.registration.domain.BillingTxType;
import com.wylie.registration.domain.Semester;
import com.wylie.registration.domain.Student;
import com.wylie.registration.repo.BillingTransactionRepository;
import com.wylie.registration.repo.SemesterRepository;
import com.wylie.registration.repo.StudentRepository;
import com.wylie.registration.web.dto.BillingTransactionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计费服务：学费计算、账单交易登记与失败重试。
 * 交易登记与发送解耦——登记成功后若计费系统不可用，仅将交易标记为 FAILED，
 * 由定时任务按固定周期重试，直至发送成功（对应需求"计费系统不可用：按固定周期重试"）。
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final BillingTransactionRepository txRepo;
    private final StudentRepository studentRepo;
    private final SemesterRepository semesterRepo;
    private final BillingSystemClient client;
    private final TuitionProperties tuition;

    public BillingService(BillingTransactionRepository txRepo, StudentRepository studentRepo,
                          SemesterRepository semesterRepo, BillingSystemClient client,
                          TuitionProperties tuition) {
        this.txRepo = txRepo;
        this.studentRepo = studentRepo;
        this.semesterRepo = semesterRepo;
        this.client = client;
        this.tuition = tuition;
    }

    /** 学分学费 = 学分 × 每学分单价 */
    public BigDecimal tuitionFor(int credits) {
        return tuition.getPerCredit().multiply(BigDecimal.valueOf(credits));
    }

    @Transactional
    public BillingTransaction create(BillingTxType type, Long studentId, Long semesterId,
                                     BigDecimal amount, String description) {
        BillingTransaction tx = new BillingTransaction();
        tx.setStudentId(studentId);
        tx.setSemesterId(semesterId);
        tx.setAmount(amount);
        tx.setTxType(type);
        tx.setStatus(BillingStatus.PENDING);
        tx.setRetryCount(0);
        tx.setDescription(description);
        return txRepo.save(tx);
    }

    /** 逐笔尝试发送（单笔独立事务：发送失败不影响其他交易） */
    public boolean attemptSend(Long txId) {
        BillingTransaction tx = txRepo.findById(txId).orElse(null);
        if (tx == null || tx.getStatus() == BillingStatus.SENT) {
            return tx != null;
        }
        Student student = studentRepo.findById(tx.getStudentId()).orElse(null);
        Semester semester = semesterRepo.findById(tx.getSemesterId()).orElse(null);
        tx.setLastAttemptAt(LocalDateTime.now());
        try {
            client.send(student == null ? "?" : student.getStudentNo(),
                    semester == null ? "?" : semester.getName(), tx.getAmount());
            tx.setStatus(BillingStatus.SENT);
            txRepo.save(tx);
            return true;
        } catch (BusinessException e) {
            tx.setStatus(BillingStatus.FAILED);
            tx.setRetryCount(tx.getRetryCount() + 1);
            txRepo.save(tx);
            return false;
        }
    }

    /** 尝试发送所有待处理/失败的交易，返回本轮成功笔数 */
    public int flushPending() {
        List<BillingTransaction> pending = txRepo.findByStatusInOrderByIdAsc(
                List.of(BillingStatus.PENDING, BillingStatus.FAILED));
        int sent = 0;
        for (BillingTransaction tx : pending) {
            if (attemptSend(tx.getId())) {
                sent++;
            }
        }
        return sent;
    }

    /** 定时重试：固定周期扫描 FAILED/PENDING 交易 */
    @Scheduled(fixedDelayString = "#{${billing.retry-interval-seconds:30} * 1000}")
    public void scheduledRetry() {
        List<BillingTransaction> pending = txRepo.findByStatusInOrderByIdAsc(
                List.of(BillingStatus.PENDING, BillingStatus.FAILED));
        if (pending.isEmpty()) {
            return;
        }
        int sent = flushPending();
        if (sent > 0) {
            log.info("计费重试周期执行完成：成功发送 {} 笔，剩余 {} 笔待重试", sent, pending.size() - sent);
        }
    }

    /** 手动触发重试（演示/运维入口） */
    public int retryNow() {
        return flushPending();
    }

    public List<BillingTransactionView> listAll(Long semesterId) {
        List<BillingTransaction> transactions = semesterId == null
                ? txRepo.findAllByOrderByIdDesc()
                : txRepo.findBySemesterIdOrderByIdAsc(semesterId);
        return toViews(transactions);
    }

    /** 学生查看本人账单（可按学期筛选） */
    public List<BillingTransactionView> listForStudent(Long studentId, Long semesterId) {
        List<BillingTransaction> transactions = semesterId == null
                ? txRepo.findByStudentIdOrderByIdDesc(studentId)
                : txRepo.findByStudentIdAndSemesterIdOrderByIdDesc(studentId, semesterId);
        return toViews(transactions);
    }

    private List<BillingTransactionView> toViews(List<BillingTransaction> transactions) {
        Map<Long, Student> students = studentRepo.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<Long, Semester> semesters = semesterRepo.findAll().stream()
                .collect(Collectors.toMap(Semester::getId, Function.identity()));
        return transactions.stream().map(tx -> {
            Student s = students.get(tx.getStudentId());
            Semester sem = semesters.get(tx.getSemesterId());
            return new BillingTransactionView(tx.getId(),
                    s == null ? "?" : s.getStudentNo(), s == null ? "?" : s.getName(),
                    sem == null ? "?" : sem.getName(), tx.getAmount(), tx.getTxType().name(),
                    tx.getStatus().name(), tx.getRetryCount(),
                    tx.getLastAttemptAt() == null ? null : tx.getLastAttemptAt().toString(),
                    tx.getDescription());
        }).toList();
    }
}