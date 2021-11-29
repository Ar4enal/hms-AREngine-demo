package com.huawei.arengine.demos.common.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.widget.TextView;

import com.huawei.arengine.demos.common.ArDemoRuntimeException;
import com.huawei.arengine.demos.common.Constants;
import com.huawei.arengine.demos.common.DisplayRotationManager;
import com.huawei.arengine.demos.common.LogUtil;
import com.huawei.arengine.demos.common.TextDisplay;
import com.huawei.arengine.demos.common.TextureDisplay;
import com.huawei.arengine.demos.common.converter.BmpProducer;
import com.huawei.arengine.demos.common.egl.EglSurfaceView;
import com.huawei.arengine.demos.java.face.FaceActivity;
import com.huawei.arengine.demos.java.face.rendering.FaceGeometryDisplay;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFace;
import com.huawei.hiar.ARFaceBlendShapes;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CameraGLSurfaceRenderer implements EglSurfaceView.Renderer {
    //zicamera 渲染器单目 无畸变

    private static final String TAG = "GLSurfaceRenderer";
    public FaceActivity mainActivity;
    private int viewWidth = 0;
    private int viewHeight = 0;

    private static final float UPDATE_INTERVAL = 0.5f;

    private int frames = 0;

    private long lastInterval;

    private ARSession mArSession;

    private float fps;

    private Context mContext;

    private Activity mActivity;

    private TextView mTextView;

    private boolean isOpenCameraOutside = true;

    private static String ServerIp = "192.168.0.1";
    private static final int ServerPort = 8002;

    /**
     * Initialize the texture ID.
     */
    private int mTextureId = -1;

    private TextureDisplay mTextureDisplay = new TextureDisplay();

    private FaceGeometryDisplay mFaceGeometryDisplay = new FaceGeometryDisplay();

    private TextDisplay mTextDisplay = new TextDisplay();

    private DisplayRotationManager mDisplayRotationManager;

    private float[] projmtx = new float[16];

    private int mTexture;
    private String vertShader;
    private String fragShader_Pre;
    private int programHandle;
    private int mPositionHandle;
    private int mTextureCoordHandle;
    FloatBuffer verticesBuffer, textureVerticesPreviewBuffer;
    // number of coordinates per vertex in this array
    private static final int BYTES_PER_FLOAT = 4;

    private static final int POSITION_COORDS_PER_VERTEX = 2; // X, Y
    private static final int TEXTURE_COORDS_PER_VERTEX = 2;
    private static final int CPV = POSITION_COORDS_PER_VERTEX + TEXTURE_COORDS_PER_VERTEX;
    private static final int VERTEX_STRIDE_BYTES = CPV * BYTES_PER_FLOAT;
    private final short drawOrder[] = {0, 1, 2, 0, 2, 3}; // order to draw vertices
    private int mvpMatrixHandle;
    private final float[] displayMatrix = new float[16];
    private static Object prevARFaceBlendShapes;

    BmpProducer bitmapProducer;
    public CameraGLSurfaceRenderer(Context context, FaceActivity mainActivity) {
        mContext = context;
        this.mainActivity = mainActivity;
    }

    public void setBitmapProducer(BmpProducer mapProducer) {
        bitmapProducer = mapProducer;
    }

    private final float squareVertices[] = { // in counterclockwise order:
            -1.0f, 1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f, 1.0f
    };
    private final float textureVerticesPreview[] = { // in counterclockwise order:
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f
    };

    private final float[] VERTEX_DATA = new float[]{
            -1, -1, 1, 1.0f,
            1, -1, 1, 0.0f,
            -1, 1, 0, 1,
            1, 1, 0, 0
    };

    private final float[] VERTEX_DATA_FBO = new float[]{
            -1, -1, 0, 1.0f,
            1, -1, 0, 0.0f,
            -1, 1, 1, 1,
            1, 1, 1, 0
    };
    private FloatBuffer vertexBuffer = createBuffer(VERTEX_DATA);

    private FloatBuffer vertexBufferFBO = createBuffer(VERTEX_DATA_FBO);

    private int mOffscreenTexture;

    int destinationTextureId;

    // 帧缓冲对象 - 颜色、深度、模板附着点，纹理对象可以连接到帧缓冲区对象的颜色附着点
    private int mFrameBuffer;

    /**
     * Set an ARSession. The input ARSession will be called in onDrawFrame
     * to obtain the latest data. This method is called when {@link Activity#onResume}.
     *
     * @param arSession ARSession.
     */
    public void setArSession(ARSession arSession) {
        if (arSession == null) {
            LogUtil.error(TAG, "Set session error, arSession is null!");
            return;
        }
        mArSession = arSession;
    }

    /**
     * Set the external camera open flag. If the value is true, the app opens the camera
     * by itself and creates a texture ID during background rendering. Otherwise, the camera
     * is opened by AR Engine. This method is called when {@link Activity#onResume}.
     *
     * @param isOpenCameraOutsideFlag Flag indicating the mode of opening the camera.
     */
    public void setOpenCameraOutsideFlag(boolean isOpenCameraOutsideFlag) {
        isOpenCameraOutside = isOpenCameraOutsideFlag;
    }

    /**
     * Set the texture ID for background rendering. This method will be called when {@link Activity#onResume}.
     *
     * @param textureId Texture ID.
     */
    public void setTextureId(int textureId) {
        mTextureId = textureId;
    }

    /**
     * Set the displayRotationManage object, which will be used in onSurfaceChanged
     * and onDrawFrame. This method is called when {@link Activity#onResume}.
     *
     * @param displayRotationManager DisplayRotationManage.
     */
    public void setDisplayRotationManage(DisplayRotationManager displayRotationManager) {
        if (displayRotationManager == null) {
            LogUtil.error(TAG, "Set display rotation manage error, displayRotationManage is null!");
            return;
        }
        mDisplayRotationManager = displayRotationManager;
    }

    /**
     * Set TextView. This object will be used in the UI thread. This method is called when {@link Activity#onCreate}.
     *
     * @param textView TextView.
     */
    public void setTextView(TextView textView) {
        if (textView == null) {
            LogUtil.error(TAG, "Set text view error, textView is null!");
            return;
        }
        mTextView = textView;
    }

    public void setServerIp(String ip){
        ServerIp = ip;
    }


    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated: ");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        //initTexture();

        if (isOpenCameraOutside) {
            mTextureDisplay.init(mTextureId);
        } else {
            mTextureDisplay.init();
        }
        LogUtil.info(TAG, "On surface created textureId= " + mTextureId);

        mFaceGeometryDisplay.init(mContext);

/*        mTextDisplay.setListener(new TextDisplay.OnTextInfoChangeListener() {
            @Override
            public void textInfoChanged(String text, float positionX, float positionY) {
                showTextViewOnUiThread(text, positionX, positionY);
            }
        });*/
    }

    /**
     * Create a thread for text display on the UI. The method for displaying texts is called back in TextureDisplay.
     *
     * @param text      Information displayed on the screen.
     * @param positionX X coordinate of a point.
     * @param positionY Y coordinate of a point.
     */
    private void showTextViewOnUiThread(final String text, final float positionX, final float positionY) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setTextColor(Color.WHITE);

                // Set the size of the text displayed on the screen.
                mTextView.setTextSize(10f);
                if (text != null) {
                    mTextView.setText(text);
                    mTextView.setPadding((int) positionX, (int) positionY, 0, 0);
                } else {
                    mTextView.setText("");
                }
            }
        });
    }


    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.d(TAG, "onSurfaceChanged(), <= 0");
            return;
        }
        Log.d(TAG, "onSurfaceChanged() " + width + " " + height);

        mTextureDisplay.onSurfaceChanged(width, height);
        mainActivity.displayRotationHelper.onSurfaceChanged(width, height);

        //mDisplayRotationManager.updateViewportRotation(width, height);
        GLES20.glViewport(0, 0, width, height);
        viewWidth = width;
        viewHeight = height;

        mTexture = createVideoTexture();

        createFBO();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (viewWidth == 0 || viewHeight == 0) {
            return;
        }
        if (mArSession == null) {
            return;
        }
        if (mArSession != null) {
            mainActivity.displayRotationHelper.updateSessionIfNeeded(mArSession);
            //mArSession.setCameraTextureName(mTexture);
        }

        try {
            //mArSession.setCameraTextureName(mTexture);
            mArSession.setCameraTextureName(mTextureDisplay.getExternalTextureId());
            ARFrame frame = mArSession.update();
            mTextureDisplay.onDrawFrame(frame);
            float fpsResult = doFpsCalculate();
            Collection<ARFace> faces = mArSession.getAllTrackables(ARFace.class);
            if (faces.size() == 0) {
                //mTextDisplay.onDrawFrame(null);
                return;
            }
            LogUtil.debug(TAG, "Face number: " + faces.size());
            ARCamera camera = frame.getCamera();
            for (ARFace face : faces) {
                if (face.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                    mFaceGeometryDisplay.onDrawFrame(camera, face);
                    StringBuilder sb = new StringBuilder();
                    updateMessageData(sb, fpsResult, face);
                    //mTextDisplay.onDrawFrame(sb);
                    ARFaceBlendShapes blendShapes = face.getFaceBlendShapes();
                    JSONObject faceBlendShapes = new JSONObject(blendShapes.getBlendShapeDataMapKeyString());
                    faceBlendShapes.put("qx", face.getPose().qx());
                    faceBlendShapes.put("qy", face.getPose().qy());
                    faceBlendShapes.put("qz", face.getPose().qz());
                    faceBlendShapes.put("qw", face.getPose().qw());
                    faceBlendShapes.put("face_detected", true);
                    send_UDP(faceBlendShapes);
                }
                else if (face.getTrackingState() == ARTrackable.TrackingState.PAUSED){
                    JSONObject faceBlendShapes = new JSONObject();
                    faceBlendShapes.put("face_detected", false);
                    send_UDP(faceBlendShapes);
                }

            }
            // Draw background.
            //drawFrameBuffer();
            //draw(mOffscreenTexture);
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }catch (ArDemoRuntimeException e) {
            LogUtil.error(TAG, "Exception on the ArDemoRuntimeException!");
        } catch (Throwable t) {
            // This prevents the app from crashing due to unhandled exceptions.
            LogUtil.error(TAG, "Exception on the OpenGL thread");
        }

    }

    private void updateMessageData(StringBuilder sb, float fpsResult, ARFace face) {
        sb.append("FPS= ").append(fpsResult).append(System.lineSeparator());
        sb.append("UDP Server Ip: ").append(ServerIp);
    }

    private float doFpsCalculate() {
        ++frames;
        long timeNow = System.currentTimeMillis();

        // Convert millisecond to second.
        if (((timeNow - lastInterval) / 1000.0f) > UPDATE_INTERVAL) {
            fps = frames / ((timeNow - lastInterval) / 1000.0f);
            frames = 0;
            lastInterval = timeNow;
        }
        return fps;
    }

    public void send_UDP(JSONObject data) throws IOException{
        DatagramPacket packet = new DatagramPacket(data.toString().getBytes(), data.toString().getBytes().length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send--face", String.valueOf(data));
        //Log.d("udp--face", ServerIp);
    }

    private void initTexture() {
        verticesBuffer = IVCGLLib.glToFloatBuffer(squareVertices);
        textureVerticesPreviewBuffer = IVCGLLib.glToFloatBuffer(textureVerticesPreview);

        vertShader = IVCGLLib.loadFromAssetsFile("IVC_VShader_Preview.sh", mainActivity.getResources());
        fragShader_Pre = IVCGLLib.loadFromAssetsFile("IVC_FShader_Camera.sh", mainActivity.getResources());
        programHandle = IVCGLLib.glCreateProgram(vertShader, fragShader_Pre);
        // IVCGLLib.glCheckGlError("glCreateProgram");
        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix");
        // IVCGLLib.glCheckGlError("glGetUniformLocation");
        mPositionHandle = GLES20.glGetAttribLocation(programHandle, "position");
        mTextureCoordHandle = GLES20.glGetAttribLocation(programHandle, "inputTextureCoordinate");
    }


    private void drawFrameBuffer() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);


        //使用程序
        GLES20.glUseProgram(programHandle);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);

        Matrix.setIdentityM(displayMatrix, 0);

        // GLES20.gl
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexture);//绑定渲染纹理
        //GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureDisplay.getExternalTextureId());//绑定渲染纹理
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "sampler2d1"), 0);
        //
        vertexBufferFBO.position(0);
        GLES20.glVertexAttribPointer(//设置顶点位置值
                mPositionHandle,
                POSITION_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBufferFBO);

        // Load texture data.
        vertexBufferFBO.position(POSITION_COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(
                mTextureCoordHandle,
                TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBufferFBO);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "stereoARMode"), 2);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programHandle, "eyeA"), 0.0f);


        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, displayMatrix, 0);
        GLES20.glViewport(0, 0, Constants.mediapipeWidth, Constants.mediapipeHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_DATA.length / CPV);


        //GLES20.glDeleteTextures(1, new int[]{destinationTextureId}, 0);
        destinationTextureId = createRgbaTexture(Constants.mediapipeWidth, Constants.mediapipeHeight);
        //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, destinationTextureId);
        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, Constants.mediapipeWidth, Constants.mediapipeHeight);
        //destinationTextureId = ShaderUtil.createRgbaTexture(bitmap);
        bitmapProducer.setBitmapData(0, destinationTextureId, Constants.mediapipeWidth, Constants.mediapipeHeight);
        //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        Log.d(TAG, "destinationTextureId: " + destinationTextureId );


        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
    }


    private void draw(int texture) {
        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        GLES20.glUseProgram(programHandle);

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);

        Matrix.setIdentityM(displayMatrix, 0);

        // GLES20.gl
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "sampler2d1"), 0);
        //
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(
                mPositionHandle,
                POSITION_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);
        //   Utils.checkGlError();

        // Load texture data.
        vertexBuffer.position(POSITION_COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(
                mTextureCoordHandle,
                TEXTURE_COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                VERTEX_STRIDE_BYTES,
                vertexBuffer);

        GLES20.glUniform1i(GLES20.glGetUniformLocation(programHandle, "stereoARMode"), 2);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programHandle, "eyeA"), 0.0f);


        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, displayMatrix, 0);
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_DATA.length / CPV);

        //VR模式不执行draw操作

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

    }


    public FloatBuffer createBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);

        return buffer;
    }

    private int createVideoTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    /**
     * 创建fbo
     */
    private void createFBO() {
        int[] values = new int[1];

        GLES20.glGenTextures(1, values, 0);
        mOffscreenTexture = values[0];   // expected > 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, viewWidth, viewHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        //ShaderUtil.checkGlError("glTexImage2D");

        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);


        //1. 创建FBO
        GLES20.glGenFramebuffers(1, values, 0);
        mFrameBuffer = values[0];    // expected > 0
        //2. 绑定FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);


        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.i("", "Framebuffer error");
        }

        //7. 解绑纹理和FBO
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public int createRgbaTexture(int width, int height) {
        int[] textureName = new int[]{0};
        GLES20.glGenTextures(1, textureName, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureName[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, (Buffer) null);
        //ShaderUtil.checkGlError("glTexImage2D");
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        //ShaderUtil.checkGlError("texture setup");
        return textureName[0];
    }

}
