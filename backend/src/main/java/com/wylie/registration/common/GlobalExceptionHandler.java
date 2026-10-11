package com.wylie.registration.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：把业务异常与框架异常统一转换成 {code, message} 错误体 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(resolveStatus(ex)).body(new ApiError(ex.getCode(), ex.getMessage()));
    }

    /**
     * 业务异常的状态码：显式指定（401/403/503 等）优先，
     * 否则按错误码推断语义化状态（不存在→404、状态冲突→409、不可用→503、其余→400）。
     */
    private HttpStatus resolveStatus(BusinessException ex) {
        if (ex.getStatus() != HttpStatus.BAD_REQUEST) {
            return ex.getStatus();
        }
        return switch (ex.getCode()) {
            case ErrorCodes.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCodes.FORBIDDEN -> HttpStatus.FORBIDDEN;
            case ErrorCodes.NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ErrorCodes.METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case ErrorCodes.STATE_ERROR, ErrorCodes.REGISTRATION_CLOSED, ErrorCodes.PREREQUISITE_NOT_MET,
                 ErrorCodes.COURSE_FULL, ErrorCodes.TIME_CONFLICT, ErrorCodes.DUPLICATE_COURSE -> HttpStatus.CONFLICT;
            case ErrorCodes.CATALOG_UNAVAILABLE, ErrorCodes.BILLING_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ApiError(ErrorCodes.VALIDATION, msg));
    }

    /** 请求路径不存在（例如调用了未实现的子路径）：返回 404 而不是 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ErrorCodes.NOT_FOUND, "请求的接口不存在：" + ex.getResourcePath()));
    }

    /** 请求方法不支持（例如对只支持 PUT 的接口发起 GET）：返回 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError(ErrorCodes.METHOD_NOT_ALLOWED, "该接口不支持 " + ex.getMethod() + " 请求方法"));
    }

    /** 路径变量/查询参数类型不匹配（例如 ID 传入非数字）：返回 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCodes.VALIDATION, "参数 " + ex.getName() + " 格式不正确"));
    }

    /** 请求体缺失或 JSON 格式错误：返回 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError(ErrorCodes.VALIDATION, "请求体缺失或 JSON 格式不正确"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError(ErrorCodes.FORBIDDEN, "当前账号无权执行该操作"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError(ErrorCodes.UNAUTHORIZED, "登录状态无效，请重新登录"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(ErrorCodes.INTERNAL, "系统内部错误：" + ex.getMessage()));
    }
}