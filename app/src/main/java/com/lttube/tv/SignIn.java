package com.lttube.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shows the code to type at yt.be/activate and waits for it. */
public final class SignIn {
    public interface Done { void done(); }

    public static void show(final Activity act, final Done done) {
        final Handler h = new Handler(Looper.getMainLooper());
        float d = act.getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(24 * d);
        col.setPadding(pad, pad, pad, pad);

        final TextView step = tv(act, 20, 0xFFDDDDDD, false);
        step.setText("On your phone or computer, go to");
        final TextView url = tv(act, 30, Color.WHITE, true);
        url.setText("yt.be/activate");
        final TextView and = tv(act, 20, 0xFFDDDDDD, false);
        and.setText("and enter this code:");
        final TextView code = tv(act, 52, 0xFF3EA6FF, true);
        code.setText("…");
        code.setLetterSpacing(0.12f);
        final TextView status = tv(act, 17, 0xFFAAAAAA, false);
        status.setText("Getting a code…");
        col.addView(step);
        col.addView(url);
        col.addView(and);
        col.addView(code);
        col.addView(status);

        final boolean[] open = {true};
        final AlertDialog dlg = new AlertDialog.Builder(act)
                .setTitle("Sign in to YouTube")
                .setView(col)
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(x -> open[0] = false)
                .show();

        Bg.run(Account::start, (dc, err) -> {
            if (!open[0]) return;
            if (dc == null) { status.setText("Couldn't get a code: " + Bg.message(err)); return; }
            code.setText(dc.userCode);
            url.setText(dc.url.replace("https://", "").replace("www.", ""));
            status.setText("Waiting for you to sign in… pick the account / channel you use.");
            final Runnable[] poll = new Runnable[1];
            poll[0] = () -> {
                if (!open[0]) return;
                Bg.run(() -> Account.poll(dc), (state, e) -> {
                    if (!open[0]) return;
                    if (state == null) { status.setText(Bg.message(e)); return; }
                    if ("ok".equals(state)) {
                        status.setText("Signed in! Loading your YouTube…");
                        Bg.run(() -> { Yti.loadProfile(); return true; }, (r, e2) -> {
                            dlg.dismiss();
                            done.done();
                        });
                        return;
                    }
                    if ("slow".equals(state)) dc.interval += 2;
                    h.postDelayed(poll[0], dc.interval * 1000L);
                });
            };
            h.postDelayed(poll[0], dc.interval * 1000L);
        });
    }

    private static TextView tv(Activity a, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 6, 0, 6);
        return t;
    }

    private SignIn() { }
}
