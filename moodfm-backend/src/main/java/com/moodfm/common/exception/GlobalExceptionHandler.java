package com.moodfm.common.exception;

import com.moodfm.common.result.R;
import com.moodfm.common.result.ResultCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletResponse response) {
        response.setStatus(e.getHttpStatus().value());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return R.fail(400, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(ResultCode.BAD_REQUEST, message);
    }

    // 凭证错误映射 400（而非 401）：401 专用于 token 失效→前端刷新流程，登录失败用 401 会误触发刷新
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBadCredentials(BadCredentialsException e) {
        return R.fail(ResultCode.WRONG_PASSWORD);
    }

    @ExceptionHandler(LockedException.class)
    @ResponseStatus(HttpStatus.LOCKED)
    public R<Void> handleLocked(LockedException e) {
        return R.fail(ResultCode.ACCOUNT_LOCKED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return R.fail(ResultCode.INTERNAL_ERROR);
    }
}
