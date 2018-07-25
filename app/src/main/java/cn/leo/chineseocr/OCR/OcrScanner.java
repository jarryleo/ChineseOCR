package cn.leo.chineseocr.OCR;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;

import cn.leo.chineseocr.BuildConfig;
import cn.leo.magic.annotation.RunOnIOThread;
import cn.leo.magic.annotation.RunOnUIThread;

/**
 * create by : Jarry Leo
 * date : 2018/7/21 15:15
 */
public class OcrScanner {
    private String dataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/tesseract/";
    private static TessBaseAPI mTess;
    private Context mContext;
    private boolean mInit;
    private OnOcrResultListener mOnOcrResultListener;

    public static OcrScanner build(Context context) {
        return new OcrScanner(context);
    }

    private OcrScanner(Context context) {
        mContext = context;
        copyFile();
    }

    @RunOnIOThread
    private void copyFile() {
        File dir = new File(dataPath + "tessdata/");
        FileUtil.CopyAssets(mContext, "tessdata", dir.getAbsolutePath());
        initTessBaseData(dataPath);
    }

    private void initTessBaseData(String dataPath) {
        mTess = new TessBaseAPI();
        String language = "chi_sim";
        mTess.setDebug(BuildConfig.DEBUG);
        mInit = mTess.init(dataPath, language);
    }

    @RunOnIOThread
    public void scan(Bitmap bitmap) {
        if (mOnOcrResultListener == null) return;
        String s = ocrTextScan(bitmap);
        callback(s);
    }

    @RunOnUIThread
    private void callback(String result) {
        mOnOcrResultListener.onOcrResult(result);
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
