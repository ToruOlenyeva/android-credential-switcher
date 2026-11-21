package io.github.toruolenyeva.credswitcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Runtime bilingual (Chinese / English) string dictionary with auto system locale detection.
 */
public final class Strings {

    private static final String PREF_LANG = "ui_lang";
    private final boolean zh;

    private Strings(boolean zh) {
        this.zh = zh;
    }

    public static Strings load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(CredentialStore.PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(PREF_LANG, "auto");
        boolean useZh;
        if ("zh".equals(saved)) {
            useZh = true;
        } else if ("en".equals(saved)) {
            useZh = false;
        } else {
            useZh = Locale.getDefault().getLanguage().startsWith("zh");
        }
        return new Strings(useZh);
    }

    public static Strings toggle(Context ctx) {
        Strings current = load(ctx);
        String next = current.zh ? "en" : "zh";
        ctx.getSharedPreferences(CredentialStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LANG, next)
                .apply();
        return new Strings(!current.zh);
    }

    public boolean isZh() {
        return zh;
    }

    public String langToggleLabel() {
        return zh ? "EN" : "中文";
    }

    public String appTitle() {
        return zh ? "凭证服务管理" : "Credential Switcher";
    }

    public String appSubtitle() {
        return zh
                ? "Android 14–17 / HyperOS 通行密钥「首选 + 其他服务」离线控制台"
                : "Offline Passkey Preferred & Additional Services Controller";
    }

    public String permGrantedPill() {
        return zh
                ? "● 已授予 WRITE_SECURE_SETTINGS · 离线永久生效"
                : "● WRITE_SECURE_SETTINGS Granted · Offline Persistent";
    }

    public String readLimitedNote() {
        return zh
                ? "Android 15+ 禁止第三方直接读取凭证服务列表，当前由本面板同步记录状态（所有写入均实时生效）"
                : "Android 15+ restricts reading credential provider lists; this panel tracks mirrored state (all writes take effect immediately)";
    }

    public String permMissingTitle() {
        return zh
                ? "尚未授予 WRITE_SECURE_SETTINGS 权限（当前仅可查看）"
                : "WRITE_SECURE_SETTINGS Not Granted (Read-Only Mode)";
    }

    public String permMissingHint(String adbCmd) {
        return (zh ? "请在电脑 ADB 执行一次：\n" : "Run once from PC via ADB:\n") + adbCmd;
    }

    public String copyAdbCmdBtn() {
        return zh ? "复制 ADB 授权命令" : "Copy ADB Grant Command";
    }

    public String adbCmdCopiedToast() {
        return zh ? "已复制 ADB 授权命令" : "ADB grant command copied";
    }

    public String ghostTitle() {
        return zh ? "⚠ 检测到异常/已失效服务指针" : "⚠ Stale or Broken Service Pointers Detected";
    }

    public String ghostBody(String list) {
        return (zh
                ? "以下配置指向系统中不存在的服务组件，可能导致 Passkey 登录转圈失效：\n"
                : "The following entries point to missing services and may break Passkey prompts:\n") + list;
    }

    public String cleanGhostBtn() {
        return zh ? "一键清理异常指针" : "Clean Broken Pointers";
    }

    public String ghostCleanedToast() {
        return zh ? "已清理无效服务指针" : "Cleaned broken service pointers";
    }

    public String primarySectionTitle() {
        return zh ? "首选服务（通行密钥 + 自动填充联动）" : "Preferred Service (Passkey + Autofill)";
    }

    public String primarySectionHint() {
        return zh ? "主力密码管理器" : "Primary Provider";
    }

    public String primaryNoneTitle() {
        return zh ? "无（关闭首选凭证服务）" : "None (Disable Preferred Service)";
    }

    public String primaryNoneSub() {
        return zh ? "不使用任何 Passkey 首选服务" : "Do not set a preferred Passkey provider";
    }

    public String additionalSectionTitle() {
        return zh ? "其他服务（通行密钥多选开关）" : "Additional Services (Multi-Select Passkeys)";
    }

    public String additionalCount(int enabled, int total) {
        return zh ? ("已开启 " + enabled + " / " + total) : ("Enabled " + enabled + " / " + total);
    }

    public String noCredServices() {
        return zh ? "未扫描到已安装的 CredentialProviderService" : "No CredentialProviderService apps found";
    }

    public String primaryBadge() {
        return zh ? "首选" : "Primary";
    }

    public String autofillSectionTitle() {
        return zh ? "自动填充服务（独立微调）" : "Autofill Service (Independent Override)";
    }

    public String autofillSectionHint() {
        return zh ? "通常随首选服务联动" : "Usually linked to Preferred";
    }

    public String autofillNoneTitle() {
        return zh ? "无（关闭自动填充）" : "None (Disable Autofill)";
    }

    public String autofillNoneSub() {
        return zh ? "仅禁用密码框 Autofill，不影响 Passkey" : "Disables password autofill only; Passkeys unchanged";
    }

    public String actionsSectionTitle() {
        return zh ? "快捷操作与底层配置核对" : "Quick Actions & Raw State";
    }

