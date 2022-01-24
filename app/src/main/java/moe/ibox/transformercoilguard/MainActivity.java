package moe.ibox.transformercoilguard;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.snackbar.Snackbar;
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

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.view.LineChartView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    /* UI Components */
    private LineChartView lissajousFigure;

    private OkHttpClient httpClient;

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

    private enum CoilGroup {
        A, B, C
    }

    private CoilGroup MonitoringCoil = CoilGroup.A;

    private String httpGetFromUrl(@NonNull String url) {
        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            return Objects.requireNonNull(response.body()).string();
        } catch (IOException e) {
            return null;
        }
    }

    private String GetBlobList() {
        String blobListUrl = "https://tcgtelemetry.blob.core.windows.net/all-telemetry-data?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D&restype=container&comp=list";
        String result = httpGetFromUrl(blobListUrl);
        if (result == null) return null;
        ByteArrayInputStream inputStream = new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8));
        Document document;
        try {
            document = documentBuilder.parse(inputStream);
        } catch (IOException | SAXException e) {
            return null;
        }
        assert document != null;
        NodeList nodeList = document.getElementsByTagName("Blob");
        return nodeList.item(nodeList.getLength() - 1).getFirstChild().getTextContent();
    }

    @SuppressLint({"SetTextI18n", "StaticFieldLeak"})
    private class RefreshChartTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... url) {
            String blobPath = GetBlobList();
            if (blobPath == null) {
                runOnUiThread(() -> Snackbar.make(MainActivity.this, findViewById(R.id.mainView), "获取数据存档列表失败", Snackbar.LENGTH_LONG).show());
                return null;
            }
            String blobUrl = "https://tcgtelemetry.blob.core.windows.net/all-telemetry-data/" + blobPath + "?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D";
            try {
                String result = httpGetFromUrl(blobUrl);
                if (result == null) {
                    runOnUiThread(() -> Snackbar.make(MainActivity.this, findViewById(R.id.mainView), "获取数据失败", Snackbar.LENGTH_LONG).show());
                    return null;
                }
                String[] records = result.split("\n");
                blobData = blobDataJsonAdapter.fromJson(records[records.length - 1]);

                assert blobData != null;
                moe.ibox.transformercoilguard.JsonAdapter.AzureBlobData.LissajousBean.LissajousAxis lissajousAxis;
                switch (MonitoringCoil) {
                    case A:
                        lissajousAxis = blobData.Body.lissajous.A;
                        break;
                    case B:
                        lissajousAxis = blobData.Body.lissajous.B;
                        break;
                    case C:
                        lissajousAxis = blobData.Body.lissajous.C;
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + MonitoringCoil);
                }

                List<PointValue> lissajousFigureValue = new ArrayList<>();
                for (int i = 0; i < lissajousAxis.X.length; i++) {
                    lissajousFigureValue.add(new PointValue((float) lissajousAxis.X[i], (float) lissajousAxis.Y[i]));
                }

                Line line_lissajous = new Line(lissajousFigureValue).setColor(Color.parseColor("#FF9800"));
                List<Line> lissajousLines = new ArrayList<>();
                lissajousLines.add(line_lissajous);
                lissajousLines.forEach(line -> line
                        .setCubic(true)
                        .setStrokeWidth(2)
                        .setPointRadius(0)
                        .setFilled(false)
                        .setShape(ValueShape.CIRCLE)
                );

                Axis lissaAxisX = new Axis();
                Axis lissaAxisY = new Axis();

                lissaAxisX.setTextColor(Color.GRAY)
                        .setName("X")
                        .setTextSize(15)
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);
                lissaAxisY.setTextColor(Color.GRAY)
                        .setName("Y")
                        .setTextSize(15)
                        .setHasLines(true)
                        .setLineColor(Color.LTGRAY);

                LineChartData lissajousChartData = new LineChartData();
                lissajousChartData.setLines(lissajousLines);
                lissajousChartData.setAxisXBottom(lissaAxisX);
                lissajousChartData.setAxisYLeft(lissaAxisY);

                lissajousFigure.setLineChartData(lissajousChartData);
                runOnUiThread(() -> Snackbar.make(MainActivity.this, findViewById(R.id.lissajous), "渲染图形成功", Snackbar.LENGTH_LONG).show());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_beta);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        httpClient = new OkHttpClient().newBuilder().build();

        /* UI initialization */
        lissajousFigure = findViewById(R.id.lissajous);
        lissajousFigure.setInteractive(true);
        ((RadioGroup) findViewById(R.id.radioGroupCoilSelect)).setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.radioButtonCoilA:
                    MonitoringCoil = CoilGroup.A;
                    break;
                case R.id.radioButtonCoilB:
                    MonitoringCoil = CoilGroup.B;
                    break;
                case R.id.radioButtonCoilC:
                    MonitoringCoil = CoilGroup.C;
                    break;
            }
            new RefreshChartTask().execute();
        });

        new TapTargetSequence(this).targets(
                TapTarget.forView(findViewById(R.id.radioGroupCoilSelect), "多组数据查看", "在这里选择不同绕组的监测图形进行查看")
                        .cancelable(false)
                        .dimColor(R.color.black)
                        .tintTarget(false)
                        .targetRadius(100)
                        .transparentTarget(true),
                TapTarget.forView(findViewById(R.id.iconViewTemp), "环境温度", "在这里监视变电器周遭环境温度")
                        .cancelable(false)
                        .dimColor(R.color.black)
                        .tintTarget(false),
                TapTarget.forView(findViewById(R.id.iconViewCoilTemp), "绕组温度", "在这里监视变电器各绕组的温度")
                        .cancelable(false)
                        .dimColor(R.color.black)
                        .tintTarget(false),
                TapTarget.forView(findViewById(R.id.iconViewVoltage), "绕组电压", "在这里监视变电器各组绕组的电压值")
                        .cancelable(false)
                        .dimColor(R.color.black)
                        .tintTarget(false)
        ).listener(new TapTargetSequence.Listener() {
            @Override
            public void onSequenceFinish() {
                new RefreshChartTask().execute();
            }

            @Override
            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {

            }

            @Override
            public void onSequenceCanceled(TapTarget lastTarget) {

            }
        }).start();
    }
}