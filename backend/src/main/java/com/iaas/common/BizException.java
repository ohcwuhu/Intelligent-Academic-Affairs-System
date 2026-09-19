package com.iaas.common;

import lombok.Getter;

/**
 * 业务异常。抛出后由 {@link GlobalExceptionHandler} 统一转为标准响应。
 *
 * <p>约定：4xx 为客户端可修正的错误（容量已满、时间冲突、越权），5xx 为服务端问题。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException notFound(String what) {
        return new BizException(404, what + "不存在");
    }

    public static BizException forbidden(String message) {
        return new BizException(403, message);
    }
}
