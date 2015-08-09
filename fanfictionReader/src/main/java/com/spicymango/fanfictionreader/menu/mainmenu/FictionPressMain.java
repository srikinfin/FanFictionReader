package com.spicymango.fanfictionreader.menu.mainmenu;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.ListView;

import com.spicymango.fanfictionreader.R;

public class FictionPressMain extends ListFragment {
    private final static MainMenuItem menuItems[] = new MainMenuItem[]{
            new MainMenuItem(R.drawable.ic_folder_open,
                    R.string.menu_button_browse_stories, 0)
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MainMenuAdapter adapter = new MainMenuAdapter(getActivity(), menuItems);
        setListAdapter(adapter);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        switch ((int) id) {
            case 0:

                break;

            default:
                break;
        }
    }
}
