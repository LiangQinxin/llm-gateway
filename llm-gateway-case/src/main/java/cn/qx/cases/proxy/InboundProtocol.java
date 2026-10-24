package cn.qx.cases.proxy;

import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import lombok.Getter;

/**
 * 数据面协议族注册表：路径决定鉴权头、model 位置与错误体形状。
 */
@Getter
public enum InboundProtocol {

    /** OpenAI Chat Completions 入站（Bearer 鉴权、OpenAI 形状错误体） */
    CC("/v1/chat/completions", ProtocolFamily.CC, "Authorization", true, false),
    /** OpenAI Responses 入站（Bearer 鉴权、OpenAI 形状错误体） */
    RP("/v1/responses", ProtocolFamily.RP, "Authorization", true, false),
    /** Claude Messages 入站（x-api-key 鉴权、Anthropic 形状错误体） */
    MS("/v1/messages", ProtocolFamily.MS, "x-api-key", false, true),
    ;

    /** 入站路径 */
    private final String path;
    /** 所属协议族（cc/rp/ms） */
    private final ProtocolFamily family;
    /** 提取调用方 key 的请求头名 */
    private final String authHeader;
    /** key 是否走 Authorization: Bearer（false=直接取 x-api-key） */
    private final boolean bearer;
    /** 错误体是否用 Anthropic 形状（true=Anthropic，false=OpenAI） */
    private final boolean anthropicError;

    InboundProtocol(String path,
                    ProtocolFamily family,
                    String authHeader,
                    boolean bearer,
                    boolean anthropicError) {
        this.path = path;
        this.family = family;
        this.authHeader = authHeader;
        this.bearer = bearer;
        this.anthropicError = anthropicError;
    }

    /** 按路径解析协议族；其他 OpenAI 兼容入口默认归 cc。 */
    public static InboundProtocol fromPath(String path) {
        if ("/v1/responses".equals(path)) {
            return RP;
        }
        if ("/v1/messages".equals(path)) {
            return MS;
        }
        return CC;
    }
}
