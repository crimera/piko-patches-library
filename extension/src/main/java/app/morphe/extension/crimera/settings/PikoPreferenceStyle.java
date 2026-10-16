package app.morphe.extension.crimera.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.crimera.theme.SettingsFont;

/** Row layout and styling of the settings preference list; colors and fonts come from {@link PikoTheme}. */
final class PikoPreferenceStyle {
    private enum TrailingAccessory {
        NONE,
        SWITCH,
        CHEVRON,
        DOTS,
    }

    private static final String TAG_LEADING = "piko_pref_leading";
    private static final String TAG_TITLE = "piko_pref_title";
    private static final String TAG_VALUE = "piko_pref_value";
    private static final String TAG_SUMMARY = "piko_pref_summary";
    private static final String TAG_SWITCH = "piko_pref_switch";
    private static final String TAG_CHEVRON = "piko_pref_chevron";
    private static final int[] TITLE_TEXT_SIZES = {13, 14, 15, 16, 17, 18};
    private static final int[] SUMMARY_TEXT_SIZES = {12, 13, 14, 15, 16, 17};

    private PikoPreferenceStyle() {
    }

    static int backgroundColor(Context context) {
        return PikoTheme.surfaceContainer(context);
    }

    private static int primaryTextColor(Context context) {
        return PikoTheme.primaryText(context);
    }

    private static int secondaryTextColor(Context context) {
        return PikoTheme.secondaryText(context);
    }

    private static int disabledColor(Context context) {
        return PikoTheme.blend(backgroundColor(context), secondaryTextColor(context), 0.45f);
    }

    private static int dp(Context context, float value) {
        return PikoTheme.dpToPx(context, value);
    }

