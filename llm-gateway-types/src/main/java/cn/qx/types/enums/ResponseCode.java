package cn.qx.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {

    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    UNAUTHORIZED("0401", "未授权"),
    FORBIDDEN("0403", "禁止访问"),
    NOT_FOUND("0404", "资源不存在"),
    CONFLICT("0409", "资源冲突"),
    ;

    private String code;
    private String info;

}
