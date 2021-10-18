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
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.arengine.demos.R;
import com.huawei.arengine.demos.common.DisplayRotationManager;
import com.huawei.arengine.demos.common.LogUtil;
import com.huawei.arengine.demos.common.PermissionManager;
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

//import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.glutil.EglManager;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * This demo shows the capabilities of HUAWEI AR Engine to recognize faces, including facial
 * features and facial expressions. In addition, this demo shows how an app can open the camera
 * to display preview.Currently, only apps of the ARface type can open the camera. If you want
 * to the app to open the camera, set isOpenCameraOutside = true in this file.
 *
 * @author HW
 * @since 2020-03-18
 */
public class FaceActivity extends Activity {
    private static final String TAG = FaceActivity.class.getSimpleName();

    private static final String BINARY_GRAPH_NAME = "holistic_iris.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";

    // Flips the camera-preview frames vertically before sending them into FrameProcessor to be
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    private static final String OUTPUT_LANDMARKS_STREAM_NAME_FACE_MESH = "face_landmarks";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME_POS_ROI = "pose_roi";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME_RIGHT_HAND = "right_hand_landmarks";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME_LEFT_HAND = "left_hand_landmarks";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME_POSE = "pose_landmarks";
    private static final String FOCAL_LENGTH_STREAM_NAME = "focal_length_pixel";

    private ARSession mArSession;

    private GLSurfaceView glSurfaceView;

    private FaceRenderManager mFaceRenderManager;

    private DisplayRotationManager mDisplayRotationManager;

    private boolean isOpenCameraOutside = false;

    private CameraHelper mCamera;

    private Surface mPreViewSurface;

    private Surface mVgaSurface;

    private Surface mMetaDataSurface;

    private Surface mDepthSurface;

    private ARConfigBase mArConfig;

    private TextView mTextView;

    private String message = null;

    private boolean isRemindInstall = false;

    /**
     * The initial texture ID is -1.
     */
    private int textureId = -1;
    
    private static final String ServerIp = "192.168.0.104";
    private static final int ServerPort = 8001;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mTextView = findViewById(R.id.faceTextView);
        glSurfaceView = findViewById(R.id.faceSurfaceview);
        mDisplayRotationManager = new DisplayRotationManager(this);

        glSurfaceView.setPreserveEGLContextOnPause(true);

        // Set the OpenGLES version.
        glSurfaceView.setEGLContextClientVersion(2);

        // Set the EGL configuration chooser, including for the
        // number of bits of the color buffer and the number of depth bits.
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        mFaceRenderManager = new FaceRenderManager(this, this);
        mFaceRenderManager.setDisplayRotationManage(mDisplayRotationManager);
        mFaceRenderManager.setTextView(mTextView);
        glSurfaceView.setRenderer(mFaceRenderManager);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);

        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor.getVideoSurfaceOutput().setFlipY(FLIP_FRAMES_VERTICALLY);

        PermissionHelper.checkAndRequestCameraPermissions(this);
/*        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME_FACE_MESH,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    //Log.d(TAG, "Received face mesh landmarks packet.");
                    try {
                        NormalizedLandmarkList multiFaceLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        JSONObject landmarks_json_object = getLandmarksJsonObject(multiFaceLandmarks, "face");
                        //JSONObject face_landmarks_json_object = getFaceLandmarkJsonObject(landmarks_json_object);
                        //Log.d("face", String.valueOf(landmarks_json_object));
                        send_UDP(landmarks_json_object.toString().getBytes());
                        //json_message = face_landmarks_json_object.toString();
                    } catch (InvalidProtocolBufferException | JSONException e) {
                        e.printStackTrace();
                    }
                });*/

        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME_RIGHT_HAND,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    //Log.v(TAG, "Received right hand landmarks packet.");
                    try {
                        NormalizedLandmarkList RightHandLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        //+ getMultiFaceLandmarksDebugString(multiFaceLandmarks));
                        //String right_hand_landmarks = getHolisticLandmarksDebugString(RightHandLandmarks, "right_hand");
                        //publishMessage(right_hand_landmarks);
                        JSONObject landmarks_json_object = getLandmarksJsonObject(RightHandLandmarks, "right_hand");
                        send_UDP(landmarks_json_object.toString().getBytes());
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                });
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME_LEFT_HAND,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    //Log.v(TAG, "Received left hand landmarks packet.");
                    try {
                        NormalizedLandmarkList LeftHandLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        //+ getMultiFaceLandmarksDebugString(multiFaceLandmarks));
                        //String left_hand_landmarks = getHolisticLandmarksDebugString(LeftHandLandmarks, "left_hand");
                        //publishMessage(left_hand_landmarks);
                        JSONObject landmarks_json_object = getLandmarksJsonObject(LeftHandLandmarks, "left_hand");
                        send_UDP(landmarks_json_object.toString().getBytes());
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                });
        processor.addPacketCallback(
                OUTPUT_LANDMARKS_STREAM_NAME_POSE,
                (packet) -> {
                    byte[] landmarksRaw = PacketGetter.getProtoBytes(packet);
                    //Log.v(TAG, "Received pose landmarks packet.");
                    try {
                        NormalizedLandmarkList PoseLandmarks = NormalizedLandmarkList.parseFrom(landmarksRaw);
                        //+ getMultiFaceLandmarksDebugString(multiFaceLandmarks));
                        //String pose_landmarks = getHolisticLandmarksDebugString(PoseLandmarks, "pose");
                        //publishMessage(pose_landmarks);
                        JSONObject landmarks_json_object = getLandmarksJsonObject(PoseLandmarks, "pose");
                        Log.d(TAG, String.valueOf(landmarks_json_object));
                        send_UDP(landmarks_json_object.toString().getBytes());
                    } catch (JSONException | IOException e) {
                        e.printStackTrace();
                    }
                });

    }

    @Override
    protected void onResume() {
        LogUtil.debug(TAG, "onResume");
        super.onResume();
        if (!PermissionManager.hasPermission(this)) {
            this.finish();
        }
        mDisplayRotationManager.registerDisplayListener();
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
        mDisplayRotationManager.registerDisplayListener();
        setCamera();
        mFaceRenderManager.setArSession(mArSession);
        mFaceRenderManager.setOpenCameraOutsideFlag(isOpenCameraOutside);
        mFaceRenderManager.setTextureId(textureId);
        glSurfaceView.onResume();
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
            if (!mCamera.openCamera()) {
                String showMessage = "Open camera filed!";
                LogUtil.error(TAG, showMessage);
                Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }
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
            mDisplayRotationManager.unregisterDisplayListener();
            glSurfaceView.onPause();
            mArSession.pause();
            LogUtil.info(TAG, "Session paused!");
        }
        LogUtil.info(TAG, "onPause end.");
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

    private static JSONObject getLandmarksJsonObject(NormalizedLandmarkList landmarks, String location) throws JSONException {
        JSONObject landmarks_json_object = new JSONObject();
        if (location == "face"){
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()){
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
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
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
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
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
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
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

    public void send_UDP(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send", String.valueOf(data));
    }
}