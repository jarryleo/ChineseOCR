package cn.leo.chineseocr.OCR;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

import cn.leo.chineseocr.BuildConfig;

/**
 * create by : Jarry Leo
 * date : 2018/7/21 15:15
 */
public class OcrScanner {
    private String dataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tesseract/";
    private static TessBaseAPI mTess;
    private Context mContext;
    private boolean mInit;
    private Handler mHandler = new Handler(Looper.myLooper());
    private OnOcrResultListener mOnOcrResultListener;

    public static OcrScanner build(Context context) {
        return new OcrScanner(context);
    }

    private OcrScanner(Context context) {
        mContext = context;
        copyFile();
    }

    private void copyFile() {
        final File dir = new File(dataPath + "tessdata/");
        new Thread() {
            @Override
            public void run() {
                FileUtil.CopyAssets(mContext, "tessdata", dir.getAbsolutePath());
                initTessBaseData(dataPath);
            }
        }.start();
    }

    private void initTessBaseData(String dataPath) {
        mTess = new TessBaseAPI();
        String language = "chi_sim";
        mTess.setDebug(BuildConfig.DEBUG);
        mInit = mTess.init(dataPath, language);
    }


    public void scan(final Bitmap bitmap) {
        new Thread() {
            @Override
            public void run() {
                String s = ocrTextScan(bitmap);
                callback(s);
            }
        }.start();
    }

    private void callback(final String result) {
        if (mOnOcrResultListener == null) return;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mOnOcrResultListener.onOcrResult(result);
            }
        });
    }

    private String ocrTextScan(Bitmap bitmap) {
        if (!mInit) return "";
        mTess.setImage(bitmap);
        Log.i("-----", "getBoxText: " + mTess.getBoxText(0));
        return mTess.getUTF8Text();
    }

    public void setOnOcrResultListener(OnOcrResultListener listener) {
        mOnOcrResultListener = listener;
    }

    public interface OnOcrResultListener {
        void onOcrResult(String result);
    }

}
