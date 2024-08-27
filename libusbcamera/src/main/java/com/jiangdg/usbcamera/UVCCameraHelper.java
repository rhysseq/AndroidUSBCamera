package com.jiangdg.usbcamera;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Environment;
import android.util.Log;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;

import com.jiangdg.libusbcamera.R;
import org.easydarwin.sw.TxtOverlay;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.common.UVCCameraHandler;
import com.serenegiant.usb.encoder.RecordParams;
import com.serenegiant.usb.widget.CameraViewInterface;


import java.io.File;
import java.util.List;
import java.util.Objects;

/** UVCCamera Helper class
 *
 * Created by jiangdongguo on 2017/9/30.
 */
public class UVCCameraHelper {
    public static final String ROOT_PATH = Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator;
    public static final String SUFFIX_JPEG = ".jpg";
    public static final String SUFFIX_MP4 = ".mp4";
    private static final String TAG = "UVCCameraHelper";
    private int previewWidth = 640;
    private int previewHeight = 480;
    public static final int FRAME_FORMAT_YUYV = UVCCamera.FRAME_FORMAT_YUYV;
    // Default using MJPEG
    // if your device is connected,but have no images
    // please try to change it to FRAME_FORMAT_YUYV
    public static final int FRAME_FORMAT_MJPEG = UVCCamera.FRAME_FORMAT_MJPEG;
    public static final int MODE_BRIGHTNESS = UVCCamera.PU_BRIGHTNESS;
    public static final int MODE_CONTRAST = UVCCamera.PU_CONTRAST;
    private int mFrameFormat = FRAME_FORMAT_MJPEG;

    private static UVCCameraHelper mCameraHelper;
    // USB Manager
    private USBMonitor mUSBMonitor;
    // Camera Handler
    private UVCCameraHandler mCameraHandler;
    private USBMonitor.UsbControlBlock mCtrlBlock;

    private Activity mActivity;
    private CameraViewInterface mCamView;

    private UVCCameraHelper() {
    }

    public static UVCCameraHelper getInstance() {
        Log.d("HANDLER","GET INSTANCE");
        if (mCameraHelper == null) {
            mCameraHelper = new UVCCameraHelper();
        }
        return mCameraHelper;
    }

    public void closeCamera() {
        if (mCameraHandler != null) {
            mCameraHandler.close();
        }
    }

    public interface OnMyDevConnectListener {
        void onAttachDev(UsbDevice device);

        void onDettachDev(UsbDevice device);

        void onConnectDev(UsbDevice device, boolean isConnected);

        void onDisConnectDev(UsbDevice device);
    }

    public void initUSBMonitor(Activity activity, CameraViewInterface cameraView, final OnMyDevConnectListener listener) {
        this.mActivity = activity;
        this.mCamView = cameraView;

        mUSBMonitor = new USBMonitor(activity.getApplicationContext(), new USBMonitor.OnDeviceConnectListener() {

            // called by checking usb device
            // do request device permission
            @Override
            public void onAttach(UsbDevice device) {
                if (listener != null) {
                    listener.onAttachDev(device);
                }
            }

            // called by taking out usb device
            // do close camera
            @Override
            public void onDettach(UsbDevice device) {
                if (listener != null) {
                    listener.onDettachDev(device);
                }
            }
            // called by connect to usb camera
// do open camera,start previewing
            @Override
            public void onConnect(final UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
                Log.d("USB_CONNECT", "onConnect: Device connected");
                Log.d("USB_CONNECT", "onConnect: UsbDevice - " + device);
                Log.d("USB_CONNECT", "onConnect: UsbControlBlock - " + ctrlBlock);

                mCtrlBlock = ctrlBlock;
                Log.d("USB_CONNECT", "onConnect: Opening camera with ctrlBlock");

                openCamera(ctrlBlock);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // wait for camera created
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Log.e("USB_CONNECT", "onConnect: Interrupted while waiting for camera creation", e);
                        }

                        Log.d("USB_CONNECT", "onConnect: Starting preview with mCamView - " + mCamView);
                        if (mCamView == null) {
                            Log.e("USB_CONNECT", "onConnect: mCamView is null, cannot start preview");
                            return;
                        }

                        // start previewing
                        startPreview(mCamView);

                        Log.d("USB_CONNECT", "onConnect: Preview started");
                    }
                }).start();

                if(listener != null) {
                    Log.d("USB_CONNECT", "onConnect: Notifying listener about connection");
                    listener.onConnectDev(device, true);
                } else {
                    Log.d("USB_CONNECT", "onConnect: Listener is null, cannot notify about connection");
                }
            }
