package com.spicymango.fanfictionreader.util;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBarActivity;

public class TabListener implements ActionBar.TabListener {
    private final ActionBarActivity mActivity;
    private final Class<? extends Fragment> mClass;
    private final Bundle mArgs;
    private final String mTag;
    private Fragment mFragment;

    public TabListener(ActionBarActivity activity, Class<? extends Fragment> fr, Bundle args) {
        this(activity, fr, args, fr.getName());
    }

    public TabListener(ActionBarActivity activity, Class<? extends Fragment> fr, Bundle args, String tag) {
        mActivity = activity;
        mClass = fr;
        mArgs = args;
        mTag = tag;

        mFragment = mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
        if (mFragment != null) {
            FragmentTransaction ft = activity.getSupportFragmentManager().beginTransaction();
            ft.hide(mFragment);
            ft.commit();
        }
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {

        if (mFragment == null) {
            mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
            ft.add(android.R.id.content, mFragment, mTag);
        }

        // If it exists, simply attach it in order to show it
        ft.show(mFragment);
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if (mFragment != null) {
            // Detach the fragment, because another one is being attached
            ft.hide(mFragment);
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {

    }
}
