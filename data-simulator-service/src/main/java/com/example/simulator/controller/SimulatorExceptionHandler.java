package com.example.simulator.controller;

import com.example.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

/** 将可预期错误转为中文响应，数据库细节仅保留在日志中。 */
@RestControllerAdvice
public class SimulatorExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorExceptionHandler.class);

    /** 请求校验统一使用中文提示，避免框架默认英文消息。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, "请求字段缺失、格式错误或超出允许范围"));
    }

    /** 参数和业务状态错误分别返回明确状态码。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(400, exception.getMessage()));
    }

    /** 状态竞争或重复启动返回冲突。 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(409).body(ApiResponse.fail(409, exception.getMessage()));
    }

    /** 按实际数据库错误区分名称、运行状态和旧约束问题，原始细节只进入日志。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> databaseConflict(DataIntegrityViolationException exception) {
        // 第一步：提取数据库错误状态，完整异常保留在中文日志中供定位。
        Throwable cause = exception.getMostSpecificCause();
        String state = cause instanceof SQLException ? ((SQLException) cause).getSQLState() : null;
        LOGGER.error("模拟源操作被数据库拒绝，数据库状态码：{}", state, exception);
        // 第二步：约束标识只用于内部判断，不把数据库原始错误直接返回前端。
        String detail = String.valueOf(exception.getMessage()) + " " + String.valueOf(cause.getMessage());
        String message;
        if (detail.contains("uk_simulator_source_name")) {
            message = "模拟源名称已存在，请更换名称";
        } else if (detail.contains("uk_simulator_active_run")) {
            message = "模拟源已经启动，请勿重复启动";
        } else if (detail.contains("simulator_source_config_pkey")) {
            message = "模拟源主键冲突，请检查数据库主键序列";
        } else if ("23514".equals(state)) {
            message = "模拟源数据库仍有旧校验约束，请执行最新模拟源建表升级脚本";
        } else if ("23503".equals(state)) {
            message = "模拟源关联记录不存在或仍被引用，请刷新后重试";
        } else if ("23502".equals(state)) {
            message = "模拟源必填配置缺失，请检查提交参数";
        } else {
            message = "模拟源数据保存失败，请查看服务日志";
        }
        // 第三步：沿用既有冲突状态码和响应结构，前端直接展示具体中文提示。
        return ResponseEntity.status(409).body(ApiResponse.fail(409, message));
    }

    /** 未预期错误记录中文上下文，调用方收到统一错误。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        LOGGER.error("模拟源接口处理失败", exception);
        return ResponseEntity.status(500).body(ApiResponse.fail(500, "模拟源服务处理失败，请检查服务日志"));
    }
}