    private static View createRow(
            Context context,
            TrailingAccessory trailingAccessory,
            boolean navigation
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(dp(context, 24), dp(context, 10), dp(context, 24), dp(context, 10));
        applyRippleBackground(row);
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if (navigation) {
            ImageView leading = new ImageView(context);
            leading.setTag(TAG_LEADING);
            leading.setScaleType(ImageView.ScaleType.FIT_CENTER);
            leading.setVisibility(View.GONE);
            LinearLayout.LayoutParams leadingParams = new LinearLayout.LayoutParams(
                    dp(context, 24),
                    dp(context, 24)
            );
            leadingParams.setMarginEnd(dp(context, 16));
            row.addView(leading, leadingParams);
        }

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        textParams.setMarginEnd(dp(context, 14));
        row.addView(textContainer, textParams);
        addTitle(context, textContainer, 0f, navigation);

        TextView summary = new TextView(context);
        summary.setTag(TAG_SUMMARY);
        summary.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                SUMMARY_TEXT_SIZES[typographyScale(context)]
        );
        summary.setTypeface(PikoTheme.typeface(context, SettingsFont.ROW_SUMMARY, Typeface.DEFAULT));
        summary.setTextColor(secondaryTextColor(context));
        summary.setLineSpacing(dp(context, 1), 1f);
        summary.setPadding(0, dp(context, 2), 0, 0);
        textContainer.addView(summary, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (trailingAccessory == TrailingAccessory.DOTS || trailingAccessory == TrailingAccessory.CHEVRON) {
            TextView value = new TextView(context);
            value.setTag(TAG_VALUE);
            value.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    TITLE_TEXT_SIZES[typographyScale(context)]
            );
            value.setTypeface(PikoTheme.typeface(context, SettingsFont.ROW_SUMMARY, Typeface.DEFAULT));
            value.setTextColor(secondaryTextColor(context));
            value.setGravity(Gravity.END);
            value.setMaxWidth(context.getResources().getDisplayMetrics().widthPixels * 11 / 20);
            value.setVisibility(View.GONE);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            valueParams.setMarginEnd(dp(context, 8));
            row.addView(value, valueParams);
        }
        if (trailingAccessory == TrailingAccessory.SWITCH) {
            SettingsUi.SwitchControl switchView =
                    new SettingsUi.SwitchControl(context);
            switchView.setTag(TAG_SWITCH);
            row.addView(switchView, new LinearLayout.LayoutParams(
                    dp(context, 52),
                    dp(context, 32)
            ));
        }
        if (trailingAccessory == TrailingAccessory.DOTS) {
            ImageView dots = new ImageView(context);
            dots.setImageDrawable(new DotsDrawable(secondaryTextColor(context)));
            row.addView(dots, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        }
        if (trailingAccessory == TrailingAccessory.CHEVRON) {
            ImageView chevron = new ImageView(context);
            chevron.setTag(TAG_CHEVRON);
            chevron.setImageDrawable(new ChevronDrawable(
                    secondaryTextColor(context),
                    Math.round(dp(context, 3) / 2f)
            ));
            row.addView(chevron, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));
        }
        return row;
    }

    private static void applyRippleBackground(View view) {
        SettingsUi.applyRippleBackground(view);
    }

    private static void addTitle(
            Context context,
            LinearLayout parent,
            float weight,
            boolean navigation
    ) {
        TextView title = new TextView(context);
        title.setTag(TAG_TITLE);
        title.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                TITLE_TEXT_SIZES[typographyScale(context)]
        );
        title.setTypeface(PikoTheme.typeface(
                context,
                SettingsFont.ROW_TITLE,
                Typeface.create(navigation ? "sans-serif" : "sans-serif-medium", Typeface.NORMAL)
        ));
        title.setTextColor(primaryTextColor(context));
        title.setSingleLine(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                weight == 0f ? LinearLayout.LayoutParams.MATCH_PARENT : 0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
        if (weight != 0f) params.setMarginEnd(dp(context, 14));
        parent.addView(title, params);
    }

    private static int typographyScale(Context context) {
        int width = context.getResources().getConfiguration().smallestScreenWidthDp;
        if (width >= 720) return 5;
        if (width >= 600) return 4;
        if (width >= 480) return 3;
        if (width >= 400) return 2;
        if (width >= 320) return 1;
        return 0;
    }

    private static void bind(Preference preference, View view) {
        boolean enabled = preference.isEnabled();
        ImageView leading = view.findViewWithTag(TAG_LEADING);
        TextView title = view.findViewWithTag(TAG_TITLE);
        TextView summary = view.findViewWithTag(TAG_SUMMARY);
        int disabled = disabledColor(view.getContext());

        if (leading != null) {
            Drawable icon = preference.getIcon();
            leading.setImageDrawable(icon);
            leading.setVisibility(icon == null ? View.GONE : View.VISIBLE);
            leading.setColorFilter(
                    enabled ? secondaryTextColor(view.getContext()) : disabled,
                    PorterDuff.Mode.SRC_IN
            );
        }
        if (title != null) {
            title.setText(preference.getTitle());
            title.setTextColor(enabled ? primaryTextColor(view.getContext()) : disabled);
        }
        TextView value = view.findViewWithTag(TAG_VALUE);
        if (value != null) {
            CharSequence text = preference instanceof ValueRow row ? row.valueText() : null;
            boolean visible = !TextUtils.isEmpty(text);
            value.setText(visible ? text : "");
            value.setVisibility(visible ? View.VISIBLE : View.GONE);
            value.setTextColor(enabled ? secondaryTextColor(view.getContext()) : disabled);
        }
        if (summary != null) {
            CharSequence text = preference.getSummary();
            boolean visible = !TextUtils.isEmpty(text);
            summary.setText(visible ? text : "");
            summary.setVisibility(visible ? View.VISIBLE : View.GONE);
            summary.setTextColor(enabled ? secondaryTextColor(view.getContext()) : disabled);
        }
        view.setEnabled(enabled);
    }

    static final class Toggle extends SwitchPreference {
        private SettingsUi.SwitchControl switchView;
        private boolean animateChange;
        private boolean previousChecked;

        Toggle(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.SWITCH, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
            switchView = view.findViewWithTag(TAG_SWITCH);
            if (switchView == null) return;
            switchView.setEnabled(isEnabled());
            if (animateChange) {
                switchView.setChecked(previousChecked, false);
                switchView.setChecked(isChecked(), true);
                animateChange = false;
                return;
            }
            if (!switchView.isAnimating()) switchView.setChecked(isChecked(), false);
        }

        @Override
        protected void onClick() {
            boolean checkedBeforeClick = isChecked();
            super.onClick();
            if (checkedBeforeClick == isChecked()) return;
            previousChecked = checkedBeforeClick;
            animateChange = true;
            notifyChanged();
        }
    }

    static final class TextInput extends Preference {
        TextInput(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.DOTS, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    /** A row that shows its current value to the left of the trailing accessory. */
    private interface ValueRow {
        CharSequence valueText();
    }

    static final class SingleChoice extends Preference implements ValueRow {
        private CharSequence valueText;

        SingleChoice(Context context) {
            super(context);
        }

        void setValueText(CharSequence valueText) {
            this.valueText = valueText;
            notifyChanged();
        }

        @Override
        public CharSequence valueText() {
            return valueText;
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.DOTS, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class MultiChoice extends Preference {
        MultiChoice(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.DOTS, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class Action extends Preference {
        Action(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.NONE, false);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    static final class Navigation extends Preference {
        Navigation(Context context) {
            super(context);
        }

        @Override
        protected View onCreateView(ViewGroup parent) {
            return createRow(getContext(), TrailingAccessory.CHEVRON, true);
        }

        @Override
        protected void onBindView(View view) {
            bind(this, view);
        }
    }

    /** Right-pointing chevron drawn as a stroked path, so it needs no app resource. */
    private static final class ChevronDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChevronDrawable(int color, int strokeWidth) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float size = Math.min(bounds.width(), bounds.height()) * 0.26f;
            Path path = new Path();
            path.moveTo(cx - size / 2f, cy - size);
            path.lineTo(cx + size / 2f, cy);
            path.lineTo(cx - size / 2f, cy + size);
            canvas.drawPath(path, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Vertical three-dot marker for rows that open a dialog or sheet. */
    private static final class DotsDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DotsDrawable(int color) {
            paint.setColor(color);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            // X's overflow vector: r=2 dots centered 7 units apart in a 24-unit box.
            float unit = Math.min(bounds.width(), bounds.height()) / 24f;
            canvas.drawCircle(cx, cy - 7 * unit, 2 * unit, paint);
            canvas.drawCircle(cx, cy, 2 * unit, paint);
            canvas.drawCircle(cx, cy + 7 * unit, 2 * unit, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
