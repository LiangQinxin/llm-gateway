package cn.qx.domain.security.service.rule;

import cn.qx.domain.security.model.valobj.SecuritySeverity;

import java.util.List;

/**
 * 内置规则出厂目录：与六个子扫描器的 ruleId/severity 一一对应。
 */
public final class SecurityRuleCatalog {

    private static final String T_UNICODE = "security.scan_unicode";
    private static final String T_NETWORK = "security.scan_network";
    private static final String T_TOOLS = "security.scan_tools";
    private static final String T_TRACKING = "security.scan_tracking";

    private static final List<RuleMetadata> DEFAULTS = List.of(
            rule("credential_apikey_openai", "credential", SecuritySeverity.HIGH, null,
                    "OpenAI API 密钥", "检测 sk-proj-*、sk-* 等 OpenAI 密钥格式"),
            rule("credential_apikey_anthropic", "credential", SecuritySeverity.HIGH, null,
                    "Anthropic API 密钥", "检测 sk-ant-* 等 Anthropic 密钥格式"),
            rule("credential_apikey_generic", "credential", SecuritySeverity.HIGH, null,
                    "通用 API 密钥", "检测 api_key、apikey、sk-* 等通用密钥格式"),
            rule("credential_apikey_google", "credential", SecuritySeverity.HIGH, null,
                    "Google API 密钥", "检测 AIza* 等 Google API 密钥格式"),
            rule("credential_jwt", "credential", SecuritySeverity.HIGH, null,
                    "JWT 令牌", "检测 eyJ* 开头的 JWT 令牌格式"),
            rule("credential_aws_access", "credential", SecuritySeverity.CRITICAL, null,
                    "AWS 访问密钥", "检测 AKIA*、ASIA* 等 AWS Access Key"),
            rule("credential_aws_secret", "credential", SecuritySeverity.CRITICAL, null,
                    "AWS 密钥对", "检测 AWS Secret Access Key 密钥对格式"),
            rule("credential_github_pat", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub 访问令牌", "检测 ghp_*、github_pat_* 等 GitHub 令牌格式"),
            rule("credential_github_oauth", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub OAuth 令牌", "检测 GitHub OAuth Token 格式"),
            rule("credential_github_pat_v2", "credential", SecuritySeverity.CRITICAL, null,
                    "GitHub 细粒度令牌", "检测 github_pat_* 细粒度令牌格式"),
            rule("credential_slack_token", "credential", SecuritySeverity.HIGH, null,
                    "Slack 令牌", "检测 xoxb-*、xoxp-* 等 Slack 令牌格式"),
            rule("credential_bearer", "credential", SecuritySeverity.HIGH, null,
                    "Bearer 令牌", "检测 Authorization: Bearer 等认证令牌"),
            rule("credential_private_key", "credential", SecuritySeverity.CRITICAL, null,
                    "私钥内容", "检测 PEM/OpenSSH 私钥头部"),
            rule("credential_named_field", "credential", SecuritySeverity.HIGH, null,
                    "敏感凭证字段", "检测 Authorization、Cookie、Session、Secret 等字段名"),
            rule("credential_password", "credential", SecuritySeverity.MEDIUM, null,
                    "密码赋值", "检测 password=、passwd 等密码赋值"),
            rule("credential_token", "credential", SecuritySeverity.MEDIUM, null,
                    "Token 赋值", "检测 token=、access_token 等令牌赋值"),
            rule("credential_secret", "credential", SecuritySeverity.MEDIUM, null,
                    "密钥赋值", "检测 secret=、client_secret 等密钥赋值"),

            rule("path_sensitive_unix", "file", SecuritySeverity.HIGH, null,
                    "敏感文件路径", "检测 .env、~/.ssh、id_rsa、.aws/credentials 等敏感路径"),
            rule("path_sensitive_windows", "file", SecuritySeverity.HIGH, null,
                    "Windows 敏感路径", "检测 Windows 系统敏感文件路径"),
            rule("path_sensitive_project", "file", SecuritySeverity.MEDIUM, null,
                    "云凭证文件", "检测 .aws/credentials、.npmrc、.pypirc、.git-credentials、.netrc 等"),
            rule("path_traversal", "file", SecuritySeverity.MEDIUM, null,
                    "路径穿越", "检测 ../、..\\ 等路径穿越特征"),

            rule("unicode_zero_width", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "零宽字符", "检测零宽空格、零宽连接符等 Unicode 隐写字符"),
            rule("unicode_bidi", "unicode", SecuritySeverity.HIGH, T_UNICODE,
                    "双向控制字符", "检测 RTL/LTR 等双向文本控制字符"),
            rule("unicode_variation", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "变体选择符", "检测 Unicode 变体选择符"),
            rule("unicode_pua", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "私用区字符", "检测 Unicode 私用区（PUA）字符"),
            rule("unicode_homoglyph", "unicode", SecuritySeverity.MEDIUM, T_UNICODE,
                    "同形字符混淆", "检测混合文字体系的同形字符伪装"),

            rule("network_exfil_target", "network", SecuritySeverity.HIGH, T_NETWORK,
                    "外部外联目标", "检测 webhook.site、requestbin 等数据外联域名"),
            rule("network_ip_probe", "network", SecuritySeverity.HIGH, T_NETWORK,
                    "公网 IP 探测服务", "检测 ipify、icanhazip 等 IP 探测服务"),
            rule("network_internal_probe", "network", SecuritySeverity.MEDIUM, T_NETWORK,
                    "内网地址探测", "检测 127.0.0.1、169.254.169.254 等内网探测目标"),

            rule("tool_curl", "tool", SecuritySeverity.MEDIUM, T_TOOLS,
                    "curl 命令", "检测 curl 命令执行"),
            rule("tool_wget", "tool", SecuritySeverity.MEDIUM, T_TOOLS,
                    "wget 命令", "检测 wget 命令执行"),
            rule("tool_bash_c", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Shell 命令执行", "检测 bash -c、sh -c 等 Shell 执行"),
            rule("tool_powershell", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "PowerShell 执行", "检测 powershell 命令执行"),
            rule("tool_python_c", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Python 内联执行", "检测 python -c 等内联执行"),
            rule("tool_rm_rf", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "递归强制删除", "检测 rm -rf 等危险删除"),
            rule("tool_mkfs", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "文件系统格式化", "检测 mkfs 等格式化命令"),
            rule("tool_dd", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "磁盘原始写入", "检测 dd 命令写入磁盘"),
            rule("tool_chmod_777", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "全局可写权限", "检测 chmod 777 等危险权限"),
            rule("tool_netcat", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "Netcat 使用", "检测 nc、netcat 命令"),
            rule("tool_nmap", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "网络扫描", "检测 nmap 扫描命令"),
            rule("tool_exfil_curl", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "命令行数据外传", "检测 curl 向外部地址上传数据"),
            rule("tool_exfil_scp", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "远程文件拷贝", "检测 scp 向外部地址拷贝文件"),
            rule("tool_revshell", "tool", SecuritySeverity.CRITICAL, T_TOOLS,
                    "反弹 Shell", "检测 bash -i、nc -e 等反弹 Shell 特征"),
            rule("tool_decode", "tool", SecuritySeverity.HIGH, T_TOOLS,
                    "解码/归档敏感数据", "检测 base64 解码、tar/zip 归档敏感数据"),
            rule("tool_exec", "tool", SecuritySeverity.MEDIUM, T_TOOLS,
                    "动态代码执行", "检测 eval、exec 等动态执行"),
            rule("tool_shell_exfiltration", "tool", SecuritySeverity.CRITICAL, T_TOOLS,
                    "疑似数据外传命令", "检测拼接外部地址的数据外传命令"),

            rule("tracking_pixel_gif", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "追踪像素", "检测透明 GIF 追踪像素"),
            rule("tracking_pixel_filename", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "追踪像素文件名", "检测 pixel.gif 等追踪像素文件名"),
            rule("tracking_google_analytics", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Google Analytics", "检测 Google Analytics 统计脚本"),
            rule("tracking_gtm", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Google 标签管理器", "检测 GTM 容器脚本"),
            rule("tracking_facebook", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Facebook 追踪", "检测 Facebook Pixel 追踪脚本"),
            rule("tracking_hotjar", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Hotjar 追踪", "检测 Hotjar 用户行为分析脚本"),
            rule("tracking_mixpanel", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "Mixpanel 追踪", "检测 Mixpanel 分析脚本"),
            rule("tracking_shortlink", "tracking", SecuritySeverity.LOW, T_TRACKING,
                    "短链接服务", "检测 t.cn、bit.ly 等短链接服务"),

            rule("fingerprint_canvas", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "Canvas 指纹采集", "检测 canvas 画布指纹采集"),
            rule("fingerprint_webgl", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "WebGL 指纹采集", "检测 WebGL 指纹采集"),
            rule("fingerprint_useragent", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "User-Agent 指纹采集", "检测 User-Agent 指纹采集"),
            rule("fingerprint_plugins", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "插件指纹采集", "检测浏览器插件指纹采集"),
            rule("fingerprint_screen", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "屏幕指纹采集", "检测屏幕分辨率等指纹采集"),
            rule("fingerprint_audio", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "音频指纹采集", "检测音频上下文指纹采集"),
            rule("fingerprint_device", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "设备指纹采集", "检测设备指纹综合采集"),
            rule("fingerprint_risk", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "风控上下文采集", "检测风控类上下文信息采集"),
            rule("fingerprint_fraud", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "反欺诈信息采集", "检测反欺诈类信息采集"),
            rule("fingerprint_captcha", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "验证码令牌采集", "检测验证码令牌信息采集"),
            rule("tracking_geolocation", "prompt", SecuritySeverity.MEDIUM, T_TRACKING,
                    "地理位置追踪", "检测地理位置信息采集")
    );

    private SecurityRuleCatalog() {
    }

    public static List<RuleMetadata> defaults() {
        return DEFAULTS.stream().map(SecurityRuleCatalog::copy).toList();
    }

    private static RuleMetadata rule(String ruleId,
                                     String category,
                                     SecuritySeverity severity,
                                     String toggleKey,
                                     String title,
                                     String description) {
        return RuleMetadata.builder()
                .id("builtin-" + ruleId)
                .ruleId(ruleId)
                .category(category)
                .name(title)
                .severity(severity)
                .title(title)
                .description(description)
                .toggleKey(toggleKey)
                .enabled(true)
                .build();
    }

    private static RuleMetadata copy(RuleMetadata source) {
        return source.toBuilder().build();
    }
}