//            // called by connect to usb camera
//            // do open camera,start previewing
//            @Override
//            public void onConnect(final UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
//                mCtrlBlock = ctrlBlock;
//                openCamera(ctrlBlock);
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        // wait for camera created
//                        try {
//                            Thread.sleep(500);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                        // start previewing
//                        startPreview(mCamView);
//                    }
//                }).start();
//                if(listener != null) {
//                    listener.onConnectDev(device,true);
//                }
//            }

            // called by disconnect to usb camera
            // do nothing
            @Override
            public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                if (listener != null) {
                    listener.onDisConnectDev(device);
                }
            }

            @Override
            public void onCancel(UsbDevice device) {
            }
        });

        createUVCCamera();
    }

    public void createUVCCamera() {
        Log.d("createUVCCamera", "Entering createUVCCamera method");

        if (mCamView == null) {
            throw new NullPointerException("CameraViewInterface cannot be null!");
        }

        // release resources for initializing camera handler
        if (mCameraHandler != null) {
            Log.d("createUVCCamera", "Releasing existing CameraHandler");
            mCameraHandler.release();
            mCameraHandler = null;
        }

        // initialize camera handler
        Log.d("createUVCCamera", "Initializing new CameraHandler");
        mCamView.setAspectRatio(previewWidth / (float) previewHeight);

        Log.d("createUVCCamera", "Creating CameraHandler with parameters - Activity: " + mActivity
                + ", CameraViewInterface: " + mCamView + ", PreviewWidth: " + previewWidth
                + ", PreviewHeight: " + previewHeight + ", FrameFormat: " + mFrameFormat);

        mCameraHandler = UVCCameraHandler.createHandler(mActivity, mCamView, 2,
                previewWidth, previewHeight, mFrameFormat);

        if (mCameraHandler != null) {
            Log.d("createUVCCamera", "CameraHandler created successfully: " + mCameraHandler);
        } else {
            Log.e("createUVCCamera", "Failed to create CameraHandler");
        }

        Log.d("createUVCCamera", "Exiting createUVCCamera method");
    }


