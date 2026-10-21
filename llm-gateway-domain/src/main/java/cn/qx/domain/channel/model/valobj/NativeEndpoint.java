package cn.qx.domain.channel.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道适配器的有序原生端点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NativeEndpoint {

    /** 协议族 */
    private ProtocolFamily protocol;
    /** 上游端点路径 */
    private String path;
    /** 鉴权方式 */
    private AuthStyle authStyle;
}
