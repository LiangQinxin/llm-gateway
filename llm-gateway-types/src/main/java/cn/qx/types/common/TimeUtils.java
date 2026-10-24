package cn.qx.types.common;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 时间与 ID 生成工具（ADR-0004）。
 */
public final class TimeUtils {

    /** 项目统一时间格式：ISO-8601 带偏移，微秒精度（yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX）。 */
    private static final DateTimeFormatter ISO_OFFSET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX");
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(8);

    private TimeUtils() {
    }

    /** 当前时间：ISO-8601 带偏移，微秒精度。 */
    public static String now() {
        return OffsetDateTime.now(DEFAULT_OFFSET).format(ISO_OFFSET);
    }

    /**
     * 将带偏移的 ISO-8601 时间规范化为项目统一格式（+08:00，微秒）。
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            return OffsetDateTime.parse(value)
                    .withOffsetSameInstant(DEFAULT_OFFSET)
                    .format(ISO_OFFSET);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间必须是带偏移的 ISO-8601: " + value, e);
        }
    }

    /** 标准 36 字符 UUID。 */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** 计算从带偏移 ISO-8601 时间到当前的毫秒数；非法或未来时间回退 0。 */
    public static long elapsedMillis(String startedAt) {
        if (startedAt == null || startedAt.isBlank()) {
            return 0L;
        }
        try {
            long elapsed = Duration.between(
                    OffsetDateTime.parse(startedAt), OffsetDateTime.now(DEFAULT_OFFSET)).toMillis();
            return Math.max(0L, elapsed);
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }
}
