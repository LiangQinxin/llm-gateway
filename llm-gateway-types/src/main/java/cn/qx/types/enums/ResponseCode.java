package cn.qx.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    /** 成功 */
    SUCCESS("0000", "成功"),
    /** 未知失败 */
    UN_ERROR("0001", "未知失败"),
    /** 非法参数 */
    ILLEGAL_PARAMETER("0002", "非法参数"),
    /** 未授权 */
    UNAUTHORIZED("0401", "未授权"),
    /** 禁止访问 */
    FORBIDDEN("0403", "禁止访问"),
    /** 资源不存在 */
    NOT_FOUND("0404", "资源不存在"),
    /** 资源冲突 */
    CONFLICT("0409", "资源冲突"),
    ;

    /** 响应码（4 位数字串，如 0000=成功） */
    private String code;
    /** 响应说明文案 */
    private String info;

}
