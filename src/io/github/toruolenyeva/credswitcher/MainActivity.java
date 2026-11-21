package io.github.toruolenyeva.credswitcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/**
 * Single-screen UI controller for CredentialProviderService and AutofillService switching.
 */
public class MainActivity extends Activity {

    private ScrollView scrollView;
    private LinearLayout contentColumn;
    private CredentialStore store;
    private UiKit ui;
    private Strings s;
    private boolean faqExpanded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        store = new CredentialStore(this);
        ui = new UiKit(this);
        s = Strings.load(this);

        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ui.cBg);
        scrollView.setClipToPadding(false);
        scrollView.setFillViewport(true);

        contentColumn = new LinearLayout(this);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(contentColumn, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final int baseH = ui.dp(16);
        final int baseTop = ui.dp(12);
        final int baseBottom = ui.dp(28);
        contentColumn.setPadding(baseH, baseTop, baseH, baseBottom);

        scrollView.setOnApplyWindowInsetsListener((v, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars());
                left = sys.left;
                top = sys.top;
                right = sys.right;
                bottom = sys.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            contentColumn.setPadding(baseH + left, baseTop + top, baseH + right, baseBottom + bottom);
            return insets;
        });

        setContentView(scrollView);
        ui.applySystemBarColors(this);
        rebuildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui = new UiKit(this);
        s = Strings.load(this);
        ui.applySystemBarColors(this);
        if (scrollView != null) {
            scrollView.setBackgroundColor(ui.cBg);
        }
        rebuildUi();
    }

    private void rebuildUi() {
        if (contentColumn == null) return;
        final int prevScrollY = scrollView != null ? scrollView.getScrollY() : 0;
        contentColumn.removeAllViews();

        final List<CredentialStore.ServiceItem> credProviders =
                store.queryServices(CredentialStore.ACTION_CREDENTIAL_PROVIDER);
        final List<CredentialStore.ServiceItem> autofillProviders =
                store.queryServices(CredentialStore.ACTION_AUTOFILL_SERVICE);
        final CredentialStore.StateSnapshot state = store.loadSnapshot(credProviders);

        addHeaderSection(state);
        if (!state.ghostPointers.isEmpty()) {
            addGhostPointerCard(state.ghostPointers);
        }
        addPrimarySection(credProviders, autofillProviders, state);
        addAdditionalSection(credProviders, state);
        addAutofillSection(autofillProviders, state);
        addActionsAndRawStateSection(state);
        addFaqSection();

        if (scrollView != null && prevScrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, prevScrollY));
        }
    }

    private void addHeaderSection(CredentialStore.StateSnapshot state) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(ui.dp(2), 0, ui.dp(2), ui.dp(14));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(s.appTitle());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ui.cText);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView langBtn = ui.makePill(s.langToggleLabel(), ui.cCardAlt, ui.cBorder, ui.cText);
        langBtn.setClickable(true);
        langBtn.setFocusable(true);
        langBtn.setOnClickListener(v -> {
            s = Strings.toggle(this);
            rebuildUi();
        });
        titleRow.addView(langBtn, ui.wrapParams());
        header.addView(titleRow);

        TextView subtitle = new TextView(this);
        subtitle.setText(s.appSubtitle());
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(ui.cSubtext);
        subtitle.setPadding(0, ui.dp(4), 0, ui.dp(10));
        header.addView(subtitle);

        if (state.hasPermission) {
            TextView pill = new TextView(this);
            pill.setText(s.permGrantedPill());
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setTextColor(ui.cSuccessText);
            pill.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
            pill.setBackground(ui.roundRect(ui.cSuccessBg, 0, ui.dp(20)));
            header.addView(pill, ui.wrapParams());

            if (state.readLimited) {
                TextView syncNote = new TextView(this);
                syncNote.setText(s.readLimitedNote());
                syncNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                syncNote.setTextColor(ui.cSubtext);
                syncNote.setPadding(ui.dp(2), ui.dp(8), ui.dp(2), 0);
                header.addView(syncNote);
            }
        } else {
            LinearLayout warn = ui.makeCard(ui.cDangerBg, ui.cDangerText);
            TextView wTitle = new TextView(this);
            wTitle.setText(s.permMissingTitle());
            wTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            wTitle.setTypeface(Typeface.DEFAULT_BOLD);
            wTitle.setTextColor(ui.cDangerText);
            warn.addView(wTitle);

            final String adbCmd = "adb shell pm grant " + getPackageName() + " " + CredentialStore.PERM_WRITE_SECURE;
            TextView wCmd = new TextView(this);
            wCmd.setText(s.permMissingHint(adbCmd));
            wCmd.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            wCmd.setTypeface(Typeface.MONOSPACE);
            wCmd.setTextColor(ui.cText);
            wCmd.setPadding(0, ui.dp(6), 0, ui.dp(10));
            warn.addView(wCmd);

            TextView copyBtn = ui.makeActionChip(s.copyAdbCmdBtn(), ui.cDangerText, Color.WHITE);
            copyBtn.setOnClickListener(v -> copyText("ADB Grant", adbCmd, s.adbCmdCopiedToast()));
            warn.addView(copyBtn, ui.wrapParams());
            header.addView(warn, ui.marginBottom(ui.dp(4)));
        }

        contentColumn.addView(header);
    }

    private void addGhostPointerCard(List<String> ghosts) {
        LinearLayout card = ui.makeCard(ui.cWarnBg, ui.cWarnText);
        TextView title = new TextView(this);
        title.setText(s.ghostTitle());
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ui.cWarnText);
        card.addView(title);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ghosts.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append("• ").append(ghosts.get(i));
        }
        TextView body = new TextView(this);
        body.setText(s.ghostBody(sb.toString()));
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setTextColor(ui.cText);
        body.setPadding(0, ui.dp(6), 0, ui.dp(10));
        card.addView(body);

        TextView fixBtn = ui.makeActionChip(s.cleanGhostBtn(), ui.cWarnText, Color.WHITE);
        fixBtn.setOnClickListener(v -> {
            CredentialStore.ActionResult res = store.cleanGhostPointers(s);
            if (!res.message.isEmpty()) toast(res.message);
            rebuildUi();
        });
        card.addView(fixBtn, ui.wrapParams());

        contentColumn.addView(card, ui.marginBottom(ui.dp(16)));
    }

    private void addPrimarySection(
            List<CredentialStore.ServiceItem> credProviders,
            List<CredentialStore.ServiceItem> autofillProviders,
            CredentialStore.StateSnapshot state) {

        addSectionHeader(s.primarySectionTitle(), s.primarySectionHint());

        LinearLayout card = ui.makeCard(ui.cCard, ui.cBorder);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        addRadioRow(card, null, s.primaryNoneTitle(), s.primaryNoneSub(),
                state.primaryCn == null, true, v -> {
                    CredentialStore.ActionResult res =
                            store.selectPrimary(null, autofillProviders, state.enabledCreds, s);
                    if (!res.message.isEmpty()) toast(res.message);
                    rebuildUi();
                });

        for (CredentialStore.ServiceItem item : credProviders) {
            boolean selected = state.primaryCn != null && state.primaryCn.equals(item.component);
            addRadioRow(card, item.icon, item.label, item.packageName, selected, false, v -> {
                CredentialStore.ActionResult res =
                        store.selectPrimary(item, autofillProviders, state.enabledCreds, s);
                if (!res.message.isEmpty()) toast(res.message);
                rebuildUi();
            });
        }

        contentColumn.addView(card, ui.marginBottom(ui.dp(18)));
    }

    private void addAdditionalSection(
            List<CredentialStore.ServiceItem> credProviders,
            CredentialStore.StateSnapshot state) {

        int enabledCount = 0;
        for (CredentialStore.ServiceItem item : credProviders) {
            if (state.enabledCreds.contains(item.component)) enabledCount++;
        }
        addSectionHeader(s.additionalSectionTitle(), s.additionalCount(enabledCount, credProviders.size()));

        LinearLayout card = ui.makeCard(ui.cCard, ui.cBorder);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        if (credProviders.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(s.noCredServices());
            empty.setTextColor(ui.cSubtext);
            empty.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            card.addView(empty);
        } else {
            for (int i = 0; i < credProviders.size(); i++) {
                CredentialStore.ServiceItem item = credProviders.get(i);
                boolean isPrimary = state.primaryCn != null && state.primaryCn.equals(item.component);
                boolean isChecked = state.enabledCreds.contains(item.component);
                addSwitchRow(card, item, isPrimary, isChecked, i == 0, credProviders, state.autofillCn);
            }
        }

        contentColumn.addView(card, ui.marginBottom(ui.dp(18)));
    }

    private void addAutofillSection(
            List<CredentialStore.ServiceItem> autofillProviders,
            CredentialStore.StateSnapshot state) {

        addSectionHeader(s.autofillSectionTitle(), s.autofillSectionHint());

        LinearLayout card = ui.makeCard(ui.cCard, ui.cBorder);
        card.setPadding(0, ui.dp(4), 0, ui.dp(4));

        addRadioRow(card, null, s.autofillNoneTitle(), s.autofillNoneSub(),
                state.autofillCn == null, true, v -> {
                    CredentialStore.ActionResult res = store.setAutofill(null, s);
                    if (!res.message.isEmpty()) toast(res.message);
                    rebuildUi();
                });

        for (CredentialStore.ServiceItem item : autofillProviders) {
            boolean selected = state.autofillCn != null && state.autofillCn.equals(item.component);
            addRadioRow(card, item.icon, item.label, item.component.flattenToShortString(), selected, false, v -> {
                CredentialStore.ActionResult res = store.setAutofill(item, s);
                if (!res.message.isEmpty()) toast(res.message);
                rebuildUi();
            });
        }

        contentColumn.addView(card, ui.marginBottom(ui.dp(18)));
    }

    private void addActionsAndRawStateSection(CredentialStore.StateSnapshot state) {
        addSectionHeader(s.actionsSectionTitle(), null);

        TextView reapplyBtn = ui.makeActionChip(s.reapplyBtn(), ui.cAccent, Color.WHITE);
        reapplyBtn.setOnClickListener(v -> {
            CredentialStore.ActionResult res =
                    store.reapplyAll(state.primaryCn, state.enabledCreds, state.autofillCn, s);
            if (!res.message.isEmpty()) toast(res.message);
            rebuildUi();
        });
        contentColumn.addView(reapplyBtn, ui.marginBottom(ui.dp(10)));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView refreshBtn = ui.makeActionChip(s.refreshBtn(), ui.cCardAlt, ui.cText);
        refreshBtn.setOnClickListener(v -> {
            rebuildUi();
            toast(s.refreshedToast());
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = ui.dp(8);
        btnRow.addView(refreshBtn, lp1);

        TextView sysBtn = ui.makeActionChip(s.systemPickerBtn(), ui.cCardAlt, ui.cText);
        sysBtn.setOnClickListener(v -> openNativePicker());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(sysBtn, lp2);

        contentColumn.addView(btnRow, ui.marginBottom(ui.dp(12)));

        LinearLayout rawCard = ui.makeCard(ui.cCard, ui.cBorder);
        final String primaryTag = state.rawPrimary != null ? s.labelLiveRead() : s.labelMirrored();
        final String listTag = state.rawCredList != null ? s.labelLiveRead() : s.labelMirrored();
        final String primaryShow = state.primaryCn != null ? state.primaryCn.flattenToString() : null;
        final String listShow = CredentialStore.joinComponents(state.enabledCreds);
        final String snapshot = CredentialStore.KEY_CRED_PRIMARY + " (" + primaryTag + "):\n  "
                + CredentialStore.valueOrNull(primaryShow)
                + "\n\n" + CredentialStore.KEY_CRED_LIST + " (" + listTag + "):\n  "
                + CredentialStore.valueOrNull(listShow)
                + "\n\n" + CredentialStore.KEY_AUTOFILL + " (" + s.labelLiveRead() + "):\n  "
                + CredentialStore.valueOrNull(state.rawAutofill);

        TextView mono = new TextView(this);
        mono.setText(snapshot);
        mono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        mono.setTypeface(Typeface.MONOSPACE);
        mono.setTextColor(ui.cSubtext);
        mono.setPadding(0, 0, 0, ui.dp(10));
        rawCard.addView(mono);

        if (state.readLimited) {
            TextView note = new TextView(this);
            note.setText(s.rawBottomNote());
            note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            note.setTextColor(ui.cSubtext);
            note.setPadding(0, 0, 0, ui.dp(10));
            rawCard.addView(note);
        }

        TextView copyCfg = ui.makeActionChip(s.copyConfigBtn(), ui.cCardAlt, ui.cText);
        copyCfg.setOnClickListener(v -> copyText("Settings.Secure Snapshot", snapshot, s.configCopiedToast()));
        rawCard.addView(copyCfg, ui.wrapParams());

        contentColumn.addView(rawCard, ui.marginBottom(ui.dp(14)));
    }

    private void addFaqSection() {
        LinearLayout card = ui.makeCard(ui.cCard, ui.cBorder);
        TextView toggle = new TextView(this);
        toggle.setText(s.faqHeader(faqExpanded));
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setTextColor(ui.cText);
        toggle.setBackground(ui.rowRipple());
        toggle.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setOnClickListener(v -> {
            faqExpanded = !faqExpanded;
            rebuildUi();
        });
        card.addView(toggle);

        if (faqExpanded) {
            TextView body = new TextView(this);
            body.setText(s.faqBody());
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            body.setTextColor(ui.cSubtext);
            body.setLineSpacing(ui.dp(3), 1f);
            body.setPadding(ui.dp(2), ui.dp(10), ui.dp(2), ui.dp(2));
            card.addView(body);
        }

        contentColumn.addView(card, ui.marginBottom(ui.dp(8)));
    }

    private void addSectionHeader(String titleText, String rightText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(8));

        TextView left = new TextView(this);
        left.setText(titleText);
        left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        left.setTypeface(Typeface.DEFAULT_BOLD);
        left.setTextColor(ui.cSubtext);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!TextUtils.isEmpty(rightText)) {
            TextView right = new TextView(this);
            right.setText(rightText);
            right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            right.setTextColor(ui.cSubtext);
            row.addView(right);
        }

        contentColumn.addView(row);
    }

    private void addRadioRow(
            LinearLayout parent,
            Drawable icon,
            String title,
            String subtitle,
            boolean selected,
            boolean isFirst,
            View.OnClickListener onClick) {

        if (!isFirst) {
            parent.addView(ui.makeDivider());
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        row.setBackground(ui.rowRipple());
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(onClick);

        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34));
            ilp.rightMargin = ui.dp(12);
            row.addView(iv, ilp);
        } else {
            TextView placeholder = new TextView(this);
            placeholder.setText("∅");
            placeholder.setGravity(Gravity.CENTER);
            placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            placeholder.setTextColor(ui.cSubtext);
            placeholder.setBackground(ui.roundRect(ui.cCardAlt, 0, ui.dp(10)));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34));
            ilp.rightMargin = ui.dp(12);
            row.addView(placeholder, ilp);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTextColor(selected ? ui.cAccent : ui.cText);
        if (selected) tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSub.setTextColor(ui.cSubtext);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        textCol.addView(tvSub);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.rightMargin = ui.dp(10);
        row.addView(textCol, tlp);

        TextView radio = new TextView(this);
        radio.setGravity(Gravity.CENTER);
        radio.setText(selected ? "✓" : "");
        radio.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        radio.setTypeface(Typeface.DEFAULT_BOLD);
        radio.setTextColor(Color.WHITE);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (selected) {
            circle.setColor(ui.cAccent);
        } else {
            circle.setColor(Color.TRANSPARENT);
            circle.setStroke(ui.dp(1), ui.cSubtext);
        }
        radio.setBackground(circle);
        row.addView(radio, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));

        parent.addView(row);
    }

    private void addSwitchRow(
            LinearLayout parent,
            CredentialStore.ServiceItem item,
            boolean isPrimary,
            boolean isChecked,
            boolean isFirst,
            List<CredentialStore.ServiceItem> credProviders,
            ComponentName autofillCn) {

        if (!isFirst) {
            parent.addView(ui.makeDivider());
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        row.setBackground(ui.rowRipple());
        row.setClickable(true);
        row.setFocusable(true);

        if (item.icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(item.icon);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34));
            ilp.rightMargin = ui.dp(12);
            row.addView(iv, ilp);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(item.label);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvTitle.setTextColor(ui.cText);
        tvTitle.setTypeface(isChecked ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        titleRow.addView(tvTitle);

        if (isPrimary) {
            TextView badge = new TextView(this);
            badge.setText(s.primaryBadge());
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setTextColor(ui.cAccent);
            badge.setPadding(ui.dp(6), ui.dp(1), ui.dp(6), ui.dp(1));
            badge.setBackground(ui.roundRect(ui.cPrimaryBadgeBg, 0, ui.dp(6)));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.leftMargin = ui.dp(8);
            titleRow.addView(badge, blp);
        }

        textCol.addView(titleRow);

        TextView tvSub = new TextView(this);
        tvSub.setText(item.packageName);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSub.setTextColor(ui.cSubtext);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        textCol.addView(tvSub);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.rightMargin = ui.dp(10);
        row.addView(textCol, tlp);

        final Switch sw = new Switch(this);
        sw.setContentDescription(s.switchContentDesc(item.label));
        sw.setChecked(isChecked);
        if (Build.VERSION.SDK_INT >= 24) {
            sw.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            sw.setTrackTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{ui.cAccent, ui.cSwitchTrackOff}
            ));
        }
        sw.setOnCheckedChangeListener((buttonView, checked) -> {
            CredentialStore.ActionResult res =
                    store.toggleAdditional(item, checked, credProviders, autofillCn, s);
            if (!res.message.isEmpty()) toast(res.message);
            rebuildUi();
        });
        row.setOnClickListener(v -> sw.toggle());
        row.addView(sw);

        parent.addView(row);
    }

    private void openNativePicker() {
        try {
            Intent intent = new Intent("android.settings.REQUEST_SET_AUTOFILL_SERVICE");
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent("android.settings.CREDENTIAL_PROVIDER");
                fallback.setData(Uri.parse("package:" + getPackageName()));
                startActivity(fallback);
            } catch (Exception e2) {
                toast(s.pickerFailedToast(e2.getClass().getSimpleName()));
            }
        }
    }

    private void copyText(String label, String text, String message) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            toast(message);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
