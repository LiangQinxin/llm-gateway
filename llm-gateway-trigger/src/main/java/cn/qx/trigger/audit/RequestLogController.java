package cn.qx.trigger.audit;

import cn.qx.api.response.Response;
import cn.qx.api.response.Responses;
import cn.qx.cases.audit.RequestLogCase;
import cn.qx.cases.audit.RequestLogPageResult;
import cn.qx.domain.audit.model.entity.RequestLog;
import cn.qx.domain.audit.model.valobj.RequestLogQuery;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 请求日志审计 Controller（管理面 /admin/api/logs）。
 */
@RestController
@RequestMapping("/admin/api/logs")
public class RequestLogController {

    @Resource
    private RequestLogCase requestLogCase;

    @GetMapping
    public Response<RequestLogPageResult> page(
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "apiKeyId", required = false) String apiKeyId,
            @RequestParam(value = "channelId", required = false) String channelId,
            @RequestParam(value = "statusCode", required = false) Integer statusCode,
            @RequestParam(value = "createdFrom", required = false) String createdFrom,
            @RequestParam(value = "createdTo", required = false) String createdTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        RequestLogQuery query = RequestLogQuery.builder()
                .model(model)
                .apiKeyId(apiKeyId)
                .channelId(channelId)
                .statusCode(statusCode)
                .createdFrom(createdFrom)
                .createdTo(createdTo)
                .page(page)
                .size(size)
                .build();
        return Responses.success(requestLogCase.page(query));
    }

    @GetMapping("/{id}")
    public Response<RequestLog> get(@PathVariable("id") String id) {
        return Responses.success(requestLogCase.get(id));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") String id) {
        requestLogCase.delete(id);
        return Responses.success(null);
    }

    @DeleteMapping(params = "before")
    public Response<Integer> deleteBefore(@RequestParam("before") String before) {
        return Responses.success(requestLogCase.deleteBefore(before));
    }

    @DeleteMapping("/before")
    public Response<Integer> deleteBeforeAlias(@RequestParam("createdAt") String createdAt) {
        return Responses.success(requestLogCase.deleteBefore(createdAt));
    }

    @DeleteMapping("/all")
    public Response<Integer> clearAll() {
        return Responses.success(requestLogCase.clearAll());
    }
}
