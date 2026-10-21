package cn.qx.domain.proxy.service;

import java.util.OptionalLong;

/**
 * 单次流式响应的 SSE usage 旁路解析器。
 */
public interface ISseUsageParserService {

    /** 接受任意字节分块。 */
    void accept(byte[] chunk);

    /** 结束输入并处理末尾未带换行的行。 */
    void finish();

    /** 已解析到的总 Token 数。 */
    OptionalLong usageTokens();
}
