package cn.leo.chineseocr.OCR;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Process;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * create by : Jarry Leo
 * date : 2018/7/20 9:15
 */
public class CameraView extends TextureView implements LifecycleObserver, TextureView.SurfaceTextureListener, View.OnClickListener {
    private String TAG = "CameraView";
    private static Camera mCamera;
    private Camera.Parameters mParameters;
    private OnBitmapCreateListener mBitmapCreateListener;
    private Rect mBitmapRect = new Rect();
    //获取预览图像变量
    private long mLastTakePicTime;
    private RenderScript mRs;
    private ScriptIntrinsicYuvToRGB mYuvToRgbIntrinsic;
    private Type.Builder mYuvType;
    private Type.Builder mRgbaType;
    private Allocation mIn;
    private Allocation mOut;

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        if (!checkCameraHardware(context)) {
            Toast.makeText(context, "没有检测到摄像头", Toast.LENGTH_SHORT).show();
            return;
        }
        if (context instanceof LifecycleOwner) {
            Lifecycle lifecycle = ((LifecycleOwner) context).getLifecycle();
            lifecycle.addObserver(this);
            setSurfaceTextureListener(this);
            setOnClickListener(this);
            mRs = RenderScript.create(getContext());
            mYuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(mRs, Element.U8_4(mRs));
        }
    }

    //检查是否存在摄像头
    private boolean checkCameraHardware(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void requestCamera(int mCameraId) {
        openCamera(mCameraId);
        initParameters();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        requestCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
        //requestCamera(Camera.CameraInfo.CAMERA_FACING_FRONT);
        setKeepScreenOn(true);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        stopPreview();
        setKeepScreenOn(false);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        closeCamera();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        initPreViewSize(width, height);
        setDisplayOrientation(90);
        setPreviewTexture(surface);
        startPreview();
        Log.i(TAG, "onSurfaceTextureAvailable: size:" + width + "," + height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    private void setDisplayOrientation(int degree) {
        if (mCamera != null) {
            mCamera.setDisplayOrientation(degree);
        }
        Log.i(TAG, "Set display orientation is : " + degree);
    }

    private void setPreviewTexture(SurfaceTexture surfaceTexture) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开启摄像头
     *
     * @param cameraId 摄像头id
     */
    private synchronized void openCamera(int cameraId) {
        try {
            if (mCamera == null) {
                mCamera = Camera.open(cameraId);
                Log.i(TAG, "Camera has opened, cameraId is " + cameraId);
            } else {
                startPreview();
            }
        } catch (Exception e) {
            Log.e(TAG, "Open Camera has exception!");
        }
    }

    private void startPreview() {
        if (mCamera != null) {
            mCamera.setErrorCallback(mErrorCallback);
            mCamera.startPreview();
            autoFocus();
            Log.i(TAG, "Camera Preview has started!");
        }
    }

    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            Log.i(TAG, "Camera Preview has stopped!");
        }
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.setErrorCallback(null);
            mCamera.release();
            mCamera = null;
            Log.i(TAG, "Camera has closed!");
        }
    }

    public void takePicture() {
        if (mCamera != null) {
            //mCamera.takePicture(null, null, mPictureCallback);
            mCamera.setPreviewCallback(mPreviewCallback);
        }
    }

    private Camera.ErrorCallback mErrorCallback = new Camera.ErrorCallback() {
        @Override
        public void onError(int error, Camera camera) {
            Log.e(TAG, "onError: got camera error callback: " + error);
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                Process.killProcess(Process.myPid());
            }
        }
    };
    private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {
        @Override
        public void onPictureTaken(final byte[] data, Camera camera) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            createBitmap(size.width, size.height, data);
            startPreview();
        }
    };

    private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            mCamera.setPreviewCallback(null);
            if (System.currentTimeMillis() - mLastTakePicTime < 1000) return;
            mLastTakePicTime = System.currentTimeMillis();
            Camera.Size size = camera.getParameters().getPreviewSize();
            //createBitmap(size.width, size.height, data);
            createPreBitmap(data.length, size.width, size.height, data);
        }
    };

    private void initParameters() {
        mParameters = getParameters();
    }

    private Camera.Parameters getParameters() {
        if (mCamera != null) {
            return mCamera.getParameters();
        }
        return null;
    }

    private void setParameters() {
        if (mCamera != null && mParameters != null) {
            mCamera.setParameters(mParameters);
        }
    }

    private void initPreViewSize(int width, int height) {
        if (mParameters == null) {
            return;
        }
        List<Camera.Size> previewSizes = mParameters.getSupportedPreviewSizes();
        List<Camera.Size> pictureSizes = mParameters.getSupportedPictureSizes();
        Camera.Size preSize = getOptimalSize(previewSizes, width, height);
        Camera.Size picSize = getOptimalSize(pictureSizes, width, height);
        Log.i(TAG, "initPreViewSize: " + preSize.width + " x " + preSize.height);
        Log.i(TAG, "initPicViewSize: " + picSize.width + " x " + picSize.height);
        mParameters.setPreviewSize(preSize.width, preSize.height);
        mParameters.setPictureSize(picSize.width, picSize.height);
        setParameters();
    }

    //选择sizeMap中大于并且最接近width和height的size
    private Camera.Size getOptimalSize(List<Camera.Size> sizeMap, int width, int height) {
        List<Camera.Size> sizeList = new ArrayList<>();
        for (Camera.Size option : sizeMap) {
            if (width > height) {
                if (option.width >= width && option.height >= height) {
                    sizeList.add(option);
                }
            } else {
                if (option.width >= height && option.height >= width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Camera.Size>() {
                @Override
                public int compare(Camera.Size lhs, Camera.Size rhs) {
                    return Long.signum(lhs.width * lhs.height - rhs.width * rhs.height);
                }
            });
        }
        return sizeMap.get(0);
    }

    @Override
    public void onClick(View v) {
        autoFocus();
    }

    private void autoFocus() {
        mCamera.autoFocus(null);
    }

    private void createBitmap(int prevSizeW, int prevSizeH, byte[] data) {
        Bitmap bmpOut = BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bitmap;
        int rw = prevSizeH * 2 / 3;
        mBitmapRect.set(prevSizeW / 2 - rw / 2, rw / 4, prevSizeW / 2 + rw / 2, rw + rw / 4);
        bitmap = Bitmap.createBitmap(bmpOut, mBitmapRect.left, mBitmapRect.top, mBitmapRect.width(), mBitmapRect.height(), matrix, true);
        bmpOut.recycle();
        if (mBitmapCreateListener != null) {
            mBitmapCreateListener.onBitmapCreate(bitmap);
        }
    }

    private void createPreBitmap(int dataLength, int prevSizeW, int prevSizeH, byte[] data) {
        if (mYuvType == null) {
            mYuvType = new Type.Builder(mRs, Element.U8(mRs)).setX(dataLength);
            mIn = Allocation.createTyped(mRs, mYuvType.create(), Allocation.USAGE_SCRIPT);
            mRgbaType = new Type.Builder(mRs, Element.RGBA_8888(mRs)).setX(prevSizeW).setY(prevSizeH);
            mOut = Allocation.createTyped(mRs, mRgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        mIn.copyFrom(data);
        mYuvToRgbIntrinsic.setInput(mIn);
        mYuvToRgbIntrinsic.forEach(mOut);
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bmpOut = Bitmap.createBitmap(prevSizeW, prevSizeH, Bitmap.Config.ARGB_8888);
        mOut.copyTo(bmpOut);
        int rw = prevSizeH * 2 / 3;
        mBitmapRect.set(prevSizeW / 2 - rw / 4, rw / 4, prevSizeW / 2 + rw / 4, rw + rw / 4);
        Bitmap bitmap = Bitmap.createBitmap(bmpOut, mBitmapRect.left, mBitmapRect.top, mBitmapRect.width(), mBitmapRect.height(), matrix, true);
        bmpOut.recycle();
        if (mBitmapCreateListener != null) {
            mBitmapCreateListener.onBitmapCreate(bitmap);
        }
    }

    public void setOnBitmapCreateListener(OnBitmapCreateListener onBitmapCreateListener) {
        mBitmapCreateListener = onBitmapCreateListener;
    }

    public interface OnBitmapCreateListener {
        void onBitmapCreate(Bitmap bitmap);
    }
}
