/**
 * Copyright 2021. Huawei Technologies Co., Ltd. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.huawei.arengine.demos.java.face;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;
import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.arengine.demos.R;
import com.huawei.arengine.demos.common.Constants;
import com.huawei.arengine.demos.common.DisplayRotationManager;
import com.huawei.arengine.demos.common.LogUtil;
import com.huawei.arengine.demos.common.PermissionManager;
import com.huawei.arengine.demos.common.camera.CameraGLSurfaceRenderer;
import com.huawei.arengine.demos.common.converter.BitmapConverter;
import com.huawei.arengine.demos.common.converter.BmpProducer;
import com.huawei.arengine.demos.common.egl.EglSurfaceView;
import com.huawei.arengine.demos.common.helpers.DisplayRotationHelper;
import com.huawei.arengine.demos.java.face.rendering.FaceRenderManager;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.ARFaceTrackingConfig;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiar.exceptions.ARUnavailableClientSdkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceApkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceNotInstalledException;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This demo shows the capabilities of HUAWEI AR Engine to recognize faces, including facial
 * features and facial expressions. In addition, this demo shows how an app can open the camera
 * to display preview.Currently, only apps of the ARface type can open the camera. If you want
 * to the app to open the camera, set isOpenCameraOutside = true in this file.
 *
 * @author HW
 * @since 2020-03-18
 */
//public class FaceActivity extends Activity implements SignalingClient.Callback{
public class FaceActivity extends AppCompatActivity implements View.OnClickListener{
    private static final String TAG = FaceActivity.class.getSimpleName();

    private ARSession mArSession;

    //private GLSurfaceView glSurfaceView;

    private boolean isOpenCameraOutside = true;

    private CameraHelper mCamera;

    private Surface mPreViewSurface;

    private Surface mVgaSurface;

    private Surface mMetaDataSurface;

    private Surface mDepthSurface;

    private ARConfigBase mArConfig;

    private String message = null;

    private boolean isRemindInstall = false;

    private Button enter_ip;

    private MediapipeFlow mediapipeFlow;

    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    protected FrameProcessor processor;

    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    //private ExternalTextureConverter converter;
    private BitmapConverter converter;
    BmpProducer bitmapProducer;

    private EglSurfaceView eglsurfaceView;
    private CameraGLSurfaceRenderer glSurfaceRenderer = null;
    public DisplayRotationHelper displayRotationHelper;

    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        try {
            System.loadLibrary("opencv_java3");
        } catch (UnsatisfiedLinkError e) {
            // Some example apps (e.g. template matching) require OpenCV 4.
            System.loadLibrary("opencv_java4");
        }
    }


    /**
     * The initial texture ID is -1.
     */
    private int textureId = -1;
/*    PeerConnectionFactory peerConnectionFactory;
    PeerConnection peerConnection;
    MediaStream mediaStream;*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_activity_main);
        bindView();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        try {
            applicationInfo =
                    this.getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }

        //mTextView = findViewById(R.id.faceTextView);
        eglsurfaceView = findViewById(R.id.faceSurfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        //mDisplayRotationManager = new DisplayRotationManager(this);

        //eglsurfaceView.setPreserveEGLContextOnPause(true);

        // Set the OpenGLES version.
        //eglsurfaceView.setEGLContextClientVersion(2);

        // Set the EGL configuration chooser, including for the
        // number of bits of the color buffer and the number of depth bits.
        //eglsurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        /*mFaceRenderManager = new FaceRenderManager(this, this);
        mFaceRenderManager.setDisplayRotationManage(mDisplayRotationManager);
        mFaceRenderManager.setTextView(mTextView);

        eglsurfaceView.setRenderer(mFaceRenderManager);
        eglsurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);*/

        AudioRecordUtil.getInstance().start();

        previewDisplayView = new SurfaceView(this);
        setupPreviewDisplayView();
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        applicationInfo.metaData.getString("binaryGraphName"),
                        applicationInfo.metaData.getString("inputVideoStreamName"),
                        applicationInfo.metaData.getString("outputVideoStreamName")
                );

        processor
                .getVideoSurfaceOutput()
                .setFlipY(
                        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

        PermissionHelper.checkAndRequestCameraPermissions((Activity) this);

        processor.addPacketCallback(
                applicationInfo.metaData.getString("poselandmarks"),
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    Log.d(TAG, "Received pose landmarks packet.");
                    try {
                        LandmarkProto.NormalizedLandmarkList multiFaceLandmarks = LandmarkProto.NormalizedLandmarkList.parseFrom(landmarksRaw);
                        JSONObject landmarks_json_object = getLandmarksJsonObject(multiFaceLandmarks, "pose");
                        Log.d("pose", String.valueOf(landmarks_json_object));
                    } catch (InvalidProtocolBufferException | JSONException e) {
                        e.printStackTrace();
                    }
                });

        glSurfaceRenderer = new CameraGLSurfaceRenderer(this, this);
        //glSurfaceRenderer.setDisplayRotationManage(displayRotationHelper);
        //glSurfaceRenderer.setTextView(mTextView);

        eglsurfaceView.setSharedContext(eglManager.getContext());
        eglsurfaceView.setPreserveEGLContextOnPause(true);
        eglsurfaceView.setEGLContextClientVersion(2);
        eglsurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        eglsurfaceView.setRenderer(glSurfaceRenderer);
        eglsurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        eglsurfaceView.setWillNotDraw(false);//默认情况下，出于性能考虑，会被设置成WILL_NOT_DROW,这样ondraw就不会被执行了，

