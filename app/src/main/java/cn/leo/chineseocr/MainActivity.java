package cn.leo.chineseocr;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import cn.leo.chineseocr.OCR.CameraView;
import cn.leo.chineseocr.OCR.OcrScanner;
import cn.leo.permission.PermissionRequest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private CameraView mCameraView;
    private Button mBtnScan;
    private TextView mTvResult;
    private OcrScanner mOcrScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //全屏无状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        mCameraView = findViewById(R.id.camera);
        mTvResult = findViewById(R.id.tvResult);
        mBtnScan = findViewById(R.id.btnScan);
        mBtnScan.setOnClickListener(this);
        init();
    }

    @PermissionRequest({Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA})
    private void init() {
        mOcrScanner = OcrScanner.build(this);
        mOcrScanner.setOnOcrResultListener(new OcrScanner.OnOcrResultListener() {
            @Override
            public void onOcrResult(String result) {
                mTvResult.setText(result);
                mBtnScan.setEnabled(true);
            }
        });
        mCameraView.setOnBitmapCreateListener(new CameraView.OnBitmapCreateListener() {
            @Override
            public void onBitmapCreate(Bitmap bitmap) {
                mOcrScanner.scan(bitmap);
            }
        });
    }

    @Override
    public void onClick(View v) {
        mBtnScan.setEnabled(false);
        mCameraView.takePicture();
    }
}
