package com.spicymango.fanfictionreader.util.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.spicymango.fanfictionreader.R;
import com.spicymango.fanfictionreader.Settings;

import java.util.List;

import info.piwai.android.JellyBeanSpanFixTextView;

/**
 * An adapter optimized for displaying long amounts of text efficiently
 *
 * @author Michael Chen
 */
public class TextAdapter extends ArrayAdapter<Spanned> {
    private final int padding;
    private final int fontSize;
    private final int primaryColor;
    private final Typeface tp;

    /**
     * Creates a new instance of TextAdapter.
     *
     * @param context The current context
     * @param objects A list of paragraphs
     */
    public TextAdapter(Context context, List<Spanned> objects) {
        super(context, 0, objects);
        padding = context.getResources().getDimensionPixelOffset(R.dimen.reading_margin);
        fontSize = Settings.fontSize(context);
        tp = Settings.getTypeFace(context);

        TypedValue color = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, color, true);
        primaryColor = color.data;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view;
        if (convertView == null) {
            view = new JellyBeanSpanFixTextView(getContext());
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize);
            view.setTypeface(tp);
            view.setTextColor(primaryColor);
            view.setPadding(padding, 0, padding, 0);
            view.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            view = (TextView) convertView;
        }
        view.setText(getItem(position), BufferType.SPANNABLE);
        return view;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }
}