package com.xianjilijd.job.exception;

import com.xianjilijd.job.common.ApiResponse;
import com.xianjilijd.job.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBiz(BizException e) {
        log.warn("biz error: {}", e.getMessage());
        return ApiResponse.badRequest(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("validation failed: {}", msg);
        return ApiResponse.badRequest(msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("request body not readable: {}", e.getMessage());
        return ApiResponse.badRequest("请求体格式错误，请检查 JSON 是否合法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleAll(Exception e) {
        log.error("server error", e);
        return ApiResponse.error("服务器内部错误");
    }
}
