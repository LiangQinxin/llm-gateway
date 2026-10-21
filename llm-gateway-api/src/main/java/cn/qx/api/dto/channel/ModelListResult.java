package cn.qx.api.dto.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据面模型列表输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelListResult {

    /** 模型列表 */
    private List<ModelInfo> data;
}