/*        EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
        // create PeerConnectionFactory
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(this)
                .createInitializationOptions());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory =
                new DefaultVideoEncoderFactory(eglBaseContext, true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory =
                new DefaultVideoDecoderFactory(eglBaseContext);
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        // create VideoCapturer
        VideoCapturer videoCapturer = createCameraCapturer(true);
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        // create VideoTrack
        VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream");
        mediaStream.addTrack(videoTrack);
        mediaStream.addTrack(audioTrack);

        SignalingClient.get().setCallback(this);
        call();*/
    }

    @Override
    protected void onResume() {
        LogUtil.debug(TAG, "onResume");
        super.onResume();
        if (!PermissionManager.hasPermission(this)) {
            this.finish();
        }
        displayRotationHelper.onResume();
        message = null;
        if (mArSession == null) {
            try {
                if (!arEngineAbilityCheck()) {
                    finish();
                    return;
                }
                mArSession = new ARSession(this);
                mArConfig = new ARFaceTrackingConfig(mArSession);

                mArConfig.setPowerMode(ARConfigBase.PowerMode.POWER_SAVING);

                if (isOpenCameraOutside) {
                    mArConfig.setImageInputMode(ARConfigBase.ImageInputMode.EXTERNAL_INPUT_ALL);
                }
                mArSession.configure(mArConfig);
            } catch (Exception capturedException) {
                setMessageWhenError(capturedException);
            }
            if (message != null) {
                stopArSession();
                return;
            }
        }
        try {
            mArSession.resume();
        } catch (ARCameraNotAvailableException e) {
            Toast.makeText(this, "Camera open failed, please restart the app", Toast.LENGTH_LONG).show();
            mArSession = null;
            return;
        }
        //displayRotationHelper.registerDisplayListener();
        displayRotationHelper.onResume();
        setCamera();
        glSurfaceRenderer.setArSession(mArSession);
        glSurfaceRenderer.setOpenCameraOutsideFlag(isOpenCameraOutside);
        glSurfaceRenderer.setTextureId(textureId);
        eglsurfaceView.onResume();

    }

    /**
     * Check whether HUAWEI AR Engine server (com.huawei.arengine.service) is installed on the current device.
     * If not, redirect the user to HUAWEI AppGallery for installation.
     *
     * @return true:AR Engine ready
     */
    private boolean arEngineAbilityCheck() {
        boolean isInstallArEngineApk = AREnginesApk.isAREngineApkReady(this);
        if (!isInstallArEngineApk && isRemindInstall) {
            Toast.makeText(this, "Please agree to install.", Toast.LENGTH_LONG).show();
            finish();
        }
        LogUtil.debug(TAG, "Is Install AR Engine Apk: " + isInstallArEngineApk);
        if (!isInstallArEngineApk) {
            startActivity(new Intent(this, com.huawei.arengine.demos.common.ConnectAppMarketActivity.class));
            isRemindInstall = true;
        }
        return AREnginesApk.isAREngineApkReady(this);
    }

    private void setMessageWhenError(Exception catchException) {
        if (catchException instanceof ARUnavailableServiceNotInstalledException) {
            startActivity(new Intent(this, com.huawei.arengine.demos.common.ConnectAppMarketActivity.class));
        } else if (catchException instanceof ARUnavailableServiceApkTooOldException) {
            message = "Please update HuaweiARService.apk";
        } else if (catchException instanceof ARUnavailableClientSdkTooOldException) {
            message = "Please update this app";
        } else if (catchException instanceof ARUnSupportedConfigurationException) {
            message = "The configuration is not supported by the device!";
        } else {
            message = "exception throw";
        }
    }

    private void stopArSession() {
        LogUtil.info(TAG, "Stop session start.");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (mArSession != null) {
            mArSession.stop();
            mArSession = null;
        }
        LogUtil.info(TAG, "Stop session end.");
    }

    private void setCamera() {
        if (isOpenCameraOutside && mCamera == null) {
            LogUtil.info(TAG, "new Camera");
            DisplayMetrics dm = new DisplayMetrics();
            mCamera = new CameraHelper(this);
            mCamera.setupCamera(dm.widthPixels, dm.heightPixels);
        }

        // Check whether setCamera is called for the first time.
        if (isOpenCameraOutside) {
            if (textureId != -1) {
                mArSession.setCameraTextureName(textureId);
                initSurface();
            } else {
                int[] textureIds = new int[1];
                GLES20.glGenTextures(1, textureIds, 0);
                textureId = textureIds[0];
                mArSession.setCameraTextureName(textureId);
                initSurface();
            }

            SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreViewSurface(mPreViewSurface);
            mCamera.setVgaSurface(mVgaSurface);
            mCamera.setDepthSurface(mDepthSurface);

            //mediapipeFlow.onCameraStarted(new SurfaceTexture(textureId));

            if (!mCamera.openCamera()) {
                String showMessage = "Open camera filed!";
                LogUtil.error(TAG, showMessage);
                Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }

        converter = new BitmapConverter(eglManager.getContext());
        converter.setConsumer(processor);
        startProducer();
        Log.d(TAG, "onResume: ");
    }

    private void initSurface() {
        List<ARConfigBase.SurfaceType> surfaceTypeList = mArConfig.getImageInputSurfaceTypes();
        List<Surface> surfaceList = mArConfig.getImageInputSurfaces();

        LogUtil.info(TAG, "surfaceList size : " + surfaceList.size());
        int size = surfaceTypeList.size();
        for (int i = 0; i < size; i++) {
            ARConfigBase.SurfaceType type = surfaceTypeList.get(i);
            Surface surface = surfaceList.get(i);
            if (ARConfigBase.SurfaceType.PREVIEW.equals(type)) {
                mPreViewSurface = surface;
            } else if (ARConfigBase.SurfaceType.VGA.equals(type)) {
                mVgaSurface = surface;
            } else if (ARConfigBase.SurfaceType.METADATA.equals(type)) {
                mMetaDataSurface = surface;
            } else if (ARConfigBase.SurfaceType.DEPTH.equals(type)) {
                mDepthSurface = surface;
            } else {
                LogUtil.info(TAG, "Unknown type.");
            }
            LogUtil.info(TAG, "list[" + i + "] get surface : " + surface + ", type : " + type);
        }
    }

    @Override
    protected void onPause() {
        LogUtil.info(TAG, "onPause start.");
        super.onPause();
        if (isOpenCameraOutside) {
            if (mCamera != null) {
                mCamera.closeCamera();
                mCamera.stopCameraThread();
                mCamera = null;
            }
        }

        if (mArSession != null) {
            //mDisplayRotationManager.unregisterDisplayListener();
            displayRotationHelper.onPause();
            eglsurfaceView.onPause();
            mArSession.pause();
            LogUtil.info(TAG, "Session paused!");
        }

        // ########## Begin Mediapipe ##########
        converter.close();
        bitmapProducer.close();
        Log.d(TAG, "onPause: ");

        // Hide preview display until we re-open the camera again.
        previewDisplayView.setVisibility(View.GONE);
        // ########## End Mediapipe ##########
        LogUtil.info(TAG, "onPause end.");
        //mediapipeFlow.mpOnPause();
        displayRotationHelper.onPause();
    }

    @Override
    protected void onDestroy() {
        LogUtil.info(TAG, "onDestroy start.");
        super.onDestroy();
        if (mArSession != null) {
            LogUtil.info(TAG, "Session onDestroy!");
            mArSession.stop();
            mArSession = null;
            LogUtil.info(TAG, "Session stop!");
        }
        LogUtil.info(TAG, "onDestroy end.");
    }

    @Override
    public void onWindowFocusChanged(boolean isHasFocus) {
        LogUtil.debug(TAG, "onWindowFocusChanged");
        super.onWindowFocusChanged(isHasFocus);
        if (isHasFocus) {
            getWindow().getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void bindView(){
        enter_ip = (Button) findViewById(R.id.enter_ip);
        enter_ip.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.enter_ip){
            AlertDialog.Builder dialog = new AlertDialog.Builder(FaceActivity.this);
            dialog.setTitle("Please enter UDP server IP address");
            final View view = View.inflate(FaceActivity.this, R.layout.udpserver_ip, null);
            EditText et = view.findViewById(R.id.server_ip);
            dialog.setView(view);
            dialog.setPositiveButton("confirm", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    String ip = et.getText().toString();
                    Toast.makeText(FaceActivity.this, "UDP server ip:" + ip, Toast.LENGTH_SHORT).show();
                    //serveraddress = ip;
                    AudioRecordUtil.getInstance().setServerIp(ip);
                    glSurfaceRenderer.setServerIp(ip);
                    dialogInterface.cancel();
                }
            });

            dialog.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            dialog.show();
        }
    }

    private void startProducer() {
        Log.d(TAG, "startProducer");
        bitmapProducer = new BmpProducer(this);
        glSurfaceRenderer.setBitmapProducer(bitmapProducer);
        previewDisplayView.setVisibility(View.VISIBLE);
    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE);
        ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        viewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                bitmapProducer.setCustomFrameAvailableListner(converter);
                                Log.d(TAG, "surfaceChanged: " + width + " " + height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    private static JSONObject getLandmarksJsonObject(LandmarkProto.NormalizedLandmarkList landmarks, String location) throws JSONException {
        JSONObject landmarks_json_object = new JSONObject();
        if (location == "face"){
            int landmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()){
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));

                /*JSONObject landmarks_json_object_part = new JSONObject();
                landmarks_json_object_part.put("X", landmark.getX());
                landmarks_json_object_part.put("Y", landmark.getY());
                landmarks_json_object_part.put("Z", landmark.getZ());*/
                String tag = "face_landmark[" + landmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++landmarkIndex;
            }
        }
        else if(location == "right_hand"){
            int rlandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                /*JSONObject landmarks_json_object_part = new JSONObject();
                landmarks_json_object_part.put("X", landmark.getX());
                landmarks_json_object_part.put("Y", landmark.getY());
                landmarks_json_object_part.put("Z", landmark.getZ());*/
                String tag = "right_hand_landmark[" + rlandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++rlandmarkIndex;
            }
        }
        else if(location == "left_hand"){
            int llandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                    /*JSONObject landmarks_json_object_part = new JSONObject();
                    landmarks_json_object_part.put("X", landmark.getX());
                    landmarks_json_object_part.put("Y", landmark.getY());
                    landmarks_json_object_part.put("Z", landmark.getZ());*/
                String tag = "left_hand_landmark[" + llandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++llandmarkIndex;
            }
        }
        else if(location == "pose"){
            int plandmarkIndex = 0;
            for (LandmarkProto.NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                List<String> list = new ArrayList<String>();
                list.add(String.format("%.8f", landmark.getX()));
                list.add(String.format("%.8f", landmark.getY()));
                list.add(String.format("%.8f", landmark.getZ()));
                /*JSONObject landmarks_json_object_part = new JSONObject();
                landmarks_json_object_part.put("X", landmark.getX());
                landmarks_json_object_part.put("Y", landmark.getY());
                landmarks_json_object_part.put("Z", landmark.getZ());*/
                String tag = "pose_landmark[" + plandmarkIndex + "]";
                landmarks_json_object.put(tag, list);
                ++plandmarkIndex;
            }
        }
        return landmarks_json_object;
    }

