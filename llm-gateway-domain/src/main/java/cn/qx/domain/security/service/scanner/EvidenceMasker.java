package cn.qx.domain.security.service.scanner;

/**
 * 证据打码工具；对齐安全审计引擎 mask_evidence：
 * 换行转空格；长度 ≤16 原样返回；否则保留前 8 字符 + **** + 后 4 字符。
 * 审计日志只保留"足够识别但不完整"的证据，防止审计系统自身成为泄露源。
 */
public final class EvidenceMasker {

    private static final int PLAIN_LIMIT = 16;
    private static final int HEAD = 8;
    private static final int TAIL = 4;
    private static final String MASK = "****";

    private EvidenceMasker() {
    }

    public static String mask(String evidence) {
        if (evidence == null) {
            return null;
        }
        String singleLine = evidence.replace('\n', ' ');
        if (singleLine.length() <= PLAIN_LIMIT) {
            return singleLine;
        }
        return singleLine.substring(0, HEAD) + MASK
                + singleLine.substring(singleLine.length() - TAIL);
    }
}
