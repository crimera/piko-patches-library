package app.morphe.extension.crimera.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.theme.PikoTheme;

/** The rounded search field used by the settings root screen. */
final class SettingsSearchField extends LinearLayout {
    interface Listener {
        void onQueryChanged(String query);
    }

    private final EditText input;
    private final ImageView clearButton;
    private final LinearLayout searchField;
    private boolean suppressNotifications;
    private Listener listener;

    SettingsSearchField(Context context) {
        super(context);
        SettingsHost host = SettingsHost.require();
        setOrientation(VERTICAL);

        searchField = new LinearLayout(context);
        searchField.setOrientation(HORIZONTAL);
        searchField.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        searchField.setPaddingRelative(dp(16), 0, dp(8), 0);
        searchField.setBackground(createSearchFieldBackground(context));
        searchField.setClickable(true);
        searchField.setFocusable(true);
        searchField.setOnClickListener(ignored -> focusInput());

        ImageView searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setColorFilter(PikoTheme.secondaryText(context));
        int searchIconId = drawableId(context, host.searchIconResourceName());
        if (searchIconId != 0) {
            searchIcon.setImageResource(searchIconId);
        } else {
            searchIcon.setVisibility(GONE);
        }
        LayoutParams searchIconParams = new LayoutParams(dp(24), dp(24));
        searchIconParams.setMarginEnd(dp(12));
        searchField.addView(searchIcon, searchIconParams);

        input = new EditText(context);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTextColor(PikoTheme.primaryText(context));
        input.setHintTextColor(PikoTheme.secondaryText(context));
        input.setHint(host.string(SettingsString.SEARCH_HINT));
        input.setTypeface(PikoTheme.typeface(context, input.getTypeface()));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setPadding(0, 0, 0, 0);
        input.setMinHeight(0);
        input.setMinWidth(0);
        input.setMinimumWidth(0);
        input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        input.setOnEditorActionListener((textView, actionId, event) -> {
            boolean enterKey = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || enterKey) {
                hideKeyboard(input);
                input.clearFocus();
                return true;
            }
            return false;
        });
        searchField.addView(input, new LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        ));

        clearButton = new ImageView(context);
        clearButton.setScaleType(ImageView.ScaleType.CENTER);
        clearButton.setColorFilter(PikoTheme.primaryAccent(context));
        int clearIconId = drawableId(context, host.clearIconResourceName());
        clearButton.setImageResource(
                clearIconId == 0 ? android.R.drawable.ic_menu_close_clear_cancel : clearIconId
        );
        clearButton.setContentDescription(host.string(SettingsString.SEARCH_CLEAR));
        clearButton.setClickable(true);
        clearButton.setFocusable(true);
        clearButton.setVisibility(GONE);
        clearButton.setOnClickListener(ignored -> {
            input.setText("");
            focusInput();
        });
        searchField.addView(clearButton, new LayoutParams(dp(40), dp(40)));

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSearchLayout(s.length() > 0);
                if (!suppressNotifications && listener != null) {
                    listener.onQueryChanged(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        addView(searchField, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        updateSearchLayout(false);
    }

    void setOnQueryChangedListener(Listener listener) {
        this.listener = listener;
    }

    String query() {
        return input.getText().toString();
    }

    void setQuery(String query) {
        suppressNotifications = true;
        input.setText(query == null ? "" : query);
        input.setSelection(input.length());
        suppressNotifications = false;
        updateSearchLayout(input.length() > 0);
    }

    private void updateSearchLayout(boolean queryActive) {
        clearButton.setVisibility(queryActive ? VISIBLE : GONE);
    }

    private void focusInput() {
        input.requestFocus();
        input.post(() -> {
            if (input.isAttachedToWindow()) showKeyboard(input);
        });
    }

    private StateListDrawable createSearchFieldBackground(Context context) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{android.R.attr.state_pressed},
                createRoundedSearchFieldDrawable(
                        context,
                        PikoTheme.blend(
                                PikoTheme.surfaceContainerHigh(context),
                                PikoTheme.primaryText(context),
                                0.08f
                        )
                )
        );
        background.addState(
                new int[]{android.R.attr.state_focused},
                createRoundedSearchFieldDrawable(
                        context,
                        PikoTheme.surfaceVariant(context)
                )
        );
        background.addState(
                new int[]{},
                createRoundedSearchFieldDrawable(
                        context,
                        PikoTheme.surfaceContainerHigh(context)
                )
        );
        return background;
    }

    private GradientDrawable createRoundedSearchFieldDrawable(Context context, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
                drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private int drawableId(Context context, @Nullable String name) {
        if (name == null) return 0;
        return context.getResources().getIdentifier(
                name,
                "drawable",
                context.getPackageName()
        );
    }

    private int dp(float value) {
        return PikoTheme.dpToPx(getContext(), value);
    }

    private static void hideKeyboard(View view) {
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager inputMethodManager) {
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static void showKeyboard(View view) {
        Object service = view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (service instanceof InputMethodManager inputMethodManager) {
            inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
