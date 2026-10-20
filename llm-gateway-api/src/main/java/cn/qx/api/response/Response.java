package cn.qx.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> implements Serializable {

    /** 序列化版本号 */
    private static final long serialVersionUID = 7000723935764546321L;

    /** 响应码（4 位数字串，如 0000=成功） */
    private String code;

    /** 响应说明文案 */
    private String info;

    /** 响应数据负载 */
    private T data;

}
