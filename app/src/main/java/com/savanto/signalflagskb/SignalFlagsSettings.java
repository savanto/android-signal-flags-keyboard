package com.savanto.signalflagskb;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

/**
 * @author savanto
 * Displays the keyboard settings inside the input method setting.
 */
public class SignalFlagsSettings extends PreferenceActivity {
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the title of the activity
        this.setTitle(R.string.settings_name);

        // Add layout preferences from xml.
        this.addPreferencesFromResource(R.xml.pref_layouts);

        // Set defaults, if this is the first time preferences are accessed.
        PreferenceManager.setDefaultValues(this, R.xml.pref_layouts, false);
    }
}
