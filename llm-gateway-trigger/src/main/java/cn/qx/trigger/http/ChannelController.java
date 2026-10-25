package cn.qx.trigger.http;

import cn.qx.api.IChannelCase;
import cn.qx.api.dto.channel.ChannelRequest;
import cn.qx.api.dto.channel.ChannelView;
import cn.qx.api.dto.common.ToggleRequest;
import cn.qx.api.response.Response;
import cn.qx.api.response.Responses;
import cn.qx.domain.channel.model.entity.Channel;
import cn.qx.domain.channel.model.valobj.TestResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 渠道管理 Controller（管理面 /admin/api/**）。
 */
@RestController
@RequestMapping("/admin/api/channels")
public class ChannelController {

    /** 渠道用例 */
    @Resource
    private IChannelCase channelCase;

    @GetMapping
    public Response<List<ChannelView>> list() {
        return Responses.success(channelCase.list().stream().map(ChannelView::from).toList());
    }

    @GetMapping("/enabled")
    public Response<List<ChannelView>> enabled() {
        return Responses.success(
                channelCase.listEnabledOrdered().stream().map(ChannelView::from).toList());
    }

    @GetMapping("/{id}")
    public Response<ChannelView> get(@PathVariable("id") String id) {
        return Responses.success(ChannelView.from(channelCase.get(id)));
    }

    @PostMapping
    public Response<ChannelView> create(@RequestBody ChannelRequest request) {
        return Responses.success(ChannelView.from(channelCase.create(toEntity(request, null))));
    }

    @PutMapping("/{id}")
    public Response<ChannelView> update(@PathVariable("id") String id, @RequestBody ChannelRequest request) {
        return Responses.success(ChannelView.from(channelCase.update(toEntity(request, id))));
    }

    @PostMapping("/{id}/toggle")
    public Response<ChannelView> toggle(@PathVariable("id") String id, @RequestBody ToggleRequest request) {
        return Responses.success(ChannelView.from(channelCase.toggle(id, request.isEnabled())));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") String id) {
        channelCase.delete(id);
        return Responses.success(null);
    }

    /**
     * 渠道连通性测试。
     *
     * @param id 测试请求体（含渠道 ID）
     * @return 测试结果
     */
    @PostMapping("/{id}/test")
    public Response<TestResult> testById(@PathVariable("id") String id) {
        return Responses.success(channelCase.testChannel(id));
    }

    private static Channel toEntity(ChannelRequest request, String id) {
        return Channel.builder()
                .id(id)
                .name(request.getName())
                .type(request.getType())
                .baseUrl(request.getBaseUrl())
                .apiKey(request.getApiKey())
                .models(request.getModels())
                .status(request.getStatus())
                .priority(request.getPriority())
                .weight(request.getWeight())
                .config(request.getConfig())
                .modelMapping(request.getModelMapping())
                .build();
    }

}