    public String reapplyBtn() {
        return zh ? "一键重刷配置到系统（修复升级重置）" : "Re-Apply Config to System (Fix OTA Reset)";
    }

    public String refreshBtn() {
        return zh ? "刷新状态" : "Refresh";
    }

    public String refreshedToast() {
        return zh ? "已刷新系统状态" : "Refreshed system state";
    }

    public String systemPickerBtn() {
        return zh ? "系统首选服务页" : "System Settings";
    }

    public String pickerFailedToast(String errName) {
        return (zh ? "系统未开放直接跳转入口：" : "System settings entry unavailable: ") + errName;
    }

    public String labelLiveRead() {
        return zh ? "系统直读" : "System Live";
    }

    public String labelMirrored() {
        return zh ? "面板同步" : "Panel Mirror";
    }

    public String rawBottomNote() {
        return zh
                ? "提示：系统仅开放 autofill_service 直读；上方两项以本面板同步记录为准。"
                : "Note: Only autofill_service is readable by non-system apps; the two keys above reflect panel-mirrored state.";
    }

    public String copyConfigBtn() {
        return zh ? "复制当前配置" : "Copy Snapshot";
    }

    public String configCopiedToast() {
        return zh ? "已复制底层配置" : "Copied configuration snapshot";
    }

    public String faqHeader(boolean expanded) {
        if (zh) {
            return (expanded ? "▼ " : "▶ ") + "HyperOS / 国产系统常见问题排查";
        }
        return (expanded ? "▼ " : "▶ ") + "HyperOS & Android Passkey Troubleshooting";
    }

    public String faqBody() {
        if (zh) {
            return "1. 为何有时 Passkey 仍弹出谷歌密码管理器？\n"
                    + "   部分系统（如 HyperOS 4）自带的选择器在第三方密码库处于锁定状态或没有对应通行密钥时，会直接取消请求，浏览器随之退回 Google 兜底。请先解锁 Bitwarden/Keyguard，并确保已授予自启动、无限制省电与后台锁定。\n\n"
                    + "2. 为何有时直接单条确认，有时出现多选列表？\n"
                    + "   仅当开启多个「其他服务」且其中至少 2 个已解锁并保存有当前网站密钥时，系统才会弹出多选切换列表；仅 1 条可用时直接显示单条确认框。\n\n"
                    + "3. 授完权后可以关闭 USB 调试吗？\n"
                    + "   可以。pm grant 授予的 WRITE_SECURE_SETTINGS 会在关闭开发者选项及重启后永久保留；仅系统 OTA 重置配置时，点上方「一键重刷」即可恢复。";
        }
        return "1. Why does Passkey login sometimes fall back to Google Password Manager?\n"
                + "   On some OEM ROMs (e.g., HyperOS 4), the native selector drops locked-vault auth entries when no unlocked manager returns a matching passkey, causing browsers to fall back to Google. Keep your vault unlocked and grant autostart + unrestricted battery.\n\n"
                + "2. When does the multi-provider picker appear?\n"
                + "   Only when >= 2 enabled Credential Services are unlocked and hold matching passkeys for the target domain. A single matching entry shows a direct confirmation prompt.\n\n"
                + "3. Can I disable USB Debugging after granting permission?\n"
                + "   Yes. WRITE_SECURE_SETTINGS granted via ADB persists across reboots and after turning off Developer Options. If an OTA resets provider defaults, tap 'Re-Apply Config to System' above.";
    }

    public String switchContentDesc(String label) {
        return (zh ? "切换其他服务:" : "Toggle additional service: ") + label;
    }

    public String toastPrimaryCleared() {
        return zh ? "已关闭首选服务与自动填充" : "Cleared preferred & autofill services";
    }

    public String toastPrimarySet(String label) {
        return (zh ? "首选服务已设为 " : "Preferred service set to ") + label;
    }

    public String toastAutofillCleared() {
        return zh ? "已关闭自动填充服务" : "Disabled autofill service";
    }

    public String toastAutofillSet(String label) {
        return (zh ? "自动填充已切换为 " : "Autofill switched to ") + label;
    }

    public String toastToggledOffPrimary(String label) {
        return zh ? ("已关闭 " + label + "（同时取消首选）") : ("Disabled " + label + " (also cleared preferred)");
    }

    public String toastToggled(String label, boolean enable) {
        if (zh) {
            return (enable ? "已开启 " : "已关闭 ") + label;
        }
        return (enable ? "Enabled " : "Disabled ") + label;
    }

    public String toastReappliedAll() {
        return zh ? "已重刷全部 3 项配置到系统" : "Re-applied all 3 settings to system";
    }

    public String toastMissingPerm() {
        return zh
                ? "缺少 WRITE_SECURE_SETTINGS 权限，请先通过 ADB 授权"
                : "Missing WRITE_SECURE_SETTINGS permission; grant once via ADB";
    }

    public String toastWriteDenied(String msg) {
        return (zh ? "写入被系统拒绝：" : "Write denied by system: ") + msg;
    }

    public String toastWriteFailed(String msg) {
        return (zh ? "写入失败：" : "Write failed: ") + msg;
    }
}
