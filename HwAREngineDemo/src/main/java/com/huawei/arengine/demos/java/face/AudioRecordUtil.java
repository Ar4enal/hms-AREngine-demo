package com.huawei.arengine.demos.java.face;

import android.content.DialogInterface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioRecordUtil {
    //设置音频采样率，44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    private final int sampleRateInHz = 44100;
    //设置音频的录制的声道CHANNEL_IN_STEREO为双声道，CHANNEL_CONFIGURATION_MONO为单声道
    private final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    //音频数据格式:PCM 16位每个样本。保证设备支持。PCM 8位每个样本。不一定能得到设备支持。
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

    private static String ServerIp = "192.168.0.1";
    private static final int ServerPort = 8001;

    private AudioTrack mTrack;
    //录制状态
    private boolean recorderState = true;
    private byte[] buffer;
    private AudioRecord audioRecord;
    private static AudioRecordUtil audioRecordUtil = new AudioRecordUtil();

    public static AudioRecordUtil getInstance() {
        return audioRecordUtil;
    }

    private AudioRecordUtil() {
        init();
    }

    private void init() {
        int recordMinBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        //指定 AudioRecord 缓冲区大小
        buffer = new byte[recordMinBufferSize];
        //根据录音参数构造AudioRecord实体对象
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig,
                audioFormat, recordMinBufferSize);
/*        mTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateInHz)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(recordMinBufferSize)
                .build();*/
    }

    /**
     * 开始录制
     */
    public void start() {
        if (audioRecord.getState() == AudioRecord.RECORDSTATE_STOPPED) {
            recorderState = true;
            audioRecord.startRecording();

            //mTrack.play();
            new RecordThread().start();
        }
    }

    /**
     * 停止录制
     */
    public void stop() {
        recorderState = false;
        if (audioRecord.getState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
            //mTrack.stop();
        }
        audioRecord.release();
        //mTrack.release();
    }

    public void setServerIp(String ip){
        ServerIp = ip;
    }

    public void send_UDP(byte[] data) throws IOException, JSONException {
        //JSONObject audioData = new JSONObject();
        //audioData.put("type", "audio");
        //audioData.put("content", data);
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(ServerIp), ServerPort);
        DatagramSocket socket = new DatagramSocket();
        socket.send(packet);
        Log.d("send--audio", String.valueOf(data));
        //Log.d("udp--audio", ServerIp);
    }

    private class RecordThread extends Thread {

        @Override
        public void run() {
            while (recorderState) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    //获取到的pcm数据就是buffer了
                    try {
                        send_UDP(buffer);
                        //mTrack.write(buffer, 0, buffer.length);
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                    //Log.d("buffer.length", String.valueOf(buffer.length));
                    //Log.d("buffer", String.valueOf(buffer));
                }
            }
        }
    }
}
