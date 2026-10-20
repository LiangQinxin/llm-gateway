package cn.qx.trigger.http;

import cn.qx.api.dto.common.ToggleRequest;
import cn.qx.api.dto.identity.ApiKeyRequest;
import cn.qx.api.response.Response;
import cn.qx.api.response.Responses;
import cn.qx.cases.identity.ApiKeyCase;
import cn.qx.domain.identity.model.entity.ApiKey;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API Key 管理 Controller（管理面 /admin/api/keys）。
 */
@RestController
@RequestMapping("/admin/api/keys")
public class IdentityController {

    /** API Key 用例 */
    @Resource
    private ApiKeyCase apiKeyCase;

    @GetMapping
    public Response<List<ApiKey>> list() {
        return Responses.success(apiKeyCase.list());
    }

    @GetMapping("/{id}")
    public Response<ApiKey> get(@PathVariable("id") String id) {
        return Responses.success(apiKeyCase.get(id));
    }

    @PostMapping
    public Response<ApiKey> create(@RequestBody ApiKeyRequest request) {
        return Responses.success(apiKeyCase.create(toEntity(request, null)));
    }

    @PutMapping("/{id}")
    public Response<ApiKey> update(@PathVariable("id") String id, @RequestBody ApiKeyRequest request) {
        return Responses.success(apiKeyCase.update(toEntity(request, id)));
    }

    @PostMapping("/{id}/toggle")
    public Response<ApiKey> toggle(@PathVariable("id") String id, @RequestBody ToggleRequest request) {
        return Responses.success(apiKeyCase.toggle(id, request.isEnabled()));
    }

    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable("id") String id) {
        apiKeyCase.delete(id);
        return Responses.success(null);
    }

    private static ApiKey toEntity(ApiKeyRequest request, String id) {
        return ApiKey.builder()
                .id(id)
                .name(request.getName())
                .key(request.getKey())
                .status(request.getStatus())
                .allowedModels(request.getAllowedModels())
                .allowedChannels(request.getAllowedChannels())
                .quotaLimit(request.getQuotaLimit())
                .expiresAt(request.getExpiresAt())
                .build();
    }
}
