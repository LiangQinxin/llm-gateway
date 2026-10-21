package cn.qx.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据面认证失败语义码。
 */
@Getter
@AllArgsConstructor
public enum AuthFailureCode {

    INVALID_API_KEY("invalid_api_key"),
    MODEL_NOT_FOUND("model_not_found"),
    FORBIDDEN("forbidden"),
    INSUFFICIENT_QUOTA("insufficient_quota"),
    ;

    private final String code;

    public static AuthFailureCode fromCode(String code) {
        for (AuthFailureCode value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return INVALID_API_KEY;
    }
}
