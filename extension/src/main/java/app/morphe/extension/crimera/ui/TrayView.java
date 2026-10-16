package app.morphe.extension.crimera.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.crimera.theme.SettingsFont;

/**
 * Floating tray in the style of the redesigned X settings: a rounded card inset from the screen
 * edges with a bold title, a close button, a divider, a body and full-width pill buttons. Same
 * builder API as {@link BottomSheetView}, which keeps its original edge-to-edge look for existing
 * consumers.
 *
 * <p>The card lifts above the navigation bar and the keyboard, supports swipe-down and
 * tap-outside dismissal, and slides in and out with Material 3 motion curves. Every color and
 * font comes from the theme installed with {@link PikoTheme#install}.
 */
public class TrayView {

    private static final float DEFAULT_DIM_AMOUNT = 0.55f;
    private static final float EDGE_MARGIN_DP = 8f;

    // Android Compose / Material 3 Motion Tokens
    private static final Interpolator COMPOSE_SETTLE_INTERPOLATOR =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
                    : new DecelerateInterpolator();

    private static final Interpolator COMPOSE_DISMISS_INTERPOLATOR =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    ? new PathInterpolator(0.3f, 0.0f, 0.8f, 0.15f)
                    : new AccelerateInterpolator();

    private final Context context;
    private final BottomSheetDialog dialog;
    private final FrameLayout rootLayout;
    private final SheetContainer mainContainer;
    private final FrameLayout dragHandleContainer;
    private final LinearLayout headerContainer;
    private final TextView titleView;
    private final TextView subtitleView;
    private final FrameLayout bodyContainer;
    private final LinearLayout actionContainer;
    private final View topDivider;
    private final View bottomDivider;
    @Nullable
    private MaxHeightScrollView scrollableBody;
    private boolean isDismissing = false;
    private boolean canceledOnTouchOutside = true;

