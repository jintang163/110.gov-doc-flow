package com.gov.gw.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ApiResponse<Void> biz(ApiException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({BindException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, IllegalArgumentException.class})
    public ApiResponse<Void> badRequest(Exception e) {
        return ApiResponse.error(400, "请求参数有误：" + e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> tooLarge(MaxUploadSizeExceededException e) {
        return ApiResponse.error(400, "文件大小超出限制（50MB）");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> unknown(Exception e) {
        log.error("未处理异常", e);
        return ApiResponse.error(500, "系统繁忙，请稍后重试");
    }
}
