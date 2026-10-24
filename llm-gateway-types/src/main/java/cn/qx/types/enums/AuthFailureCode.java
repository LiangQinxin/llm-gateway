package cn.qx.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据面认证失败语义码。
 */
@Getter
@AllArgsConstructor
public enum AuthFailureCode {

    /** 无效 API Key（密钥不存在/已停用/格式非法）。 */
    INVALID_API_KEY("invalid_api_key"),
    /** 请求的模型不存在。 */
    MODEL_NOT_FOUND("model_not_found"),
    /** 调用方无权访问该模型或渠道。 */
    FORBIDDEN("forbidden"),
    /** 账户额度不足。 */
    INSUFFICIENT_QUOTA("insufficient_quota"),
    ;

    /** 失败语义码字符串值。 */
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
