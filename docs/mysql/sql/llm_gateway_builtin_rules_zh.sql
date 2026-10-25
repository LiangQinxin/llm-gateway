-- ============================================================
-- 安全内置规则写入脚本（65 条，中文名称 + 检测说明）
-- 适用：security_builtin_rules 表数据已清空后直接写入
-- 执行方式：mysql -uroot -p123456 llm_api < 本文件
-- 说明：pattern/action 为遗留列固定 NULL；enabled 默认 1；
--       severity 按枚举 code 小写存储；toggle_key 关联类别总开关
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO security_builtin_rules
(id, rule_id, category, name, severity, pattern, action, title, description, toggle_key, enabled, created_at, updated_at) VALUES
('builtin-credential_apikey_openai','credential_apikey_openai','credential','OpenAI API 密钥','high',NULL,NULL,'OpenAI API 密钥','检测 sk-proj-*、sk-* 等 OpenAI 密钥格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_apikey_anthropic','credential_apikey_anthropic','credential','Anthropic API 密钥','high',NULL,NULL,'Anthropic API 密钥','检测 sk-ant-* 等 Anthropic 密钥格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_apikey_generic','credential_apikey_generic','credential','通用 API 密钥','high',NULL,NULL,'通用 API 密钥','检测 api_key、apikey、sk-* 等通用密钥格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_apikey_google','credential_apikey_google','credential','Google API 密钥','high',NULL,NULL,'Google API 密钥','检测 AIza* 等 Google API 密钥格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_jwt','credential_jwt','credential','JWT 令牌','high',NULL,NULL,'JWT 令牌','检测 eyJ* 开头的 JWT 令牌格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_aws_access','credential_aws_access','credential','AWS 访问密钥','critical',NULL,NULL,'AWS 访问密钥','检测 AKIA*、ASIA* 等 AWS Access Key',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_aws_secret','credential_aws_secret','credential','AWS 密钥对','critical',NULL,NULL,'AWS 密钥对','检测 AWS Secret Access Key 密钥对格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_github_pat','credential_github_pat','credential','GitHub 访问令牌','critical',NULL,NULL,'GitHub 访问令牌','检测 ghp_*、github_pat_* 等 GitHub 令牌格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_github_oauth','credential_github_oauth','credential','GitHub OAuth 令牌','critical',NULL,NULL,'GitHub OAuth 令牌','检测 GitHub OAuth Token 格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_github_pat_v2','credential_github_pat_v2','credential','GitHub 细粒度令牌','critical',NULL,NULL,'GitHub 细粒度令牌','检测 github_pat_* 细粒度令牌格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_slack_token','credential_slack_token','credential','Slack 令牌','high',NULL,NULL,'Slack 令牌','检测 xoxb-*、xoxp-* 等 Slack 令牌格式',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_bearer','credential_bearer','credential','Bearer 令牌','high',NULL,NULL,'Bearer 令牌','检测 Authorization: Bearer 等认证令牌',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_private_key','credential_private_key','credential','私钥内容','critical',NULL,NULL,'私钥内容','检测 PEM/OpenSSH 私钥头部',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_named_field','credential_named_field','credential','敏感凭证字段','high',NULL,NULL,'敏感凭证字段','检测 Authorization、Cookie、Session、Secret 等字段名',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_password','credential_password','credential','密码赋值','medium',NULL,NULL,'密码赋值','检测 password=、passwd 等密码赋值',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_token','credential_token','credential','Token 赋值','medium',NULL,NULL,'Token 赋值','检测 token=、access_token 等令牌赋值',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-credential_secret','credential_secret','credential','密钥赋值','medium',NULL,NULL,'密钥赋值','检测 secret=、client_secret 等密钥赋值',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-path_sensitive_unix','path_sensitive_unix','file','敏感文件路径','high',NULL,NULL,'敏感文件路径','检测 .env、~/.ssh、id_rsa、.aws/credentials 等敏感路径',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-path_sensitive_windows','path_sensitive_windows','file','Windows 敏感路径','high',NULL,NULL,'Windows 敏感路径','检测 Windows 系统敏感文件路径',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-path_sensitive_project','path_sensitive_project','file','云凭证文件','medium',NULL,NULL,'云凭证文件','检测 .aws/credentials、.npmrc、.pypirc、.git-credentials、.netrc 等',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-path_traversal','path_traversal','file','路径穿越','medium',NULL,NULL,'路径穿越','检测 ../、..\\ 等路径穿越特征',NULL,1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-unicode_zero_width','unicode_zero_width','unicode','零宽字符','medium',NULL,NULL,'零宽字符','检测零宽空格、零宽连接符等 Unicode 隐写字符','security.scan_unicode',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-unicode_bidi','unicode_bidi','unicode','双向控制字符','high',NULL,NULL,'双向控制字符','检测 RTL/LTR 等双向文本控制字符','security.scan_unicode',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-unicode_variation','unicode_variation','unicode','变体选择符','medium',NULL,NULL,'变体选择符','检测 Unicode 变体选择符','security.scan_unicode',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-unicode_pua','unicode_pua','unicode','私用区字符','medium',NULL,NULL,'私用区字符','检测 Unicode 私用区（PUA）字符','security.scan_unicode',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-unicode_homoglyph','unicode_homoglyph','unicode','同形字符混淆','medium',NULL,NULL,'同形字符混淆','检测混合文字体系的同形字符伪装','security.scan_unicode',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-network_exfil_target','network_exfil_target','network','外部外联目标','high',NULL,NULL,'外部外联目标','检测 webhook.site、requestbin 等数据外联域名','security.scan_network',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-network_ip_probe','network_ip_probe','network','公网 IP 探测服务','high',NULL,NULL,'公网 IP 探测服务','检测 ipify、icanhazip 等 IP 探测服务','security.scan_network',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-network_internal_probe','network_internal_probe','network','内网地址探测','medium',NULL,NULL,'内网地址探测','检测 127.0.0.1、169.254.169.254 等内网探测目标','security.scan_network',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_curl','tool_curl','tool','curl 命令','medium',NULL,NULL,'curl 命令','检测 curl 命令执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_wget','tool_wget','tool','wget 命令','medium',NULL,NULL,'wget 命令','检测 wget 命令执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_bash_c','tool_bash_c','tool','Shell 命令执行','high',NULL,NULL,'Shell 命令执行','检测 bash -c、sh -c 等 Shell 执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_powershell','tool_powershell','tool','PowerShell 执行','high',NULL,NULL,'PowerShell 执行','检测 powershell 命令执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_python_c','tool_python_c','tool','Python 内联执行','high',NULL,NULL,'Python 内联执行','检测 python -c 等内联执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_rm_rf','tool_rm_rf','tool','递归强制删除','high',NULL,NULL,'递归强制删除','检测 rm -rf 等危险删除','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_mkfs','tool_mkfs','tool','文件系统格式化','high',NULL,NULL,'文件系统格式化','检测 mkfs 等格式化命令','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_dd','tool_dd','tool','磁盘原始写入','high',NULL,NULL,'磁盘原始写入','检测 dd 命令写入磁盘','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_chmod_777','tool_chmod_777','tool','全局可写权限','high',NULL,NULL,'全局可写权限','检测 chmod 777 等危险权限','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_netcat','tool_netcat','tool','Netcat 使用','high',NULL,NULL,'Netcat 使用','检测 nc、netcat 命令','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_nmap','tool_nmap','tool','网络扫描','high',NULL,NULL,'网络扫描','检测 nmap 扫描命令','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_exfil_curl','tool_exfil_curl','tool','命令行数据外传','high',NULL,NULL,'命令行数据外传','检测 curl 向外部地址上传数据','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_exfil_scp','tool_exfil_scp','tool','远程文件拷贝','high',NULL,NULL,'远程文件拷贝','检测 scp 向外部地址拷贝文件','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_revshell','tool_revshell','tool','反弹 Shell','critical',NULL,NULL,'反弹 Shell','检测 bash -i、nc -e 等反弹 Shell 特征','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_decode','tool_decode','tool','解码/归档敏感数据','high',NULL,NULL,'解码/归档敏感数据','检测 base64 解码、tar/zip 归档敏感数据','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_exec','tool_exec','tool','动态代码执行','medium',NULL,NULL,'动态代码执行','检测 eval、exec 等动态执行','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tool_shell_exfiltration','tool_shell_exfiltration','tool','疑似数据外传命令','critical',NULL,NULL,'疑似数据外传命令','检测拼接外部地址的数据外传命令','security.scan_tools',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_pixel_gif','tracking_pixel_gif','tracking','追踪像素','low',NULL,NULL,'追踪像素','检测透明 GIF 追踪像素','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_pixel_filename','tracking_pixel_filename','tracking','追踪像素文件名','low',NULL,NULL,'追踪像素文件名','检测 pixel.gif 等追踪像素文件名','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_google_analytics','tracking_google_analytics','tracking','Google Analytics','low',NULL,NULL,'Google Analytics','检测 Google Analytics 统计脚本','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_gtm','tracking_gtm','tracking','Google 标签管理器','low',NULL,NULL,'Google 标签管理器','检测 GTM 容器脚本','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_facebook','tracking_facebook','tracking','Facebook 追踪','low',NULL,NULL,'Facebook 追踪','检测 Facebook Pixel 追踪脚本','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_hotjar','tracking_hotjar','tracking','Hotjar 追踪','low',NULL,NULL,'Hotjar 追踪','检测 Hotjar 用户行为分析脚本','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_mixpanel','tracking_mixpanel','tracking','Mixpanel 追踪','low',NULL,NULL,'Mixpanel 追踪','检测 Mixpanel 分析脚本','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_shortlink','tracking_shortlink','tracking','短链接服务','low',NULL,NULL,'短链接服务','检测 t.cn、bit.ly 等短链接服务','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_canvas','fingerprint_canvas','prompt','Canvas 指纹采集','medium',NULL,NULL,'Canvas 指纹采集','检测 canvas 画布指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_webgl','fingerprint_webgl','prompt','WebGL 指纹采集','medium',NULL,NULL,'WebGL 指纹采集','检测 WebGL 指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_useragent','fingerprint_useragent','prompt','User-Agent 指纹采集','medium',NULL,NULL,'User-Agent 指纹采集','检测 User-Agent 指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_plugins','fingerprint_plugins','prompt','插件指纹采集','medium',NULL,NULL,'插件指纹采集','检测浏览器插件指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_screen','fingerprint_screen','prompt','屏幕指纹采集','medium',NULL,NULL,'屏幕指纹采集','检测屏幕分辨率等指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_audio','fingerprint_audio','prompt','音频指纹采集','medium',NULL,NULL,'音频指纹采集','检测音频上下文指纹采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_device','fingerprint_device','prompt','设备指纹采集','medium',NULL,NULL,'设备指纹采集','检测设备指纹综合采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_risk','fingerprint_risk','prompt','风控上下文采集','medium',NULL,NULL,'风控上下文采集','检测风控类上下文信息采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_fraud','fingerprint_fraud','prompt','反欺诈信息采集','medium',NULL,NULL,'反欺诈信息采集','检测反欺诈类信息采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-fingerprint_captcha','fingerprint_captcha','prompt','验证码令牌采集','medium',NULL,NULL,'验证码令牌采集','检测验证码令牌信息采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00'),
('builtin-tracking_geolocation','tracking_geolocation','prompt','地理位置追踪','medium',NULL,NULL,'地理位置追踪','检测地理位置信息采集','security.scan_tracking',1,'2026-10-25T13:00:00+08:00','2026-10-25T13:00:00+08:00');

-- 校验写入行数（应为 65）
SELECT COUNT(*) AS inserted_count FROM security_builtin_rules;