//    public void createUVCCamera() {
//        if (mCamView == null)
//            throw new NullPointerException("CameraViewInterface cannot be null!");
//
//        // release resources for initializing camera handler
//        if (mCameraHandler != null) {
//            mCameraHandler.release();
//            mCameraHandler = null;
//        }
//        // initialize camera handler
//        Log.d("create","CREATE HANDLER");
//        mCamView.setAspectRatio(previewWidth / (float)previewHeight);
//        mCameraHandler = UVCCameraHandler.createHandler(mActivity, mCamView, 2,
//                previewWidth, previewHeight, mFrameFormat);
//        Log.d("HANDLER","Created $",mCameraHandler);
//    }

    public void updateResolution(int width, int height) {
        if (previewWidth == width && previewHeight == height) {
            return;
        }
        this.previewWidth = width;
        this.previewHeight = height;
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        mCamView.setAspectRatio(previewWidth / (float)previewHeight);
        mCameraHandler = UVCCameraHandler.createHandler(mActivity,mCamView, 2,
                previewWidth, previewHeight, mFrameFormat);
        openCamera(mCtrlBlock);
        new Thread(new Runnable() {
            @Override
            public void run() {
                // wait for camera created
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // start previewing
                startPreview(mCamView);
            }
        }).start();
    }

    public void registerUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.register();
        }
    }

    public void unregisterUSB() {
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
        }
    }

    public boolean checkSupportFlag(final int flag) {
        return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
    }

    public int getModelValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
    }

    public int setModelValue(final int flag, final int value) {
        return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
    }

    public int resetModelValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
    }

    public void requestPermission(int index) {
        Log.d("USB_DEBUG", "requestPermission called with index: " + index);

        List<UsbDevice> devList = getUsbDeviceList();
        Log.d("USB_DEBUG", "Device list retrieved: " + (devList == null ? "null" : devList.toString()));

        if (devList == null || devList.size() == 0) {
            Log.d("USB_DEBUG", "No USB devices found or device list is null");
            return;
        }

        int count = devList.size();
        Log.d("USB_DEBUG", "Number of USB devices found: " + count);

        if (index >= count) {
            Log.e("USB_DEBUG", "Illegal argument: index should be less than devList.size()");
            throw new IllegalArgumentException("index illegal, should be < devList.size()");
        }

        if (mUSBMonitor != null) {
            Log.d("USB_DEBUG", "USB Monitor is not null, requesting permission for device: " + devList.get(index));
            mUSBMonitor.requestPermission(devList.get(index));
        } else {
            Log.d("USB_DEBUG", "USB Monitor is null, cannot request permission");
        }
    }

    public int getUsbDeviceCount() {
        List<UsbDevice> devList = getUsbDeviceList();
        if (devList == null || devList.size() == 0) {
            return 0;
        }
        return devList.size();
    }

    public List<UsbDevice> getUsbDeviceList() {
        List<DeviceFilter> deviceFilters = DeviceFilter
                .getDeviceFilters(mActivity.getApplicationContext(), R.xml.device_filter);
        if (mUSBMonitor == null || deviceFilters == null)
//            throw new NullPointerException("mUSBMonitor ="+mUSBMonitor+"deviceFilters=;"+deviceFilters);
            return null;
        // matching all of filter devices
        return mUSBMonitor.getDeviceList(deviceFilters);
    }

    public void capturePicture(String savePath,AbstractUVCCameraHandler.OnCaptureListener listener) {
        if (mCameraHandler != null && mCameraHandler.isOpened()) {

            File file = new File(savePath);
            if(! Objects.requireNonNull(file.getParentFile()).exists()) {
                file.getParentFile().mkdirs();
            }
            mCameraHandler.captureStill(savePath, (AbstractUVCCameraHandler.OnCaptureListener) listener);
        }
    }

    public void startPusher(AbstractUVCCameraHandler.OnEncodeResultListener listener) {
        if (mCameraHandler != null && !isPushing()) {
            mCameraHandler.startRecording((RecordParams) null, (AbstractUVCCameraHandler.OnEncodeResultListener) listener);
        }
    }

    public void startPusher(RecordParams params, AbstractUVCCameraHandler.OnEncodeResultListener listener) {
        if (mCameraHandler != null && !isPushing()) {
            if(params.isSupportOverlay()) {
                TxtOverlay.install(mActivity.getApplicationContext());
            }
            mCameraHandler.startRecording(params, (AbstractUVCCameraHandler.OnEncodeResultListener) listener);
        }
    }

    public void stopPusher() {
        if (mCameraHandler != null && isPushing()) {
            mCameraHandler.stopRecording();
        }
    }

    public boolean isPushing() {
        if (mCameraHandler != null) {
            return mCameraHandler.isRecording();
        }
        return false;
    }

    public boolean isCameraOpened() {
        if (mCameraHandler != null) {
            return mCameraHandler.isOpened();
        }
        return false;
    }

    public void release() {
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
    }

    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    public void setOnPreviewFrameListener(AbstractUVCCameraHandler.OnPreViewResultListener listener) {
        if(mCameraHandler != null) {
            mCameraHandler.setOnPreViewResultListener((AbstractUVCCameraHandler.OnPreViewResultListener) listener);
        }
    }

    private void openCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        if (mCameraHandler != null) {
            mCameraHandler.open(ctrlBlock);
        }
    }

    public void startPreview(CameraViewInterface cameraView) {
        Log.d("START_PREVIEW", "START PREVIEW");
        if (cameraView == null) {
            Log.e("START_PREVIEW", "START PREVIEW: cameraView is null!");
            return;
        }

        Log.d("START_PREVIEW", "START PREVIEW: cameraView class - " + cameraView.getClass().getName());

        SurfaceTexture st = cameraView.getSurfaceTexture();
        if (st == null) {
            Log.e("START_PREVIEW", "START PREVIEW: SurfaceTexture is null!");
        } else {
            Log.d("START_PREVIEW", "START PREVIEW: SurfaceTexture is not null");
        }

        if (mCameraHandler != null) {
            Log.d("START_PREVIEW", "START PREVIEW HANDLER NOT NULL");
            mCameraHandler.startPreview(st);
        } else {
            Log.e("START_PREVIEW", "START PREVIEW HANDLER IS NULL");
        }
    }


    public void stopPreview() {
        Log.d("STOP_PREVIEW","STOP PREVIEW");
        if (mCameraHandler != null) {
            mCameraHandler.stopPreview();
        }
    }

    public void startCameraFoucs() {
        if (mCameraHandler != null) {
            mCameraHandler.startCameraFoucs();
        }
    }

    public List<Size> getSupportedPreviewSizes() {
        if (mCameraHandler == null)
            return null;
        return mCameraHandler.getSupportedPreviewSizes();
    }

    public void setDefaultPreviewSize(int defaultWidth,int defaultHeight) {
        if(mUSBMonitor != null) {
            throw new IllegalStateException("setDefaultPreviewSize should be call before initMonitor");
        }
        this.previewWidth = defaultWidth;
        this.previewHeight = defaultHeight;
    }

    public void setDefaultFrameFormat(int format) {
        if(mUSBMonitor != null) {
            throw new IllegalStateException("setDefaultFrameFormat should be call before initMonitor");
        }
        this.mFrameFormat = format;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }
}
