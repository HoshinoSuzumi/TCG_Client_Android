package moe.ibox.transformercoilguard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

public class MainActivityBeta extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_beta);

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
        ).start();

//        TapTargetView.showFor(this,
//                TapTarget.forView(findViewById(R.id.radioGroupCoilSelect), "多组数据查看", "可以在这里选择不同绕组的数据进行查看")
//                        .dimColor(R.color.black)
//                        .drawShadow(false)
//                        .tintTarget(false)
//                        .targetRadius(100),
//                new TapTargetView.Listener() {
//                    @Override
//                    public void onTargetClick(TapTargetView view) {
//                        super.onTargetClick(view);
//                        Toast.makeText(MainActivityBeta.this, "Clicked", Toast.LENGTH_SHORT).show();
//                    }
//                }
//        );
    }
}