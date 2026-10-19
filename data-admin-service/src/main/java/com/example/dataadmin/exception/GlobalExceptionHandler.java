package com.example.dataadmin.exception;

import com.example.common.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将参数校验异常和业务异常转换为统一接口响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            GlobalExceptionHandler.class);

    /** 请求体格式或字段类型错误按参数错误返回，避免文本框误提交字符串时返回系统异常。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableJson(
            HttpMessageNotReadableException exception) {
        // 第一步：普通请求使用通用格式提示，不为其他业务接口附加协议配置字段要求。
        String message = "请求体JSON格式或字段类型不正确";
        // 第二步：只有解析路径涉及协议动态参数时，明确提示该字段必须提交对象。
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonMappingException) {
                for (JsonMappingException.Reference reference : ((JsonMappingException) cause).getPath()) {
                    if ("configParams".equals(reference.getFieldName())) {
                        message = "请求体JSON格式或字段类型不正确，configParams应为JSON对象";
                    }
                }
            }
        }
        // 第三步：统一按参数错误返回四百，不把JSON格式错误报告为系统异常。
        return ResponseEntity.badRequest().body(ApiResponse.<Void>fail(400, message));
    }

    /** 处理 JSON 请求体参数校验异常。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        return buildValidationResponse(
                exception.getBindingResult().getFieldError());
    }

    /** 处理查询参数绑定与校验异常。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException exception) {
        return buildValidationResponse(exception.getFieldError());
    }

    /** 处理业务规则校验异常。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException exception) {
        LOGGER.warn("请求处理失败：{}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>fail(400, exception.getMessage()));
    }

    /** 处理卫星目录管理远程调用异常。 */
    @ExceptionHandler(SatelliteManagementException.class)
    public ResponseEntity<ApiResponse<Void>> handleSatelliteManagementException(
            SatelliteManagementException exception) {
        // 记录中文业务信息，底层异常保留在服务端日志中便于定位。
        LOGGER.error("卫星目录管理调用失败：{}", exception.getMessage(), exception);
        return ResponseEntity.status(exception.getResponseCode())
                .body(ApiResponse.<Void>fail(
                        exception.getResponseCode(),
                        exception.getMessage()));
    }

    /** 处理必填请求参数缺失异常。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception) {
        // 使用实际缺失的参数名生成中文提示，供所有必填请求参数复用。
        String message = "缺少必填请求参数：" + exception.getParameterName();
        // 参数缺失属于请求错误，沿用统一响应结构返回状态码四百。
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>fail(400, message));
    }

    /** 处理查询参数类型转换异常。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception) {
        String message = "range".equals(exception.getName())
                ? "时间范围只能为3h、24h或7d"
                : "请求参数格式错误：" + exception.getName();
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>fail(400, message));
    }

    /** 处理未预期的系统异常。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception exception) {
        LOGGER.error("未处理的系统异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>fail(500, "系统内部错误"));
    }

    /**
     * 构造参数校验失败响应。
     */
    private ResponseEntity<ApiResponse<Void>> buildValidationResponse(
            FieldError fieldError) {
        String message = fieldError == null
                ? "请求参数校验失败"
                : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>fail(400, message));
    }
}
