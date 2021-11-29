package com.huawei.arengine.demos.java.face;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.CameraXPreviewHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.glutil.EglManager;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.protobuf.InvalidProtocolBufferException;
import com.huawei.arengine.demos.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MediapipeFlow extends AppCompatActivity {
    private static final String TAG = "Mediapipe";
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    private ViewGroup mviewGroup;

    private Activity mActivity;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    protected FrameProcessor processor;
    // Handles camera access via the {@link CameraX} Jetpack support library.
    protected CameraXPreviewHelper cameraHelper;

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;

    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;

    //private BitmapConverter converter;
    //BmpProducer bitmapProducer;
    //private EglSurfaceView surfaceView;
    //private CameraGLSurfaceRenderer glSurfaceRenderer = null;

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
     * The constructor initializes context and activity.
     * This method will be called when {@link Activity#onCreate}.
     *
     * @param mcontext  Context
     */
    public MediapipeFlow(Context mcontext, Activity activity, ViewGroup viewGroup){
        Log.d(TAG, "MediapipeFlow start");
        try {
            applicationInfo =
                    mcontext.getPackageManager().getApplicationInfo(mcontext.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }
        //mActivity = activity;
        mviewGroup = viewGroup;
        previewDisplayView = new SurfaceView(mcontext);;
        setupPreviewDisplayView();
        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(mcontext);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        mcontext,
                        eglManager.getNativeContext(),
                        applicationInfo.metaData.getString("binaryGraphName"),
                        applicationInfo.metaData.getString("inputVideoStreamName"),
                        applicationInfo.metaData.getString("outputVideoStreamName")
                );

        processor
                .getVideoSurfaceOutput()
                .setFlipY(
                        applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));

        PermissionHelper.checkAndRequestCameraPermissions((Activity) mcontext);

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
    }

    private void setupPreviewDisplayView() {
        Log.d(TAG, "setupPreviewDisplayView");
        previewDisplayView.setVisibility(View.GONE);
        //ViewGroup viewGroup = findViewById(R.id.preview_display_layout);
        mviewGroup.addView(previewDisplayView);

        previewDisplayView
                .getHolder()
                .addCallback(
                        new SurfaceHolder.Callback() {
                            @Override
                            public void surfaceCreated(SurfaceHolder holder) {
                                Log.d(TAG, "surfaceCreated");
                                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                            }

                            @Override
                            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                Log.d(TAG, "surfaceChanged");
                                onPreviewDisplaySurfaceChanged(holder, format, width, height);
                            }

                            @Override
                            public void surfaceDestroyed(SurfaceHolder holder) {
                                processor.getVideoSurfaceOutput().setSurface(null);
                            }
                        });
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "onPreviewDisplaySurfaceChanged");
        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                720,
                1280);
    }

    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    /**
     * This method is called when {@link Activity#onResume}.
     */
    public void mpOnResume(){
        Log.d(TAG, "mpOnResume");
        converter = new ExternalTextureConverter(eglManager.getContext());
        converter.setFlipY(
                applicationInfo.metaData.getBoolean("flipFramesVertically", FLIP_FRAMES_VERTICALLY));
        converter.setConsumer(processor);
        Log.d(TAG, "mpOnResume over");
        //startCamera();
    }

    /**
     * This method is called when {@link Activity#onPause}.
     */
    public void mpOnPause(){
        converter.close();
    }

    protected Size cameraTargetResolution() {
        return null; // No preference and let the camera (helper) decide.
    }

    public void startCamera() {
        cameraHelper = new CameraXPreviewHelper();
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    onCameraStarted(surfaceTexture);
                });
/*        CameraHelper.CameraFacing cameraFacing =
                applicationInfo.metaData.getBoolean("cameraFacingFront", false)
                        ? CameraHelper.CameraFacing.BACK
                        : CameraHelper.CameraFacing.FRONT;
        cameraHelper.startCamera(
                mActivity, cameraFacing, *//*surfaceTexture=*//* null, cameraTargetResolution());*/
    }

    public void onCameraStarted(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onCameraStarted");
        previewFrameTexture = surfaceTexture;
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView.setVisibility(View.VISIBLE);
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
}
