package cn.qx.cases.proxy;

import cn.qx.domain.channel.model.valobj.ProtocolFamily;
import lombok.Getter;

/**
 * 数据面协议族注册表：路径决定鉴权头、model 位置与错误体形状。
 */
@Getter
public enum InboundProtocol {

    CC("/v1/chat/completions", ProtocolFamily.CC, "Authorization", true, false),
    RP("/v1/responses", ProtocolFamily.RP, "Authorization", true, false),
    MS("/v1/messages", ProtocolFamily.MS, "x-api-key", false, true),
    ;

    private final String path;
    private final ProtocolFamily family;
    private final String authHeader;
    private final boolean bearer;
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
