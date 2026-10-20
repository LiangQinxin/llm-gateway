package cn.qx.types.common;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 时间与 ID 生成工具（ADR-0004）。
 */
public final class TimeUtils {

    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");

    private TimeUtils() {
    }

    /** 当前时间：ISO-8601 带偏移，微秒精度。 */
    public static String now() {
        return OffsetDateTime.now().format(ISO_OFFSET);
    }

    /** 标准 36 字符 UUID。 */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
