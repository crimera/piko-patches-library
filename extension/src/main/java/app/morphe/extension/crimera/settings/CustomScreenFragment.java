package app.morphe.extension.crimera.settings;

import android.app.Fragment;
import android.os.Bundle;
import android.view.View;

/** Base class for app-owned settings screens: themes the view tree once it is created. */
@SuppressWarnings("deprecation")
public abstract class CustomScreenFragment extends Fragment {
    private CustomScreenHost customScreenHost;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        customScreenHost = CustomScreenHost.attach(view);
    }

    @Override
    public void onDestroyView() {
        if (customScreenHost != null) {
            customScreenHost.close();
            customScreenHost = null;
        }
        super.onDestroyView();
    }
}
