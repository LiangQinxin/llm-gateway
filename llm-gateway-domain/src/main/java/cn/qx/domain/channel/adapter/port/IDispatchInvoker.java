package cn.qx.domain.channel.adapter.port;

import cn.qx.domain.channel.model.valobj.DispatchOutcome;
import cn.qx.domain.channel.model.valobj.ProxyRequest;

/**
 * 对 proxy 用例暴露的唯一渠道转发门面（ADR-0009）。
 */
public interface IDispatchInvoker {

    DispatchOutcome invoke(ProxyRequest request);
}
