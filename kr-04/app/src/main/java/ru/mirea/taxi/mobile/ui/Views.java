package ru.mirea.taxi.mobile.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.List;

final class Views {
    static final int TEXT = Color.rgb(28, 51, 47);
    static final int MUTED = Color.rgb(66, 96, 84);
    static final int BRAND = Color.rgb(20, 102, 83);
    static final int BACKGROUND = Color.rgb(237, 242, 239);
    static final int SURFACE = Color.WHITE;
    static final int ERROR = Color.rgb(158, 52, 43);
    private final Context context;

    Views(Context context) {
        this.context = context;
    }

    int dp(float value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    LinearLayout column() {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return layout;
    }

    LinearLayout card() {
        var card = column();
        card.setBackground(background(SURFACE, 12));
        card.setPadding(dp(13), dp(10), dp(13), dp(10));
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    TextView text(String value, int size, int color, boolean bold) {
        var text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setLineSpacing(dp(3), 1);
        text.setPadding(0, dp(3), 0, dp(4));
        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return text;
    }

    Button button(String caption, Runnable action) {
        var button = new Button(context);
        button.setAllCaps(false);
        button.setText(caption);
        button.setTextColor(BRAND);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(220, 236, 227)));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    Button primary(String caption, Runnable action) {
        Button button = button(caption, action);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(ColorStateList.valueOf(BRAND));
        return button;
    }

    EditText field(LinearLayout parent, String caption, String value, int type) {
        var edit = new EditText(context);
        edit.setInputType(type);
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            edit.setSaveEnabled(false);
        }
        edit.setText(value);
        edit.setSingleLine((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        int id = View.generateViewId();
        edit.setId(id);
        var label = text(caption, 14, MUTED, true);
        label.setLabelFor(id);
        parent.addView(label);
        parent.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }

    <T> Selector<T> select(LinearLayout parent, String caption, List<Option<T>> options, T initial) {
        var spinner = new Spinner(context);
        int id = View.generateViewId();
        spinner.setId(id);
        var label = text(caption, 14, MUTED, true);
        label.setLabelFor(id);
        parent.addView(label);
        var adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                options.stream().map(Option::label).toArray(String[]::new));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int index = 0; index < options.size(); index++) {
            if (java.util.Objects.equals(initial, options.get(index).value())) {
                spinner.setSelection(index);
                break;
            }
        }
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(43)));
        return new Selector<>(spinner, options);
    }

    CheckBox check(LinearLayout parent, String caption, boolean checked) {
        var box = new CheckBox(context);
        box.setText(caption);
        box.setChecked(checked);
        parent.addView(box);
        return box;
    }

    ScrollView scroll(View content) {
        var scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
        return scroll;
    }

    GradientDrawable background(int color, int radius) {
        var shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    void message(String title, String message) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Закрыть", null).show();
    }

    void confirm(String title, String message, Runnable confirmed) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setNegativeButton("Отмена", null).setPositiveButton("Подтвердить", (dialog, id) -> confirmed.run())
                .show();
    }

    AlertDialog form(String title, LinearLayout fields) {
        fields.setPadding(dp(19), dp(8), dp(19), dp(8));
        return new AlertDialog.Builder(context).setTitle(title).setView(scroll(fields))
                .setNegativeButton("Отмена", null).setPositiveButton("Сохранить", null).create();
    }

    static String value(EditText field) {
        return field.getText().toString().trim();
    }

    record Option<T>(T value, String label) {}
    record Selector<T>(Spinner control, List<Option<T>> options) {
        T value() {
            int position = control.getSelectedItemPosition();
            return position < 0 || position >= options.size() ? null : options.get(position).value();
        }
    }
}
