package cn.qx.domain.channel.model.valobj;

/**
 * 渠道分发未命中原因。
 */
public enum DispatchUnroutableReason {

    /** 有启用渠道，但没有渠道支持请求模型 */
    MODEL_NOT_FOUND,

    /** 全库没有任何启用渠道 */
    NO_AVAILABLE_CHANNEL
}
