package cn.qx.domain.security.model.valobj;

/**
 * 交给子扫描器的一段文本及其定位信息。
 *
 * @param text     文本
 * @param location JSONPath；原文扫描时为 {@code @raw}
 * @param raw      是否对整段原文扫描
 */
public record ScanText(String text, String location, boolean raw) {

    public String locationAt(int start, int end) {
        return raw ? "@raw[" + start + ".." + end + "]" : location;
    }
}
