package com.huawei.arengine.demos.common.converter;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

public class BmpProducer extends Thread {
    public static final String TAG="BmpProducer";

    CustomFrameAvailableListner customFrameAvailableListner;

    private boolean running = false;

    long timestamp;
    int destinationTextureId;
    int viewWidth = 720;
    int viewHeight = 1280;
    boolean sendVaild = false;

    public BmpProducer(Context context){
        //start();
        running = true;
    }

    BmpProducer(Bitmap bitmap, CustomFrameAvailableListner customFrameAvailableListner){
        this.customFrameAvailableListner = customFrameAvailableListner;
        start();
    }

    public void setCustomFrameAvailableListner(CustomFrameAvailableListner customFrameAvailableListner){
        this.customFrameAvailableListner = customFrameAvailableListner;
    }

    public void setBitmapData( long ts, int TextureId, int width, int height) {
        Log.d(TAG, "setBitmapData: ");
        timestamp = ts;
        destinationTextureId = TextureId;
        viewWidth = width;
        viewHeight = height;
        sendVaild = true;

        if(customFrameAvailableListner == null)
            return;
        customFrameAvailableListner.onFrame(timestamp, destinationTextureId, viewWidth, viewHeight);
    }

    @Override
    public void run() {
        super.run();
        while ((running)){
            if(customFrameAvailableListner == null || sendVaild == false)
                continue;
            Log.d(TAG,"Writing frame");
            customFrameAvailableListner.onFrame(timestamp, destinationTextureId, viewWidth, viewHeight);
            sendVaild = false;
            /*OTMainActivity.imageView.post(new Runnable() {
                @Override
                public void run() {
                    OTMainActivity.imageView.setImageBitmap(bg);
                }
            });*/
            try{
                Thread.sleep(5);
            }catch (Exception e){
                Log.d(TAG,e.toString());
            }
        }
    }

    public void close(){
        running = false;
    }
}
