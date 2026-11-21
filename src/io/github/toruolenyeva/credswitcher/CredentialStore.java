package io.github.toruolenyeva.credswitcher;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Handles service discovery and Settings.Secure read/write with Android 15+ mirror fallback.
 */
public final class CredentialStore {

    public static final String ACTION_CREDENTIAL_PROVIDER = "android.service.credentials.CredentialProviderService";
    public static final String ACTION_AUTOFILL_SERVICE = "android.service.autofill.AutofillService";
    public static final String PERM_WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS";

    public static final String KEY_AUTOFILL = "autofill_service";
    public static final String KEY_CRED_LIST = "credential_service";
    public static final String KEY_CRED_PRIMARY = "credential_service_primary";

    public static final String PREFS_NAME = "cred_switcher_state";
    private static final String PREF_INIT = "initialized";
    private static final String PREF_PRIMARY = "mirrored_primary";
    private static final String PREF_CRED_LIST = "mirrored_creds";

    private final Context ctx;

    public static final class ServiceItem {
        public final ComponentName component;
        public final String packageName;
        public final String label;
        public final Drawable icon;

        public ServiceItem(ComponentName component, String packageName, String label, Drawable icon) {
            this.component = component;
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    public static final class StateSnapshot {
        public final boolean hasPermission;
        public final boolean readLimited;
        public final ComponentName primaryCn;
        public final Set<ComponentName> enabledCreds;
        public final ComponentName autofillCn;
        public final String rawPrimary;
        public final String rawCredList;
        public final String rawAutofill;
        public final List<String> ghostPointers;

        public StateSnapshot(
                boolean hasPermission,
                boolean readLimited,
                ComponentName primaryCn,
                Set<ComponentName> enabledCreds,
                ComponentName autofillCn,
                String rawPrimary,
                String rawCredList,
                String rawAutofill,
                List<String> ghostPointers) {
            this.hasPermission = hasPermission;
            this.readLimited = readLimited;
            this.primaryCn = primaryCn;
            this.enabledCreds = enabledCreds;
            this.autofillCn = autofillCn;
            this.rawPrimary = rawPrimary;
            this.rawCredList = rawCredList;
            this.rawAutofill = rawAutofill;
            this.ghostPointers = ghostPointers;
        }
    }

    public static final class ActionResult {
        public final boolean success;
        public final String message;

        public ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public CredentialStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public boolean hasWriteSecurePermission() {
        return ctx.checkSelfPermission(PERM_WRITE_SECURE) == PackageManager.PERMISSION_GRANTED;
    }

    public List<ServiceItem> queryServices(String action) {
        PackageManager pm = ctx.getPackageManager();
        Intent query = new Intent(action);
        int flags = PackageManager.MATCH_ALL | PackageManager.GET_META_DATA;
        List<ResolveInfo> resolved = pm.queryIntentServices(query, flags);
        if (resolved == null) resolved = Collections.emptyList();

        Map<ComponentName, ServiceItem> dedup = new LinkedHashMap<>();
        for (ResolveInfo ri : resolved) {
            if (ri.serviceInfo == null) continue;
            ComponentName cn = new ComponentName(ri.serviceInfo.packageName, ri.serviceInfo.name);
            if (dedup.containsKey(cn)) continue;

            CharSequence cs = ri.loadLabel(pm);
            String label = cs != null ? cs.toString().trim() : "";
            if (label.isEmpty()) {
                try {
                    label = pm.getApplicationLabel(ri.serviceInfo.applicationInfo).toString();
                } catch (Exception ignored) {
                    label = ri.serviceInfo.packageName;
                }
            }
            Drawable icon = null;
            try {
                icon = ri.loadIcon(pm);
            } catch (Exception ignored) {
            }
            dedup.put(cn, new ServiceItem(cn, ri.serviceInfo.packageName, label, icon));
        }

        List<ServiceItem> items = new ArrayList<>(dedup.values());
        items.sort(Comparator.comparing(o -> o.label.toLowerCase(Locale.ROOT)));
        return items;
    }

    public StateSnapshot loadSnapshot(List<ServiceItem> credProviders) {
        String rawPrimary = readSecure(KEY_CRED_PRIMARY);
        String rawCredList = readSecure(KEY_CRED_LIST);
        String rawAutofill = readSecure(KEY_AUTOFILL);

        boolean readLimited = (rawPrimary == null && rawCredList == null);
        ComponentName autofillCn = parseComponent(rawAutofill);
        ComponentName primaryCn = resolveEffectivePrimary(rawPrimary, autofillCn, credProviders);
        Set<ComponentName> enabledCreds = resolveEffectiveCredSet(rawCredList, primaryCn);
        List<String> ghostPointers = detectGhostPointers(rawPrimary, rawCredList, rawAutofill);

        return new StateSnapshot(
                hasWriteSecurePermission(),
                readLimited,
                primaryCn,
                enabledCreds,
                autofillCn,
                rawPrimary,
                rawCredList,
                rawAutofill,
                ghostPointers
        );
    }

    public ActionResult selectPrimary(
            ServiceItem chosen,
            List<ServiceItem> autofillProviders,
            Set<ComponentName> currentEnabled,
            Strings s) {

        if (chosen == null) {
            ActionResult r1 = writeSecure(KEY_CRED_PRIMARY, null, s);
            if (!r1.success) return r1;
            ActionResult r2 = writeSecure(KEY_AUTOFILL, null, s);
            if (!r2.success) return r2;
            saveMirrorState(null, currentEnabled);
            return new ActionResult(true, s.toastPrimaryCleared());
        }

        Set<ComponentName> nextCreds = new LinkedHashSet<>(currentEnabled);
        nextCreds.add(chosen.component);

        ActionResult rPrimary = writeSecure(KEY_CRED_PRIMARY, chosen.component.flattenToString(), s);
        if (!rPrimary.success) return rPrimary;
        ActionResult rList = writeSecure(KEY_CRED_LIST, joinComponents(nextCreds), s);
        if (!rList.success) return rList;

        ServiceItem matchingAutofill = null;
        for (ServiceItem af : autofillProviders) {
            if (chosen.packageName.equals(af.packageName)) {
                matchingAutofill = af;
                break;
            }
        }
        if (matchingAutofill != null) {
            ActionResult rAf = writeSecure(KEY_AUTOFILL, matchingAutofill.component.flattenToString(), s);
            if (!rAf.success) return rAf;
        }

        saveMirrorState(chosen.component, nextCreds);
        return new ActionResult(true, s.toastPrimarySet(chosen.label));
    }

    public ActionResult toggleAdditional(
            ServiceItem item,
            boolean enable,
            List<ServiceItem> credProviders,
            ComponentName autofillCn,
            Strings s) {

        ComponentName primaryCn = resolveEffectivePrimary(readSecure(KEY_CRED_PRIMARY), autofillCn, credProviders);
        Set<ComponentName> next = new LinkedHashSet<>(resolveEffectiveCredSet(readSecure(KEY_CRED_LIST), primaryCn));

        if (enable) {
            next.add(item.component);
        } else {
            next.remove(item.component);
        }

        ActionResult rList = writeSecure(KEY_CRED_LIST, joinComponents(next), s);
        if (!rList.success) return rList;

        ComponentName nextPrimary = primaryCn;
        if (!enable && primaryCn != null && primaryCn.equals(item.component)) {
            ActionResult rClear = writeSecure(KEY_CRED_PRIMARY, null, s);
            if (rClear.success) {
                nextPrimary = null;
            }
            saveMirrorState(nextPrimary, next);
            return new ActionResult(true, s.toastToggledOffPrimary(item.label));
        }

        if (nextPrimary != null && next.contains(nextPrimary)) {
            writeSecure(KEY_CRED_PRIMARY, nextPrimary.flattenToString(), s);
        }
        saveMirrorState(nextPrimary, next);
        return new ActionResult(true, s.toastToggled(item.label, enable));
    }

    public ActionResult setAutofill(ServiceItem chosen, Strings s) {
        String val = chosen != null ? chosen.component.flattenToString() : null;
        ActionResult r = writeSecure(KEY_AUTOFILL, val, s);
        if (!r.success) return r;
        String msg = chosen == null ? s.toastAutofillCleared() : s.toastAutofillSet(chosen.label);
        return new ActionResult(true, msg);
    }

    public ActionResult reapplyAll(
            ComponentName primaryCn,
            Set<ComponentName> enabledCreds,
            ComponentName autofillCn,
            Strings s) {

        ActionResult r1 = writeSecure(KEY_CRED_PRIMARY, primaryCn != null ? primaryCn.flattenToString() : null, s);
        if (!r1.success) return r1;
        ActionResult r2 = writeSecure(KEY_CRED_LIST, joinComponents(enabledCreds), s);
        if (!r2.success) return r2;
        ActionResult r3 = writeSecure(KEY_AUTOFILL, autofillCn != null ? autofillCn.flattenToString() : null, s);
        if (!r3.success) return r3;

        saveMirrorState(primaryCn, enabledCreds);
        return new ActionResult(true, s.toastReappliedAll());
    }

    public ActionResult cleanGhostPointers(Strings s) {
        String rawPrimary = readSecure(KEY_CRED_PRIMARY);
        String rawList = readSecure(KEY_CRED_LIST);
        String rawAutofill = readSecure(KEY_AUTOFILL);

        ComponentName pCn = parseComponent(rawPrimary);
        if (rawPrimary != null && !rawPrimary.trim().isEmpty() && !isServiceValid(pCn)) {
            ActionResult r = writeSecure(KEY_CRED_PRIMARY, null, s);
            if (!r.success) return r;
        }

        ComponentName aCn = parseComponent(rawAutofill);
        if (rawAutofill != null && !rawAutofill.trim().isEmpty() && !isServiceValid(aCn)) {
            ActionResult r = writeSecure(KEY_AUTOFILL, null, s);
            if (!r.success) return r;
        }

        Set<ComponentName> validList = new LinkedHashSet<>();
        for (ComponentName cn : parseComponentSet(rawList)) {
            if (isServiceValid(cn)) {
                validList.add(cn);
            }
        }
        ActionResult rList = writeSecure(KEY_CRED_LIST, joinComponents(validList), s);
        if (!rList.success) return rList;

        return new ActionResult(true, s.ghostCleanedToast());
    }

    private ComponentName resolveEffectivePrimary(
            String rawPrimary,
            ComponentName autofillCn,
            List<ServiceItem> credProviders) {
        if (rawPrimary != null) {
            ComponentName live = parseComponent(rawPrimary);
            return isServiceValid(live) ? live : null;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_INIT, false)) {
            ComponentName saved = parseComponent(prefs.getString(PREF_PRIMARY, null));
            return isServiceValid(saved) ? saved : null;
        }
        if (autofillCn != null) {
            for (ServiceItem item : credProviders) {
                if (item.packageName.equals(autofillCn.getPackageName())) {
                    return item.component;
                }
            }
        }
        return null;
    }

    private Set<ComponentName> resolveEffectiveCredSet(String rawCredList, ComponentName effectivePrimary) {
        if (rawCredList != null) {
            Set<ComponentName> liveSet = new LinkedHashSet<>();
            for (ComponentName cn : parseComponentSet(rawCredList)) {
                if (isServiceValid(cn)) liveSet.add(cn);
            }
            return liveSet;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_INIT, false)) {
            Set<ComponentName> savedSet = new LinkedHashSet<>();
            for (ComponentName cn : parseComponentSet(prefs.getString(PREF_CRED_LIST, null))) {
                if (isServiceValid(cn)) savedSet.add(cn);
            }
            return savedSet;
        }
        Set<ComponentName> initSet = new LinkedHashSet<>();
        if (effectivePrimary != null) {
            initSet.add(effectivePrimary);
        }
        return initSet;
    }

