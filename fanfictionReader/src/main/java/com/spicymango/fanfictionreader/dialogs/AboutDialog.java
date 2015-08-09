/**
 *
 */
package com.spicymango.fanfictionreader.dialogs;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.util.FileHandler;

/**
 * @author Michael Chen
 */

@SuppressLint("InflateParams")
public class AboutDialog extends DialogFragment implements OnClickListener {

    public AboutDialog() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        LayoutInflater inflater = getActivity().getLayoutInflater();
        View content = inflater.inflate(R.layout.dialog_about, null);

        AlertDialog.Builder builder = new Builder(getActivity());
        builder.setView(content);
        builder.setTitle(R.string.menu_button_about);

        content.findViewById(R.id.about_contact).setOnClickListener(this);

        AlertDialog diag = builder.create();
        diag.setCanceledOnTouchOutside(true);
        return diag;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        FileHandler.deleteFile(getActivity(), 0, 0);
        Log.d(AboutDialog.class.getName(), "Dismissed");
    }

    @Override
    public void onClick(View v) {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", "michaelchentejada+dev@gmail.com", null));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "FanFiction Reader");

        String error = FileHandler.getRawFile(getActivity(), 0, 0);
        if (error != null) {
            emailIntent.putExtra(Intent.EXTRA_TEXT, error);
        }

        startActivity(Intent.createChooser(emailIntent, "Send email..."));
    }

}