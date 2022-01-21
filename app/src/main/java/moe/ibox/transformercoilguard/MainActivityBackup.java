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

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import lecho.lib.hellocharts.listener.LineChartOnValueSelectListener;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.view.LineChartView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivityBackup extends AppCompatActivity {

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
            scrollToBottom(loggingView, textView);
        }
    };

    private enum logType {

        INFO("[-]"), WARN("[!]"), ERROR("[×]"), DONE("[√]");

        private final String type;

        logType(String type) {
            this.type = type;
        }

        public String getPrefix() {
            return type;
        }
    }

    private void log(String content, logType type) {
        Message msg = new Message();
        msg.what = 0x01;
        msg.obj = type.getPrefix() + " " + content;
        logHandler.sendMessage(msg);
    }

    private LineChartView chartView;
    private LineChartView lissajousFigure;

    OkHttpClient client;

    ArrayList<XMLAdapter.BlobEnumList> blobEnumLists = new ArrayList<XMLAdapter.BlobEnumList>();
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder documentBuilder;

    {
        try {
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData> blobDataJsonAdapter = moshi.adapter(moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData.class);
    moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData blobData;

    private String GetBlobList() {
        Request request = new Request.Builder()
                .url("https://tcgtelemetry.blob.core.windows.net/all-telemetry-data?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D&restype=container&comp=list")
                .method("GET", null)
                .build();
        try {
            log("Fetching blob list...", logType.INFO);
            Response response = client.newCall(request).execute();
            String result = Objects.requireNonNull(response.body()).string();
            log("Parsing blob list..", logType.INFO);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
            Document document = documentBuilder.parse(inputStream);
            NodeList nodeList = document.getElementsByTagName("Blob");
            String blobPath = nodeList.item(nodeList.getLength() - 1).getFirstChild().getTextContent();
            log("Got latest blob path [" + blobPath + "]", logType.INFO);
            return blobPath;
        } catch (IOException | SAXException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressLint({"SetTextI18n", "StaticFieldLeak"})
    private class RefreshChartTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... url) {
            String blobPath = GetBlobList();
            Request request = new Request.Builder()
                    .url("https://tcgtelemetry.blob.core.windows.net/all-telemetry-data/" + blobPath + "?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D")
                    .method("GET", null)
                    .build();
            try {
                log("Fetching blob data...", logType.INFO);
                Response response = client.newCall(request).execute();
                String result = Objects.requireNonNull(response.body()).string();
                log("Got blob[" + result.length() + " bytes], parsing...", logType.INFO);
                String[] records = result.split("\n");
                blobData = blobDataJsonAdapter.fromJson(records[records.length - 1]);
                log("Rendering chart...", logType.INFO);

                List<PointValue> chartValueOfVoltage = new ArrayList<>();
                List<PointValue> chartValueOfCurrent = new ArrayList<>();
                List<PointValue> lissajousFigureValue = new ArrayList<>();
                for (int i = 0; i < blobData.Body.voltages.length; i++) {
                    chartValueOfVoltage.add(new PointValue(i, (float) blobData.Body.voltages[i]));
                }
                for (int i = 0; i < blobData.Body.currents.length; i++) {
                    chartValueOfCurrent.add(new PointValue(i, (float) blobData.Body.currents[i]));
                }
                for (int i = 0; i < blobData.Body.lissajous.X.length; i++) {
                    lissajousFigureValue.add(new PointValue((float) blobData.Body.lissajous.X[i], (float) blobData.Body.lissajous.Y[i]));
                }

                Line line_voltage = new Line(chartValueOfVoltage).setColor(Color.YELLOW);
                Line line_current = new Line(chartValueOfCurrent).setColor(Color.GREEN);

                Line line_lissajous = new Line(lissajousFigureValue).setColor(Color.MAGENTA);

                List<Line> lines = new ArrayList<>();
                lines.add(line_voltage);
                lines.add(line_current);

                List<Line> lissajousLines = new ArrayList<>();
                lissajousLines.add(line_lissajous);

                lines.forEach(line -> line
                        .setCubic(true)
                        .setStrokeWidth(1)
                        .setPointRadius(0)
                        .setFilled(false)
                        .setShape(ValueShape.CIRCLE)
                );
                lissajousLines.forEach(line -> line
                        .setCubic(true)
                        .setStrokeWidth(2)
                        .setPointRadius(0)
                        .setFilled(false)
                        .setShape(ValueShape.CIRCLE)
                );

                Axis axisX = new Axis();
                Axis axisY = new Axis();

                axisX.setTextColor(Color.GRAY)
                        .setName("Samples")
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);
                axisY.setTextColor(Color.GRAY)
                        .setName("U & I")
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);

                Axis lissaAxisX = new Axis();
                Axis lissaAxisY = new Axis();

                lissaAxisX.setTextColor(Color.GRAY)
                        .setName("X")
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);
                lissaAxisY.setTextColor(Color.GRAY)
                        .setName("Y")
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);

                LineChartData chartData = new LineChartData();
                chartData.setLines(lines);
                chartData.setAxisXBottom(axisX);
                chartData.setAxisYLeft(axisY);

                LineChartData lissajousChartData = new LineChartData();
                lissajousChartData.setLines(lissajousLines);
                lissajousChartData.setAxisXBottom(lissaAxisX);
                lissajousChartData.setAxisYLeft(lissaAxisY);

                chartView.setLineChartData(chartData);
                lissajousFigure.setLineChartData(lissajousChartData);

                log("Done.", logType.DONE);
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
        chartView.setInteractive(true);
        chartView.setOnValueTouchListener(new ValueTouchListener());

        lissajousFigure = findViewById(R.id.lissajous);
        lissajousFigure.setInteractive(true);
        lissajousFigure.setOnValueTouchListener(new ValueTouchListener());

        btn_refresh.setOnClickListener(new ControlOnClickListener());
        btn_clear.setOnClickListener(new ControlOnClickListener());

        log("Initialization...", logType.INFO);

        client = new OkHttpClient().newBuilder().build();

        new RefreshChartTask().execute();

    }

    private class ControlOnClickListener implements View.OnClickListener {
        @SuppressLint("NonConstantResourceId")
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_refresh:
                    btn_refresh.setEnabled(false);
                    log("Refreshing chart...", logType.INFO);
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
            Toast.makeText(MainActivityBackup.this, value.getX() + " 处的" + (lineIndex == 0 ? "电压" : "电流") + "为 " + value.getY() + (lineIndex == 0 ? "V" : "A"), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onValueDeselected() {

        }
    }
}