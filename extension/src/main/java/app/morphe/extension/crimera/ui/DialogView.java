package app.morphe.extension.crimera.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.theme.PikoTheme;

/**
 * Reusable Dialog builder and view container.
 */
public class DialogView {

    private final Context context;
    private final Dialog dialog;
    private final LinearLayout mainContainer;
    private final LinearLayout headerContainer;
    private final TextView titleView;
    private final TextView subtitleView;
    private final FrameLayout bodyContainer;
    private final ActionRow actionContainer;
    private final View topDivider;
    private final View bottomDivider;
    @Nullable
    private MaxHeightScrollView scrollableBody;

    public DialogView(Context context) {
        this.context = context;
        this.dialog = new Dialog(context);

        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.55f);
        }

        // Main card container
        mainContainer = new LinearLayout(context);
        mainContainer.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(PikoTheme.dpToPx(context, 28f));
        cardBg.setColor(PikoTheme.surfaceContainerHigh(context));
        mainContainer.setBackground(cardBg);

        mainContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // 1. Header Container
        headerContainer = new LinearLayout(context);
        headerContainer.setOrientation(LinearLayout.VERTICAL);
        headerContainer.setPadding(
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 16f)
        );

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleView.setTypeface(PikoTheme.typeface(context, android.graphics.Typeface.DEFAULT_BOLD));
        titleView.setTextColor(PikoTheme.primaryText(context));
        headerContainer.addView(titleView);

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitleView.setTypeface(PikoTheme.typeface(context, subtitleView.getTypeface()));
        subtitleView.setTextColor(PikoTheme.secondaryText(context));
        subtitleView.setPadding(0, PikoTheme.dpToPx(context, 6f), 0, 0);
        subtitleView.setVisibility(View.GONE);
        headerContainer.addView(subtitleView);

        mainContainer.addView(headerContainer);

        topDivider = createDivider();
        mainContainer.addView(topDivider);

        // 2. Body Container
        bodyContainer = new FrameLayout(context);
        mainContainer.addView(bodyContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        bottomDivider = createDivider();
        mainContainer.addView(bottomDivider);

        // 3. Action Container
        actionContainer = new ActionRow(context);
        actionContainer.setPadding(
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 12f),
                PikoTheme.dpToPx(context, 24f),
                PikoTheme.dpToPx(context, 24f)
        );
        mainContainer.addView(actionContainer);

        dialog.setContentView(mainContainer);
    }

    public DialogView setTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public DialogView setSubtitle(@Nullable CharSequence subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        return this;
    }

    public DialogView setBodyView(View view) {
        scrollableBody = null;
        setDividersVisible(false);
        bodyContainer.removeAllViews();
        bodyContainer.addView(view);
        return this;
    }

    public DialogView setScrollableBodyView(View view) {
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

    public DialogView addButton(ButtonView button) {
        actionContainer.addView(button, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return this;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void show() {
        if (!dialog.isShowing()) {
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int targetWidth = Math.min(
                        screenWidth - PikoTheme.dpToPx(context, 56f),
                        PikoTheme.dpToPx(context, 560f)
                );
                window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            mainContainer.post(this::updateScrollableDividers);
        }
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
        int visibility = visible ? View.VISIBLE : View.GONE;
        topDivider.setVisibility(visibility);
        bottomDivider.setVisibility(visibility);
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /**
     * Button row that keeps its buttons on one line while their labels fit, and stacks them
     * end-aligned when they do not. A fixed horizontal row squeezed the last button to nothing
     * on phone-width screens.
     */
    private static class ActionRow extends ViewGroup {
        private final int spacingPx;
        private boolean stacked;

        ActionRow(Context context) {
            super(context);
            spacingPx = PikoTheme.dpToPx(context, 8f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());

            int rowHeight = 0;
            int stackHeight = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                // measureChild subtracts this row's padding itself, so pass the full width.
                measureChild(child,
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                stackHeight += child.getMeasuredHeight();
            }
            stackHeight += spacingPx * Math.max(0, getChildCount() - 1);

            stacked = measuredRowWidth() > available;
            int contentHeight = stacked ? stackHeight : rowHeight;
            setMeasuredDimension(width, resolveSize(
                    contentHeight + getPaddingTop() + getPaddingBottom(),
                    heightMeasureSpec
            ));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            int contentLeft = getPaddingLeft();
            int contentRight = getWidth() - getPaddingRight();
            int contentTop = getPaddingTop();
            int contentHeight = getHeight() - contentTop - getPaddingBottom();

            if (stacked) {
                int y = contentTop;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    int childWidth = child.getMeasuredWidth();
                    int childHeight = child.getMeasuredHeight();
                    int childLeft = rtl ? contentLeft : contentRight - childWidth;
                    child.layout(childLeft, y, childLeft + childWidth, y + childHeight);
                    y += childHeight + spacingPx;
                }
                return;
            }

            // The row block sits at the end edge; buttons keep their added order within it.
            int rowWidth = measuredRowWidth();
            int x = rtl ? contentLeft + rowWidth : contentRight - rowWidth;
            int step = rtl ? -1 : 1;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int childWidth = child.getMeasuredWidth();
                int childHeight = child.getMeasuredHeight();
                int childLeft = rtl ? x - childWidth : x;
                int childTop = contentTop + (contentHeight - childHeight) / 2;
                child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
                x += step * (childWidth + spacingPx);
            }
        }

        private int measuredRowWidth() {
            int total = 0;
            for (int i = 0; i < getChildCount(); i++) {
                total += getChildAt(i).getMeasuredWidth();
            }
            return total + spacingPx * Math.max(0, getChildCount() - 1);
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
}
