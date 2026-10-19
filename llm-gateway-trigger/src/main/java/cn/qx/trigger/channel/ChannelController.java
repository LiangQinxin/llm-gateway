package cn.qx.trigger.channel;

import cn.qx.api.dto.channel.TestChannelRequest;
import cn.qx.api.response.Response;
import cn.qx.cases.channel.ChannelCase;
import cn.qx.domain.channel.model.valobj.TestResult;
import cn.qx.types.enums.ResponseCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 渠道管理 Controller（管理面 /admin/api/**）。
 */
@RestController
@RequestMapping("/admin/api/channel")
public class ChannelController {

    /** 渠道用例 */
    @Resource
    private ChannelCase channelCase;

    /**
     * 渠道连通性测试。
     *
     * @param request 测试请求体（含渠道 ID）
     * @return 测试结果
     */
    @PostMapping("/test")
    public Response<TestResult> test(@RequestBody TestChannelRequest request) {
        return Response.<TestResult>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(channelCase.testChannel(request.getId()))
                .build();
    }
}
