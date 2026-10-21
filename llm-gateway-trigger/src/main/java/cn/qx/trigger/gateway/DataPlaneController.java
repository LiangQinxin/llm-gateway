package cn.qx.trigger.gateway;

import cn.qx.api.IChannelCase;
import cn.qx.api.dto.channel.ModelInfo;
import cn.qx.api.dto.channel.ModelListResult;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据面公开端点：健康检查与模型列表。
 */
@RestController
public class DataPlaneController {

    /** 渠道用例 */
    @Resource
    private IChannelCase channelCase;

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject health() {
        JSONObject body = new JSONObject();
        body.put("status", "ok");
        return body;
    }

    @GetMapping(value = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject apiHealth() {
        return health();
    }

    @GetMapping(value = "/v1/models", produces = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject models() {
        ModelListResult result = channelCase.listModelCatalog();
        JSONArray data = new JSONArray();
        for (ModelInfo model : result.getData()) {
            JSONObject item = new JSONObject();
            item.put("id", model.getId());
            item.put("object", model.getObject());
            item.put("owned_by", model.getOwnedBy());
            data.add(item);
        }
        JSONObject body = new JSONObject();
        body.put("object", "list");
        body.put("data", data);
        return body;
    }
}
