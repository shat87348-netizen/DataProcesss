package com.example.dataprocess.controller;

import com.example.common.response.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP采集的统一接收入口，当前只确认收到JSON，后续按业务约定扩展处理。 */
@RestController
@RequestMapping("/collection/receive")
public class HttpCollectionController {

    /** 接收统一前缀下任意路径的JSON，不要求为每条采集配置新增接口方法。 */
    @PostMapping(value = "/**", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> receive(@RequestBody JsonNode content) {
        // 第一步：由框架读取必填请求体并解析JSON，不绑定尚未确定的业务字段。
        // 第二步：当前只返回收到请求的成功应答，后续业务处理在此入口扩展。
        return ApiResponse.success();
    }

    /** 请求体缺失或JSON无法解析时返回中文参数错误，不返回成功应答。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidJson(
            HttpMessageNotReadableException exception) {
        // 使用已有统一响应结构，明确区分请求格式错误与成功接收。
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>fail(400, "请求体必须是非空的合法JSON"));
    }
}
