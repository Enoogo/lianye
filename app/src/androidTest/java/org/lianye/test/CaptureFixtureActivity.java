package org.lianye.test;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Standalone native fixture in the test APK; no target-app runtime is required. */
public class CaptureFixtureActivity extends Activity {
    private ScrollView scroll;
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra("scrollY")) scroll.post(() -> scroll.scrollTo(0, intent.getIntExtra("scrollY", 0)));
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding((int)(24 * density), (int)(24 * density), (int)(24 * density), (int)(24 * density));
        column.setBackgroundColor(Color.rgb(250, 248, 243));
        for (int index = 0; index < 28; index++) {
            if (getIntent().getBooleanExtra("animation", false) && index % 3 == 0) {
                View video = new View(this) {
                    final Paint paint = new Paint();
                    @Override protected void onDraw(Canvas canvas) {
                        paint.setColor(Color.rgb((int)(System.currentTimeMillis() / 90 % 180) + 40, 70, 120));
                        canvas.drawRect(0, 0, getWidth() / 2f, getHeight(), paint);
                        postInvalidateDelayed(90);
                    }
                };
                column.addView(video, new LinearLayout.LayoutParams(-1, (int)(110 * density)));
            }
            TextView section = new TextView(this);
            section.setText("Section " + (index + 1) + " / 28\n\nOffline capture test. Each section has a unique position.\n\nThis page contains generated content only.\n\nEnd marker: " + (index + 1));
            section.setTextSize(18f);
            section.setTextColor(Color.rgb(36, 39, 34));
            section.setPadding((int)(12 * density), (int)(20 * density), (int)(12 * density), (int)(20 * density));
            section.setBackgroundColor(index % 2 == 0 ? Color.rgb(229, 236, 248) : Color.WHITE);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = (int)(16 * density);
            column.addView(section, params);
        }
        scroll = new ScrollView(this);
        scroll.addView(column);
        if (getIntent().getBooleanExtra("chrome", false)) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            TextView header = new TextView(this);
            header.setText("Fixed navigation\n\nGenerated scroll / dynamic content test");
            header.setTextSize(22); header.setPadding(24, 24, 24, 24);
            header.setBackgroundColor(Color.rgb(223, 233, 227));
            root.addView(header, new LinearLayout.LayoutParams(-1, (int)(150 * density)));
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView footer = new TextView(this);
            footer.setText("Fixed footer · retained once"); footer.setTextSize(18);
            footer.setBackgroundColor(Color.rgb(223, 233, 227));
            root.addView(footer, new LinearLayout.LayoutParams(-1, (int)(64 * density)));
            setContentView(root);
        } else setContentView(scroll);
    }
}
