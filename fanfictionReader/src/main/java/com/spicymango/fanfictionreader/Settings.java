package com.spicymango.fanfictionreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

import com.kolavar.preference.PreferenceFragment;
import com.spicymango.fanfictionreader.dialogs.BackUpDialog;
import com.spicymango.fanfictionreader.dialogs.FontDialog;
import com.spicymango.fanfictionreader.dialogs.RestoreDialog;
import com.spicymango.fanfictionreader.dialogs.RestoreDialogConfirmation;
import com.spicymango.fanfictionreader.util.FileHandler;


public class Settings extends ActionBarActivity {

    public final static int SANS_SERIF = 0;
    public final static int SERIF = 1;

    public static int fontSize(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        int textSize;
        try {
            //Try to get the text size as an integer
            textSize = sharedPref.getInt(context.getString(R.string.pref_key_text_size), 14);
        } catch (ClassCastException e) {
            //Else, get the old format and convert it to the new one
            textSize = TextSize.getSize(sharedPref.getString(context.getString(R.string.pref_key_text_size), ""));
            Editor editor = sharedPref.edit();
            editor.putInt(context.getString(R.string.pref_key_text_size), textSize);
            editor.commit();
        }

        return textSize;
    }

    public static boolean isIncrementalUpdatingEnabled(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean(context.getString(R.string.pref_incremental_updating), true);
    }

    public static boolean isWakeLockEnabled(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getBoolean(context.getString(R.string.pref_wake_lock), true);
    }

    public static boolean shouldWriteToSD(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getString(context.getString(R.string.pref_loc), "ext").equals("ext");
    }

    public static int getTypeFaceId(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getInt(context.getString(R.string.pref_key_type_face), 0);
    }

    public static Typeface getTypeFace(Context context) {
        switch (getTypeFaceId(context)) {
            case SANS_SERIF:
                return Typeface.SANS_SERIF;
            case SERIF:
                return Typeface.SERIF;
            default:
                return Typeface.DEFAULT;
        }
    }

    private static void setOrientation(Activity activity, SharedPreferences sharedPref) {
        String orientation = sharedPref.getString(activity.getString(R.string.pref_orientation), "A");
        if (orientation.equals("A")) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        } else if (orientation.equals("H")) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    /**
     * Sets the orientation of the activity based on current settings
     *
     * @param activity The activity to set
     */
    public static void setOrientationAndTheme(Activity activity) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        setOrientation(activity, sharedPref);

        String theme = sharedPref.getString(activity.getString(R.string.pref_theme), "D");
        if (theme.equals("DD")) {
            activity.setTheme(R.style.AppActionBar_Darker);
        } else if (theme.equals("D")) {
            activity.setTheme(R.style.AppActionBar);
        } else {
            activity.setTheme(R.style.AppActionBarLight);
        }
    }

    /**
     * Sets the orientation of the activity based on current settings
     *
     * @param activity The activity to set
     */
    public static void setOrientationAndThemeNoActionBar(Activity activity) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
        setOrientation(activity, sharedPref);

        String theme = sharedPref.getString(activity.getString(R.string.pref_key_theme), "D");
        if (theme.equals("DD")) {
            //Materials Darker
            activity.setTheme(R.style.MaterialDarker);
        } else if (theme.equals("D")) {
            //Materials Dark
            activity.setTheme(R.style.MaterialDark);
        } else if (theme.equals("L")) {
            //Materials Light
            activity.setTheme(R.style.MaterialLight);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.setOrientationAndTheme(this);
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment()).commit();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private enum TextSize {
        SMALL(14, "S"),
        MEDIUM(18, "M"),
        LARGE(22, "L"),
        XLARGE(32, "XL");

        private int size;
        private String key;

        TextSize(int fontSize, String key) {
            size = fontSize;
            this.key = key;
        }

        public static int getSize(String key) {
            for (TextSize t : values()) {
                if (t.key.equals(key)) {
                    return t.size;
                }
            }
            return MEDIUM.size;
        }
    }

    public static class PrefsFragment extends PreferenceFragment implements OnPreferenceChangeListener, OnPreferenceClickListener {
        private final static String CREATE_DIALOG = "CreateDialog";

        @Override
        public void onCreate(Bundle paramBundle) {
            super.onCreate(paramBundle);
            addPreferencesFromResource(R.xml.preferences);

            Preference orientationPref = findPreference(getString(R.string.pref_orientation));
            orientationPref.setOnPreferenceChangeListener(this);

            final boolean isSdCardAvailable = FileHandler.isExternalStorageWritable(getActivity());
            Preference installLocation = findPreference(getString(R.string.pref_loc));
            installLocation.setEnabled(isSdCardAvailable);
            installLocation.setOnPreferenceChangeListener(this);

            Preference themeChanged = findPreference(getString(R.string.pref_key_theme));
            themeChanged.setOnPreferenceChangeListener(this);

            Preference backup = findPreference(getString(R.string.pref_key_back_up));
            backup.setOnPreferenceClickListener(this);

            Preference restore = findPreference(getString(R.string.pref_key_restore));
            restore.setEnabled(RestoreDialog.findBackUpFile(getActivity()) != null);
            restore.setOnPreferenceClickListener(this);

            Preference fontDiag = findPreference(getString(R.string.pref_key_text_size));
            fontDiag.setOnPreferenceClickListener(this);

            if (getActivity().getIntent().getBooleanExtra(CREATE_DIALOG, false)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.diag_theme_warning);
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                                        int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {

            if (preference.getKey().equals(getString(R.string.pref_orientation))) {
                String value = (String) newValue;
                if (value.equals("A")) {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                } else if (value.equals("H")) {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                } else {
                    getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            } else if (preference.getKey().equals(getString(R.string.pref_loc))) {
                showMoveDialog();
            } else if (preference.getKey().equals(getString(R.string.pref_key_theme))) {
                // Saves the preference, then reopens the settings file if
                // the new value is different.
                String currentValue = preference.getSharedPreferences()
                        .getString(getString(R.string.pref_key_theme), "D");
                if (currentValue.equals(newValue)) {
                    return false;
                } else {
                    Editor editor = preference.getEditor();
                    editor.putString(preference.getKey(), (String) newValue);
                    editor.commit();

                    Intent i = getActivity().getIntent();

                    //Display imperfect theme dialog.
                    if (newValue.equals("DD")) {
                        i.putExtra(CREATE_DIALOG, true);
                    } else {
                        i.putExtra(CREATE_DIALOG, false);
                    }

                    getActivity().finish();
                    startActivity(i);

                    return false;
                }
            }
            return true;
        }

        private void showMoveDialog() {
            AlertDialog.Builder diag = new Builder(getActivity());
            diag.setTitle(R.string.pref_loc_diag_title);
            diag.setMessage(R.string.pref_loc_diag);
            diag.setNeutralButton(android.R.string.ok, null);
            diag.show();
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (preference.getKey().equals(getString(R.string.pref_key_back_up))) {
                DialogFragment diag = new BackUpDialog();
                diag.show(getFragmentManager(), diag.getClass().getName());
            } else if (preference.getKey().equals(getString(R.string.pref_key_restore))) {
                DialogFragment diag = new RestoreDialogConfirmation();
                diag.show(getFragmentManager(), diag.getClass().getName());
            } else if (preference.getKey().equals(getString(R.string.pref_key_text_size))) {
                DialogFragment diag = new FontDialog();
                diag.show(getFragmentManager(), diag.getClass().getName());
            }
            return false;
        }
    }
}

