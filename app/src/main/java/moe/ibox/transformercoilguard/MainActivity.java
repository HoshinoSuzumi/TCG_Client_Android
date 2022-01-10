package moe.ibox.transformercoilguard;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.sql.Ref;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lecho.lib.hellocharts.listener.LineChartOnValueSelectListener;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.view.LineChartView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    /* UI Components */
    private Button btn_refresh, btn_clear;
    private ScrollView loggingView;
    private TextView textView;

    public static void scrollToBottom(final ScrollView scroll, final View inner) {
        Handler mHandler = new Handler();
        mHandler.post(() -> {
            if (scroll == null || inner == null) return;
            int offset = inner.getMeasuredHeight() - scroll.getHeight();
            if (offset < 0) offset = 0;
            scroll.smoothScrollTo(0, offset);
        });
    }

    @SuppressLint("HandlerLeak")
    private final Handler logHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            textView.append(msg.obj + "\n");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            loggingView.fullScroll(ScrollView.FOCUS_DOWN);
            scrollToBottom(loggingView, textView);
        }
    };

    private void log(String content) {
        Message msg = new Message();
        msg.what = 0x01;
        msg.obj = content;
        logHandler.sendMessage(msg);
    }

    private LineChartView chartView;
    private LineChartData chartData;

    OkHttpClient client;

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData> blobDataJsonAdapter = moshi.adapter(moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData.class);
    moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData blobData;

    @SuppressLint({"SetTextI18n", "StaticFieldLeak"})
    private class RefreshChartTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... url) {
            client = new OkHttpClient().newBuilder()
                    .build();
            Request request = new Request.Builder()
                    .url("https://tcgtelemetry.blob.core.windows.net/all-telemetry-data/TransformerCoilGuard/01/2022-01-07/08-48.json?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D")
                    .method("GET", null)
                    .build();
            try {
                log("Fetching blob data...");
                Response response = client.newCall(request).execute();
                log("Got blob, parsing...");
                String result = Objects.requireNonNull(response.body()).string();
                String[] records = result.split("\n");
                blobData = blobDataJsonAdapter.fromJson(records[records.length - 1]);
                log("Rendering chart...");
                List<PointValue> chartValues = new ArrayList<PointValue>();
                for (int i = 0; i < blobData.Body.voltages.length; i++) {
                    chartValues.add(new PointValue(i, (float) blobData.Body.voltages[i]));
                }
                Line line = new Line(chartValues);
                line.setColor(Color.parseColor("#FFBB86FC"))
                        .setCubic(true)
                        .setStrokeWidth(2)
                        .setPointRadius(2);
                List<Line> lines = new ArrayList<Line>();
                lines.add(line);

                chartData = new LineChartData();
                chartData.setLines(lines);
                chartView.setLineChartData(chartData);

                log("Done.");
                runOnUiThread(() -> btn_refresh.setEnabled(true));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* UI initialization */
        textView = findViewById(R.id.tv);
        btn_refresh = findViewById(R.id.btn_refresh);
        btn_clear = findViewById(R.id.btn_clear);
        loggingView = findViewById(R.id.scrollView);
        loggingView.setSmoothScrollingEnabled(true);
        chartView = findViewById(R.id.chart);
        chartView.setOnValueTouchListener(new ValueTouchListener());
        chartView.setInteractive(false);

        btn_refresh.setOnClickListener(new ControlOnClickListener());
        btn_clear.setOnClickListener(new ControlOnClickListener());

        log("Initialization...");

        new RefreshChartTask().execute();

    }

    private class ControlOnClickListener implements View.OnClickListener {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_refresh:
                    btn_refresh.setEnabled(false);
                    log("Refreshing chart...");
                    new RefreshChartTask().execute();
                    break;
                case R.id.btn_clear:
                    textView.setText("");
                    break;
            }
        }
    }

    private class ValueTouchListener implements LineChartOnValueSelectListener {

        @Override
        public void onValueSelected(int lineIndex, int pointIndex, PointValue value) {
            Toast.makeText(MainActivity.this, "Selected: " + value, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onValueDeselected() {

        }
    }
}