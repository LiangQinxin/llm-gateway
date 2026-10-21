package cn.qx.api.dto.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OpenAI 模型列表项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    /** 模型 ID */
    private String id;
    /** 对象类型，固定 model */
    private String object;
    /** 提供方 */
    private String ownedBy;
}
