package moe.ibox.transformercoilguard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.material.snackbar.Snackbar;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
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
    private ProgressBar autoRefreshProgress;

    /* Shared storage */
    SharedPreferences systemPreferences;
    SharedPreferences.Editor systemPreferencesEditor;

    /* Auto refresh Timer */
    private Thread autoRefreshThread;
    private long entropy;
    private final int autoRefreshSecs = 30;  // sec
    private final int granularity = 10; // ms
    private final int stepPerSec = 1000 / granularity;


    /* Network */
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

    /* View processing */
    private enum CoilGroup {
        A, B, C
    }

    /* Default viewing coil */
    private CoilGroup MonitoringCoil = CoilGroup.A;

    /* Tool methods */
    private void showSnackBar(String title, boolean showOnTop) {
        runOnUiThread(() -> {
            Snackbar snack = Snackbar.make(MainActivity.this, findViewById(R.id.mainView), title, Snackbar.LENGTH_LONG);
            View snackView = snack.getView();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackView.getLayoutParams();
            if (showOnTop) {
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                params.topMargin = 200;
            } else {
                params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                params.bottomMargin = 30;
            }
            snackView.setLayoutParams(params);
            snack.show();
        });
    }

    private void showSnackBar(String title) {
        showSnackBar(title, false);
    }

    /* Data processing */
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
                showSnackBar(getString(R.string.app_err_fetch_blob_list));
                return null;
            }
            String blobUrl = "https://tcgtelemetry.blob.core.windows.net/all-telemetry-data/" + blobPath + "?sp=rl&st=2022-01-07T08:32:47Z&se=2025-01-07T16:32:47Z&spr=https&sv=2020-08-04&sr=c&sig=4NoRuN64Z77%2FAgx6XkeyNEYYmn78JsJV373FW8JMR78%3D";
            try {
                String result = httpGetFromUrl(blobUrl);
                if (result == null) {
                    runOnUiThread(() -> Snackbar.make(MainActivity.this, findViewById(R.id.mainView), getString(R.string.app_err_fetch_data), Snackbar.LENGTH_LONG).show());
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
                showSnackBar(getString(R.string.app_render_finished));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_refresh:
                entropy = 0;
                item.setEnabled(false);
                new RefreshChartTask().execute();
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(() -> {
                        item.setEnabled(true);
                    });
                }).start();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Init SharedPreferences */
        systemPreferences = getSharedPreferences("systemPreferences", MODE_PRIVATE);
        systemPreferencesEditor = systemPreferences.edit();

        /* 沉浸式处理 */
        ImmersionBar.with(this).transparentBar().hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).init();
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
//        getWindow().setNavigationBarColor(Color.TRANSPARENT);
//        getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//        );

        /* HTTP Client initialization */
        httpClient = new OkHttpClient().newBuilder().build();

        /* UI initialization */
        lissajousFigure = findViewById(R.id.lissajous);
        lissajousFigure.setInteractive(false);
        ((RadioGroup) findViewById(R.id.radioGroupCoilSelect)).setOnCheckedChangeListener((group, checkedId) -> {
            switch (checkedId) {
                case R.id.radioButtonCoilA:
                    MonitoringCoil = CoilGroup.A;
                    showSnackBar("正在显示 A 绕组数据");
                    break;
                case R.id.radioButtonCoilB:
                    MonitoringCoil = CoilGroup.B;
                    showSnackBar("正在显示 B 绕组数据");
                    break;
                case R.id.radioButtonCoilC:
                    MonitoringCoil = CoilGroup.C;
                    showSnackBar("正在显示 C 绕组数据");
                    break;
            }
//            new RefreshChartTask().execute();
        });
        autoRefreshProgress = findViewById(R.id.progress_auto_refresh);
        autoRefreshProgress.setMax(autoRefreshSecs * stepPerSec);

        /* Auto refresh thread */
        autoRefreshThread = new Thread(() -> {
            while (true) {
                runOnUiThread(() -> {
                    autoRefreshProgress.setProgress((int) entropy % (autoRefreshSecs * stepPerSec));
//                    if (entropy % 100 == 0) Log.i("ENTROPY", String.valueOf(entropy));
                    if ((entropy % (autoRefreshSecs * stepPerSec) == 0)) {
                        new RefreshChartTask().execute();
                    }
                });
                entropy++;
                try {
                    //noinspection BusyWait
                    Thread.sleep(granularity);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        autoRefreshThread.start();

        /* The first data fetching and viewing */
        new RefreshChartTask().execute();

        if (!systemPreferences.getBoolean("guideCompleted", false)) {
            new TapTargetSequence(this).targets(
                    TapTarget.forView(findViewById(R.id.radioGroupCoilSelect), getString(R.string.guide_stp1_title), getString(R.string.guide_stp1_description))
                            .cancelable(false)
                            .dimColor(R.color.black)
                            .tintTarget(false)
                            .targetRadius(100)
                            .transparentTarget(true),
                    TapTarget.forView(findViewById(R.id.iconViewTemp), getString(R.string.guide_stp2_title), getString(R.string.guide_stp2_description))
                            .cancelable(false)
                            .dimColor(R.color.black)
                            .tintTarget(false),
                    TapTarget.forView(findViewById(R.id.iconViewCoilTemp), getString(R.string.guide_stp3_title), getString(R.string.guide_stp3_description))
                            .cancelable(false)
                            .dimColor(R.color.black)
                            .tintTarget(false),
                    TapTarget.forView(findViewById(R.id.iconViewVoltage), getString(R.string.guide_stp4_title), getString(R.string.guide_stp4_description))
                            .cancelable(false)
                            .dimColor(R.color.black)
                            .tintTarget(false)
            ).listener(new TapTargetSequence.Listener() {
                @Override
                public void onSequenceFinish() {
                    systemPreferencesEditor.putBoolean("guideCompleted", true).commit();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoRefreshThread = null;
    }
}