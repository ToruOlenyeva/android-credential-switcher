package io.github.toruolenyeva.credswitcher;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Programmatic UI palette, ripple drawables, and system-bar styling without third-party dependencies.
 */
public final class UiKit {

    private final Context ctx;
    public final boolean isDark;
    public final int cBg;
    public final int cCard;
    public final int cCardAlt;
    public final int cBorder;
    public final int cText;
    public final int cSubtext;
    public final int cAccent = Color.parseColor("#1677FF");
    public final int cSuccessBg;
    public final int cSuccessText;
    public final int cDangerBg;
    public final int cDangerText;
    public final int cWarnBg;
    public final int cWarnText;
    public final int cPrimaryBadgeBg;
    public final int cSwitchTrackOff;
    public final int cRipple;

    public UiKit(Context ctx) {
        this.ctx = ctx;
        int nightFlags = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        this.isDark = nightFlags == Configuration.UI_MODE_NIGHT_YES;
        if (isDark) {
            cBg = Color.parseColor("#000000");
            cCard = Color.parseColor("#1C1C1E");
            cCardAlt = Color.parseColor("#262629");
            cBorder = Color.parseColor("#2C2C2E");
            cText = Color.parseColor("#F2F2F7");
            cSubtext = Color.parseColor("#8E8E93");
            cSuccessBg = Color.parseColor("#112E1D");
            cSuccessText = Color.parseColor("#52C41A");
            cDangerBg = Color.parseColor("#311518");
            cDangerText = Color.parseColor("#FF6B6B");
            cWarnBg = Color.parseColor("#332611");
            cWarnText = Color.parseColor("#FAAD14");
            cPrimaryBadgeBg = Color.parseColor("#112645");
            cSwitchTrackOff = Color.parseColor("#3A3A3C");
            cRipple = Color.parseColor("#24FFFFFF");
        } else {
            cBg = Color.parseColor("#F5F6F8");
            cCard = Color.parseColor("#FFFFFF");
            cCardAlt = Color.parseColor("#F0F2F5");
            cBorder = Color.parseColor("#E5E7EB");
            cText = Color.parseColor("#1A1A1A");
            cSubtext = Color.parseColor("#6B7280");
            cSuccessBg = Color.parseColor("#EAF7EE");
            cSuccessText = Color.parseColor("#1E8E3E");
            cDangerBg = Color.parseColor("#FDEDED");
            cDangerText = Color.parseColor("#D93025");
            cWarnBg = Color.parseColor("#FFF7E6");
            cWarnText = Color.parseColor("#D46B08");
            cPrimaryBadgeBg = Color.parseColor("#E6F4FF");
            cSwitchTrackOff = Color.parseColor("#D1D5DB");
            cRipple = Color.parseColor("#18000000");
        }
    }

    @SuppressWarnings("deprecation")
    public void applySystemBarColors(Activity activity) {
        Window window = activity.getWindow();
        if (window == null) return;
        // Touch decorView before getInsetsController() so PhoneWindow has initialized its root view.
        View decor = window.getDecorView();
        window.setStatusBarColor(cBg);
        window.setNavigationBarColor(cBg);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController ctrl = decor.getWindowInsetsController();
            if (ctrl != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                ctrl.setSystemBarsAppearance(isDark ? 0 : mask, mask);
            }
        } else {
            int flags = decor.getSystemUiVisibility();
            if (isDark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    public LinearLayout makeCard(int bgColor, int strokeColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(roundRect(bgColor, strokeColor, dp(16)));
        return card;
    }

    public TextView makeActionChip(String text, int bgColor, int textColor) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(textColor);
        btn.setPadding(dp(14), dp(11), dp(14), dp(11));
        btn.setBackground(rippleRect(bgColor, 0, dp(12)));
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    public TextView makePill(String text, int bgColor, int strokeColor, int textColor) {
        TextView pill = new TextView(ctx);
        pill.setText(text);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setTextColor(textColor);
        pill.setPadding(dp(12), dp(6), dp(12), dp(6));
        pill.setBackground(rippleRect(bgColor, strokeColor, dp(20)));
        return pill;
    }

    public View makeDivider() {
        View d = new View(ctx);
        d.setBackgroundColor(cBorder);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.leftMargin = dp(60);
        lp.rightMargin = dp(14);
        d.setLayoutParams(lp);
        return d;
    }

    public GradientDrawable roundRect(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(radiusPx);
        gd.setColor(fillColor);
        if (strokeColor != 0) {
            gd.setStroke(1, strokeColor);
        }
        return gd;
    }

    public Drawable rippleRect(int fillColor, int strokeColor, int radiusPx) {
        GradientDrawable content = roundRect(fillColor, strokeColor, radiusPx);
        GradientDrawable mask = roundRect(Color.WHITE, 0, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(cRipple), content, mask);
    }

    public Drawable rowRipple() {
        GradientDrawable mask = roundRect(Color.WHITE, 0, 0);
        return new RippleDrawable(ColorStateList.valueOf(cRipple), null, mask);
    }

    public LinearLayout.LayoutParams marginBottom(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px;
        return lp;
    }

    public LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }
}