    public TrayView(Context context) {
        this.context = context;
        this.dialog = new BottomSheetDialog(context);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(DEFAULT_DIM_AMOUNT);
            // 0 would fall back to the theme's default (fade) dialog animation; this style has none,
            // leaving the slide below as the only entrance motion.
            window.setWindowAnimations(android.R.style.Animation);
            window.setGravity(Gravity.BOTTOM);

            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.setNavigationBarColor(Color.TRANSPARENT);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        window.setNavigationBarContrastEnforced(false);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // Root backdrop container (fills entire dialog window to capture touch outside)
        rootLayout = new FrameLayout(context);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // Main sheet container with swipe-to-dismiss gesture support
        mainContainer = new SheetContainer(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        // Floating card inset from the screen edges, rounded on every corner.
        float cornerRadius = PikoTheme.dpToPx(context, 28f);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setCornerRadius(cornerRadius);
        sheetBg.setColor(PikoTheme.surfaceContainerHigh(context));
        mainContainer.setBackground(sheetBg);

        FrameLayout.LayoutParams mainParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        int edgeMargin = PikoTheme.dpToPx(context, EDGE_MARGIN_DP);
        mainParams.setMargins(edgeMargin, 0, edgeMargin, edgeMargin);
        mainContainer.setLayoutParams(mainParams);
        // Start fully below the screen so the first frame never shows the sheet in place.
        mainContainer.setTranslationY(context.getResources().getDisplayMetrics().heightPixels);

        // Tap-up-only dismissal on outside touch (with touch slop tracking to prevent accidental dismissals)
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final boolean[] touchDownOutside = new boolean[]{false};
        final float[] touchDownCoords = new float[]{0f, 0f};

        rootLayout.setOnTouchListener((v, event) -> {
            if (!canceledOnTouchOutside || isDismissing) return false;
            int action = event.getActionMasked();
            float rawX = event.getRawX();
            float rawY = event.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (!isTouchInsideView(mainContainer, (int) rawX, (int) rawY)) {
                        touchDownOutside[0] = true;
                        touchDownCoords[0] = rawX;
                        touchDownCoords[1] = rawY;
                        return true;
                    }
                    touchDownOutside[0] = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchDownOutside[0]) {
                        float dx = Math.abs(rawX - touchDownCoords[0]);
                        float dy = Math.abs(rawY - touchDownCoords[1]);
                        if (dx > touchSlop || dy > touchSlop) {
                            touchDownOutside[0] = false;
                        }
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (touchDownOutside[0]) {
                        touchDownOutside[0] = false;
                        if (!isTouchInsideView(mainContainer, (int) rawX, (int) rawY)) {
                            dismiss();
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                    touchDownOutside[0] = false;
                    break;
            }
            return false;
        });

        // 1. Drag handle indicator (pill shape 36dp x 4dp)
        dragHandleContainer = new FrameLayout(context);
        dragHandleContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dragHandleContainer.setPadding(0, PikoTheme.dpToPx(context, 10f), 0, PikoTheme.dpToPx(context, 4f));

        View pill = new View(context);
        int pillWidth = PikoTheme.dpToPx(context, 36f);
        int pillHeight = PikoTheme.dpToPx(context, 4f);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(pillWidth, pillHeight, Gravity.CENTER_HORIZONTAL);
        pill.setLayoutParams(pillParams);

        GradientDrawable pillDrawable = new GradientDrawable();
        pillDrawable.setShape(GradientDrawable.RECTANGLE);
        pillDrawable.setCornerRadius(PikoTheme.dpToPx(context, 2f));
        pillDrawable.setColor(PikoTheme.dragHandleColor(context));
        pill.setBackground(pillDrawable);

        dragHandleContainer.addView(pill);
        dragHandleContainer.setVisibility(View.GONE);
        mainContainer.addView(dragHandleContainer);

        // 2. Header Container (Title & Subtitle)
        headerContainer = new LinearLayout(context);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setPadding(
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 12f),
                PikoTheme.dpToPx(context, 12f),
                PikoTheme.dpToPx(context, 4f)
        );

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        headerContainer.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        titleView.setTypeface(PikoTheme.typeface(context, SettingsFont.ROW_TITLE, Typeface.DEFAULT_BOLD));
        titleView.setTextColor(PikoTheme.primaryText(context));
        titleRow.addView(titleView, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        ImageView closeButton = new ImageView(context);
        closeButton.setImageDrawable(new CloseDrawable(
                PikoTheme.primaryText(context),
                PikoTheme.dpToPx(context, 2f),
                PikoTheme.dpToPx(context, 14f)
        ));
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setContentDescription("Close");
        closeButton.setClickable(true);
        closeButton.setFocusable(true);
        closeButton.setOnClickListener(ignored -> dismiss());
        titleRow.addView(closeButton, new LinearLayout.LayoutParams(
                PikoTheme.dpToPx(context, 48f),
                PikoTheme.dpToPx(context, 48f)
        ));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTypeface(PikoTheme.typeface(context, SettingsFont.ROW_SUMMARY, subtitleView.getTypeface()));
        subtitleView.setTextColor(PikoTheme.secondaryText(context));
        subtitleView.setPadding(0, PikoTheme.dpToPx(context, 4f), 0, 0);
        subtitleView.setVisibility(View.GONE);
        headerContainer.addView(subtitleView);

        mainContainer.addView(headerContainer);

        topDivider = createDivider();
        topDivider.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams topDividerParams =
                (LinearLayout.LayoutParams) topDivider.getLayoutParams();
        topDividerParams.setMarginStart(PikoTheme.dpToPx(context, 24f));
        topDividerParams.setMarginEnd(PikoTheme.dpToPx(context, 24f));
        mainContainer.addView(topDivider);

        // 3. Body Container
        bodyContainer = new FrameLayout(context);
        mainContainer.addView(bodyContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        bottomDivider = createDivider();
        mainContainer.addView(bottomDivider);

        // 4. Action Container (initial padding with fallback navbar height)
        actionContainer = new LinearLayout(context);
        actionContainer.setOrientation(LinearLayout.VERTICAL);
        actionContainer.setPadding(
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f)
        );
        actionContainer.setVisibility(View.GONE);
        mainContainer.addView(actionContainer);
        setBottomInset(getNavigationBarHeight(context));

        rootLayout.addView(mainContainer);
        dialog.setContentView(rootLayout);
        dialog.setCanceledOnTouchOutside(false);
    }

    public TrayView setTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public TrayView setSubtitle(@Nullable CharSequence subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public TrayView setShowDragHandle(boolean show) {
        dragHandleContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        return this;
    }

    public TrayView setCanceledOnTouchOutside(boolean cancel) {
        this.canceledOnTouchOutside = cancel;
        return this;
    }

    public TrayView setBodyView(View view) {
        scrollableBody = null;
        setDividersVisible(false);
        bodyContainer.removeAllViews();
        bodyContainer.addView(view);
        return this;
    }

    public TrayView setScrollableBodyView(View view) {
        setDividersVisible(false);
        MaxHeightScrollView scrollView = new MaxHeightScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        int maxScreenHeight = context.getResources().getDisplayMetrics().heightPixels;
        scrollView.setMaxHeightPx((int) (maxScreenHeight * 0.6f));
        scrollView.addView(view);
        scrollView.addOnLayoutChangeListener((changedView, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollableDividers());

        scrollableBody = scrollView;
        bodyContainer.removeAllViews();
        bodyContainer.addView(scrollView);
        return this;
    }

    public TrayView addButton(ButtonView button) {
        actionContainer.setVisibility(View.VISIBLE);
        mainContainer.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                PikoTheme.dpToPx(context, 48f)
        );
        if (actionContainer.getChildCount() > 0) {
            params.topMargin = PikoTheme.dpToPx(context, 8f);
        }
        actionContainer.addView(button, params);
        return this;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void show() {
        if (!dialog.isShowing()) {
            isDismissing = false;
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                window.setGravity(Gravity.BOTTOM);
                View decorView = window.getDecorView();
                if (decorView != null) {
                    decorView.setPadding(0, 0, 0, 0);
                    decorView.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    );
                    applyDynamicNavbarPadding(decorView);
                }
            }

            // Compose Material 3 pure slide-up entrance animation (no alpha fade)
            mainContainer.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mainContainer.getViewTreeObserver().removeOnPreDrawListener(this);
                    int height = mainContainer.getHeight();
                    float startY = (height > 0 ? height : 1000) + PikoTheme.dpToPx(context, 100f);
                    mainContainer.setTranslationY(startY);
                    mainContainer.animate()
                            .translationY(0f)
                            .setDuration(220)
                            .setInterpolator(COMPOSE_SETTLE_INTERPOLATOR)
                            .start();
                    return true;
                }
            });

            mainContainer.post(this::updateScrollableDividers);
        }
    }

    private void applyDynamicNavbarPadding(View decorView) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                    setBottomInset(bottomInset(insets));
                    return insets;
                });
                decorView.requestApplyInsets();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Navigation bar, or the keyboard while it is showing. */
    private static int bottomInset(android.view.WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Math.max(
                    insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
            );
        }
        return insets.getSystemWindowInsetBottom();
    }

    /** Lifts the floating card above the system bars (and keyboard) by {@code insetPx}. */
    private void setBottomInset(int insetPx) {
        ViewGroup.LayoutParams params = mainContainer.getLayoutParams();
        if (!(params instanceof FrameLayout.LayoutParams margins)) return;
        // Body-only sheets keep their own breathing room below the content.
        mainContainer.setPadding(0, 0, 0, actionContainer.getVisibility() == View.GONE
                ? PikoTheme.dpToPx(context, 12f) : 0);
        int bottom = PikoTheme.dpToPx(context, EDGE_MARGIN_DP) + insetPx;
        if (margins.bottomMargin == bottom) return;
        margins.bottomMargin = bottom;
        mainContainer.setLayoutParams(margins);
    }

    private static int getNavigationBarHeight(Context context) {
        try {
            int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private View createDivider() {
        View divider = new View(context);
        divider.setBackgroundColor(PikoTheme.dividerColor(context));
        divider.setVisibility(View.GONE);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                PikoTheme.dpToPx(context, 1f)
        ));
        return divider;
    }

    private void updateScrollableDividers() {
        if (scrollableBody == null) {
            setDividersVisible(false);
            return;
        }

        boolean hasOverflow = scrollableBody.canScrollVertically(1)
                || scrollableBody.canScrollVertically(-1);
        setDividersVisible(hasOverflow);
    }

    private void setDividersVisible(boolean visible) {
        bottomDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            if (isDismissing) return;
            isDismissing = true;
            int height = mainContainer.getHeight();
            float targetY = (height > 0 ? height : 1000) + PikoTheme.dpToPx(context, 100f);

            mainContainer.animate()
                    .translationY(targetY)
                    .setDuration(160)
                    .setInterpolator(COMPOSE_DISMISS_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            dialog.superDismiss();
                        }
                    })
                    .start();
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    private static boolean isTouchInsideView(View view, int rawX, int rawY) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        int w = view.getWidth();
        int h = view.getHeight();
        return rawX >= x && rawX <= (x + w) && rawY >= y && rawY <= (y + h);
    }

    /**
     * Custom Dialog that intercepts hardware/gesture back, escape, and backspace keys,
     * ensuring all exit paths trigger the smooth Compose animation.
     */
    private class BottomSheetDialog extends Dialog {
        private boolean isSuperDismissing = false;

        public BottomSheetDialog(Context context) {
            super(context);
        }

        @Override
        public void onBackPressed() {
            TrayView.this.dismiss();
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) {
                event.startTracking();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_DEL) {
                if (!event.isCanceled()) {
                    TrayView.this.dismiss();
                    return true;
                }
            }
            return super.onKeyUp(keyCode, event);
        }

        @Override
        public void dismiss() {
            if (isSuperDismissing) {
                super.dismiss();
            } else {
                TrayView.this.dismiss();
            }
        }

        void superDismiss() {
            isSuperDismissing = true;
            try {
                super.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Custom container with swipe-down-to-dismiss gesture handling.
     */
    private class SheetContainer extends LinearLayout {
        private final int touchSlop;
        private float initialDownX = 0f;
        private float initialDownY = 0f;
        private float lastMotionY = 0f;
        private boolean isDragging = false;
        @Nullable
        private VelocityTracker velocityTracker;

        public SheetContainer(Context context) {
            super(context);
            this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (isDismissing) return true;

            int action = ev.getActionMasked();
            float x = ev.getRawX();
            float y = ev.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    initialDownX = x;
                    initialDownY = y;
                    lastMotionY = y;
                    isDragging = false;
                    initVelocityTracker();
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dy = y - initialDownY;
                    float dx = x - initialDownX;

                    if (dy > touchSlop && dy > Math.abs(dx)) {
                        // Check if touch target is a scrollable child that can scroll up
                        if (scrollableBody != null && isTouchInsideView(scrollableBody, (int) ev.getRawX(), (int) ev.getRawY())) {
                            if (!scrollableBody.canScrollVertically(-1)) {
                                isDragging = true;
                                lastMotionY = y;
                                return true;
                            }
                        } else {
                            isDragging = true;
                            lastMotionY = y;
                            return true;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    recycleVelocityTracker();
                    break;
            }

            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (isDismissing) return true;

            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }

            int action = ev.getActionMasked();
            float y = ev.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    initialDownY = y;
                    lastMotionY = y;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float totalDy = y - initialDownY;
                    if (totalDy >= 0) {
                        setTranslationY(totalDy);
                    } else {
                        // Elastic resistance when pulling up
                        setTranslationY(totalDy * 0.15f);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float currentTranslationY = getTranslationY();
                    float yVelocity = 0f;

                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        yVelocity = velocityTracker.getYVelocity();
                        recycleVelocityTracker();
                    }

                    int height = getHeight();
                    boolean shouldDismiss = (yVelocity > 1000f) || (currentTranslationY > (height * 0.35f));

                    if (shouldDismiss && height > 0) {
                        animateDismiss(height);
                    } else {
                        animateCancel();
                    }
                    isDragging = false;
                    return true;
            }

            return super.onTouchEvent(ev);
        }

        private void animateDismiss(int height) {
            isDismissing = true;
            float targetY = height + PikoTheme.dpToPx(context, 100f);

            animate()
                    .translationY(targetY)
                    .setDuration(160)
                    .setInterpolator(COMPOSE_DISMISS_INTERPOLATOR)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            dialog.superDismiss();
                        }
                    })
                    .start();
        }

        private void animateCancel() {
            animate()
                    .translationY(0f)
                    .setDuration(200)
                    .setInterpolator(COMPOSE_SETTLE_INTERPOLATOR)
                    .setListener(null)
                    .start();
        }

        private void initVelocityTracker() {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
        }

        private void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
    }

    /** ScrollView capped at a maximum height to avoid overflowing screen. */
    private static class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx = Integer.MAX_VALUE;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        void setMaxHeightPx(int maxHeightPx) {
            this.maxHeightPx = maxHeightPx;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int heightSpec = heightMeasureSpec;
            if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
                int heightSize = Math.min(MeasureSpec.getSize(heightMeasureSpec), maxHeightPx);
                heightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightSpec);
        }
    }

    /** Cross drawn as two stroked lines, so the sheet needs no app resource. */
    private static final class CloseDrawable extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        private final float halfSpan;

        CloseDrawable(int color, int strokeWidth, int span) {
            halfSpan = span / 2f;
            paint.setColor(color);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        @Override
        public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float cx = bounds.exactCenterX();
            float cy = bounds.exactCenterY();
            float size = halfSpan;
            canvas.drawLine(cx - size, cy - size, cx + size, cy + size, paint);
            canvas.drawLine(cx - size, cy + size, cx + size, cy - size, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
