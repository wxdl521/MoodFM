package com.moodfm.common.exception;

import com.moodfm.common.result.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    /** 兼容旧的 new BizException(500, "...") 调用方式 */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 410 -> HttpStatus.GONE;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default  -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
