package app.morphe.extension.crimera.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.shared.Utils;

/**
 * Hosts the settings fragments. Apps declare a subclass in their manifest (so the component name
 * stays in the app's own package) and configure everything through {@link SettingsHost} and
 * {@code PikoTheme}; override {@link #onUnhandledActivityResult} to receive result codes of
 * app-owned features launched from a settings screen.
 */
@SuppressWarnings("deprecation")
public class PikoSettingsActivity extends Activity {
    static final int SETTINGS_CONTAINER_ID = 0x00f00001;

    private LinearLayout toolbar;
    private TextView toolbarTitle;
    private TextView patchVersionFooter;
    private Object backCallback;
    private boolean backCallbackRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PikoTheme.applyHostTheme(this);
        super.onCreate(savedInstanceState);
        Utils.setActivity(this);
        int containerId = createContentView();
        configureSystemBars();
        configureToolbar();
        applyCustomFontToToolbar();
        getFragmentManager().addOnBackStackChangedListener(this::onBackStackChanged);
        updateBackCallback();
        if (savedInstanceState != null) return;

        getFragmentManager()
                .beginTransaction()
                .replace(containerId, new PikoSettingsFragment())
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (!isFinishing() && !isDestroyed()) {
            try {
                if (getFragmentManager().popBackStackImmediate()) {
                    return;
                }
            } catch (IllegalStateException ignored) {
                // Ignore if called after state saved
            }
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallbackRegistered) {
            Api33BackHelper.unregister(this, backCallback);
            backCallback = null;
            backCallbackRegistered = false;
        }
        super.onDestroy();
    }

    private void onBackStackChanged() {
        updateBackCallback();
        if (getFragmentManager().getBackStackEntryCount() == 0) {
            setPageTitle(SettingsHost.require().string(SettingsString.SETTINGS_TITLE));
            return;
        }
        setPatchVersionFooterVisible(false);
    }

    private void updateBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        boolean shouldRegister = getFragmentManager().getBackStackEntryCount() > 0;
        if (shouldRegister && !backCallbackRegistered) {
            backCallback = Api33BackHelper.register(this, this::onBackPressed);
            backCallbackRegistered = true;
        } else if (!shouldRegister && backCallbackRegistered) {
            Api33BackHelper.unregister(this, backCallback);
            backCallback = null;
            backCallbackRegistered = false;
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33BackHelper {
        @androidx.annotation.DoNotInline
        static Object register(Activity activity, Runnable onBack) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = onBack::run;
            dispatcher.registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    callback
            );
            return callback;
        }

        @androidx.annotation.DoNotInline
        static void unregister(Activity activity, Object callback) {
            if (callback instanceof android.window.OnBackInvokedCallback backCallback) {
                activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (SettingsBackupRestore.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        onUnhandledActivityResult(requestCode, resultCode, data);
    }

    /** Receives results the library did not consume. */
    protected void onUnhandledActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    }

    static Context createPreferenceContext(Context context) {
        int theme = PikoTheme.isDark(context)
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
        return new ContextThemeWrapper(context, theme);
    }

    private int createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(
                toolbar,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        PikoTheme.dpToPx(this, 56f)
                )
        );

        FrameLayout container = new FrameLayout(this);
        container.setId(SETTINGS_CONTAINER_ID);
        root.addView(
                container,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                )
        );

        patchVersionFooter = new TextView(this);
        patchVersionFooter.setText(SettingsHost.require().string(
                SettingsString.PATCH_VERSION,
                Utils.getPatchesReleaseVersion()
        ));
        patchVersionFooter.setTextColor(PikoTheme.secondaryText(this));
        patchVersionFooter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        patchVersionFooter.setGravity(Gravity.CENTER);
        patchVersionFooter.setSingleLine(true);
        patchVersionFooter.setTypeface(PikoTheme.typeface(this, patchVersionFooter.getTypeface()));
        patchVersionFooter.setPadding(
                0,
                PikoTheme.dpToPx(this, 12f),
                0,
                PikoTheme.dpToPx(this, 24f)
        );
        root.addView(
                patchVersionFooter,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);
        return SETTINGS_CONTAINER_ID;
    }

    private void configureToolbar() {
        int contentColor = PikoTheme.primaryText(this);
        Drawable navigationIcon = backIcon(contentColor);

        ImageButton navigationButton = new ImageButton(this);
        navigationButton.setImageDrawable(navigationIcon);
        navigationButton.setBackgroundColor(Color.TRANSPARENT);
        navigationButton.setContentDescription("Back");
        navigationButton.setOnClickListener(ignored -> onBackPressed());
        toolbar.addView(
                navigationButton,
                new LinearLayout.LayoutParams(
                        PikoTheme.dpToPx(this, 56f),
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        toolbarTitle = new TextView(this);
        toolbarTitle.setText(SettingsHost.require().string(SettingsString.SETTINGS_TITLE));
        toolbarTitle.setTextColor(contentColor);
        toolbarTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL);
        toolbarTitle.setSingleLine(true);
        toolbar.addView(
                toolbarTitle,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                )
        );
        toolbar.setBackgroundColor(PikoTheme.surfaceContainer(this));

        ViewGroup toolbarParent = (ViewGroup) toolbar.getParent();
        View divider = new View(this);
        divider.setBackgroundColor(PikoTheme.dividerColor(this));
        toolbarParent.addView(
                divider,
                toolbarParent.indexOfChild(toolbar) + 1,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, PikoTheme.dpToPx(this, 1f))
                )
        );
    }

    public void setPageTitle(CharSequence title) {
        if (toolbarTitle == null) return;
        toolbarTitle.setText(title);
        applyCustomFontToToolbar();
    }

    public void setPatchVersionFooterVisible(boolean visible) {
        if (patchVersionFooter == null) return;
        patchVersionFooter.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyCustomFontToToolbar() {
        if (toolbarTitle == null) return;
        toolbarTitle.setTypeface(PikoTheme.typeface(this, toolbarTitle.getTypeface()));
    }

    private void configureSystemBars() {
        Window window = getWindow();
        int systemBarColor = PikoTheme.surfaceContainer(this);
        View decorView = window.getDecorView();
        decorView.setBackgroundColor(systemBarColor);
        findViewById(android.R.id.content).setBackgroundColor(systemBarColor);
        if (Build.VERSION.SDK_INT >= 35) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            window.setStatusBarColor(systemBarColor);
            window.setNavigationBarColor(systemBarColor);
        }
        int visibility = decorView.getSystemUiVisibility();
        int lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decorView.setSystemUiVisibility(
                PikoTheme.isDark(this) ? visibility & ~lightBarFlags : visibility | lightBarFlags
        );
        if (Build.VERSION.SDK_INT < 35) return;

        decorView.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom()
            );
            return insets.consumeSystemWindowInsets();
        });
        decorView.requestApplyInsets();
    }

    private Drawable backIcon(int color) {
        String name = SettingsHost.require().backIconResourceName();
        int id = name == null ? 0 : getResources().getIdentifier(name, "drawable", getPackageName());
        if (id != 0) {
            Drawable icon = getDrawable(id).mutate();
            icon.setTint(color);
            return icon;
        }
        return new BackArrowDrawable(color, PikoTheme.dpToPx(this, 2f));
    }

    /** Drawn arrow used when the app supplies no back icon of its own. */
    private static final class BackArrowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BackArrowDrawable(int color, int strokeWidth) {
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
            float size = Math.min(bounds.width(), bounds.height()) * 0.18f;
            Path path = new Path();
            path.moveTo(cx + size, cy);
            path.lineTo(cx - size, cy);
            path.moveTo(cx, cy - size);
            path.lineTo(cx - size, cy);
            path.lineTo(cx, cy + size);
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

        @Override
        public int getIntrinsicWidth() {
            return -1;
        }

        @Override
        public int getIntrinsicHeight() {
            return -1;
        }
    }
}
