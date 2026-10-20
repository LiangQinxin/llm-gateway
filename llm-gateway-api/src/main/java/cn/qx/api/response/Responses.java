package cn.qx.api.response;

import cn.qx.types.enums.ResponseCode;

/**
 * 管理面统一响应工厂。
 */
public final class Responses {

    private Responses() {
    }

    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }
}