    private void saveMirrorState(ComponentName primaryCn, Set<ComponentName> creds) {
        String p = primaryCn != null ? primaryCn.flattenToString() : "";
        String list = joinComponents(creds);
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_INIT, true)
                .putString(PREF_PRIMARY, p)
                .putString(PREF_CRED_LIST, list != null ? list : "")
                .apply();
    }

    private List<String> detectGhostPointers(String rawPrimary, String rawCredList, String rawAutofill) {
        Set<String> broken = new LinkedHashSet<>();
        checkSinglePointer(rawPrimary, broken);
        checkSinglePointer(rawAutofill, broken);
        if (!TextUtils.isEmpty(rawCredList)) {
            for (String token : rawCredList.split(":")) {
                checkSinglePointer(token, broken);
            }
        }
        return new ArrayList<>(broken);
    }

    private void checkSinglePointer(String raw, Set<String> out) {
        if (raw == null) return;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return;
        ComponentName cn = ComponentName.unflattenFromString(trimmed);
        if (!isServiceValid(cn)) {
            out.add(trimmed);
        }
    }

    private boolean isServiceValid(ComponentName cn) {
        if (cn == null) return false;
        try {
            ctx.getPackageManager().getServiceInfo(cn, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String readSecure(String key) {
        try {
            return Settings.Secure.getString(ctx.getContentResolver(), key);
        } catch (Exception e) {
            return null;
        }
    }

    private ActionResult writeSecure(String key, String value, Strings s) {
        if (!hasWriteSecurePermission()) {
            return new ActionResult(false, s.toastMissingPerm());
        }
        try {
            ContentResolver cr = ctx.getContentResolver();
            boolean ok = Settings.Secure.putString(cr, key, value);
            return new ActionResult(ok, ok ? "" : s.toastWriteFailed(key));
        } catch (SecurityException se) {
            return new ActionResult(false, s.toastWriteDenied(se.getMessage()));
        } catch (Exception e) {
            return new ActionResult(false, s.toastWriteFailed(e.getMessage()));
        }
    }

    public static ComponentName parseComponent(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        return ComponentName.unflattenFromString(t);
    }

    public static Set<ComponentName> parseComponentSet(String raw) {
        Set<ComponentName> set = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) return set;
        for (String token : raw.split(":")) {
            ComponentName cn = parseComponent(token);
            if (cn != null) set.add(cn);
        }
        return set;
    }

    public static String joinComponents(Set<ComponentName> set) {
        if (set == null || set.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (ComponentName cn : set) {
            if (sb.length() > 0) sb.append(':');
            sb.append(cn.flattenToString());
        }
        return sb.toString();
    }

    public static String valueOrNull(String v) {
        return v == null ? "<null>" : (v.isEmpty() ? "<empty>" : v);
    }
}
