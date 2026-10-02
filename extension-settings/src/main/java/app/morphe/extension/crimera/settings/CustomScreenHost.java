package app.morphe.extension.crimera.settings;

import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.WeakHashMap;

import app.morphe.extension.crimera.theme.PikoTheme;

/** Applies the settings theme to extension-owned custom screen views. */
public final class CustomScreenHost implements ViewTreeObserver.OnGlobalLayoutListener {
    private final View root;
    private final ViewTreeObserver observer;
    private final WeakHashMap<TextView, Typeface> appliedTypefaces = new WeakHashMap<>();
    private boolean applying;
    private boolean closed;

    private CustomScreenHost(View root) {
        this.root = root;
        if (root.getBackground() == null) {
            root.setBackgroundColor(SettingsUi.backgroundColor(root.getContext()));
        }
        observer = root.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        applyTheme();
    }

    public static CustomScreenHost attach(View root) {
        if (root == null) throw new IllegalArgumentException("Custom screen root is null");
        return new CustomScreenHost(root);
    }

    @Override
    public void onGlobalLayout() {
        if (closed) return;
        applyTheme();
    }

    public void close() {
        if (closed) return;
        closed = true;
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(this);
        }
        appliedTypefaces.clear();
    }

    private void applyTheme() {
        if (applying || closed) return;
        applying = true;
        try {
            applyTheme(root);
        } finally {
            applying = false;
        }
    }

    private void applyTheme(View view) {
        if (view instanceof TextView textView) {
            applyTypeface(textView);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int index = 0; index < group.getChildCount(); index++) {
            applyTheme(group.getChildAt(index));
        }
    }

    private void applyTypeface(TextView textView) {
        Typeface current = textView.getTypeface();
        Typeface previous = appliedTypefaces.get(textView);
        if (previous != null && previous.equals(current)) return;

        Typeface themed = PikoTheme.typeface(root.getContext(), current);
        if (themed != current && !themed.equals(current)) {
            textView.setTypeface(themed);
        }
        appliedTypefaces.put(textView, textView.getTypeface());
    }
}