/*    private void call() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, new PeerConnectionAdapter("localconnection") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                SignalingClient.get().sendIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                runOnUiThread(() -> {
                    remoteVideoTrack.addSink(remoteView);
                });
            }
        });

        peerConnection.addStream(mediaStream);
    }

    private VideoCapturer createCameraCapturer(boolean isFront) {
        Camera1Enumerator enumerator = new Camera1Enumerator(false);
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (isFront ? enumerator.isFrontFacing(deviceName) : enumerator.isBackFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }
    @Override
    public void onCreateRoom() {

    }

    @Override
    public void onPeerJoined() {

    }

    @Override
    public void onSelfJoined() {
        peerConnection.createOffer(new SdpAdapter("local offer sdp") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new SdpAdapter("local set local"), sessionDescription);
                SignalingClient.get().sendSessionDescription(sessionDescription);
            }
        }, new MediaConstraints());
    }

    @Override
    public void onPeerLeave(String msg) {

    }

    @Override
    public void onOfferReceived(JSONObject data) {
        runOnUiThread(() -> {
            peerConnection.setRemoteDescription(new SdpAdapter("localSetRemote"),
                    new SessionDescription(SessionDescription.Type.OFFER, data.optString("sdp")));
            peerConnection.createAnswer(new SdpAdapter("localAnswerSdp") {
                @Override
                public void onCreateSuccess(SessionDescription sdp) {
                    super.onCreateSuccess(sdp);
                    peerConnection.setLocalDescription(new SdpAdapter("localSetLocal"), sdp);
                    SignalingClient.get().sendSessionDescription(sdp);
                }
            }, new MediaConstraints());

        });
    }

    @Override
    public void onAnswerReceived(JSONObject data) {
        peerConnection.setRemoteDescription(new SdpAdapter("localSetRemote"),
                new SessionDescription(SessionDescription.Type.ANSWER, data.optString("sdp")));
    }

    @Override
    public void onIceCandidateReceived(JSONObject data) {
        peerConnection.addIceCandidate(new IceCandidate(
                data.optString("id"),
                data.optInt("label"),
                data.optString("candidate")
        ));
    }*/
}