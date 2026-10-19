package com.example.dataprocess.exception;

/** 标记已经记录详细告警的处理异常，避免外层重复入库。 */
public class RecordedDataAlarmException extends RuntimeException {
    public RecordedDataAlarmException(String message, Throwable cause) { super(message, cause); }
}
