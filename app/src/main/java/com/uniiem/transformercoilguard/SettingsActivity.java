package com.uniiem.transformercoilguard;

import android.os.Bundle;
import android.text.InputType;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            EditTextPreference autoRefreshDelayPreference = findPreference("edit_text_preference_auto_refresh_delay");

            assert autoRefreshDelayPreference != null;
            autoRefreshDelayPreference.setDialogMessage("单位：秒");
            autoRefreshDelayPreference.setOnBindEditTextListener((editText) -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            });
        }
    }
}