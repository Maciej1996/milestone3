package com.example.customkeyboard;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.MediaPlayer;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.media.AudioManager;
import android.app.Notification;
import android.app.NotificationManager;
import androidx.annotation.NonNull;
import android.content.Context;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static androidx.core.app.ActivityCompat.startActivityForResult;


public class KeyboardServiceIME extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private Keyboard mKeyBoard;
    private KeyboardView mKeyView;

    BluetoothAdapter bluetoothAdapter;
    SendReceive sendReceive;


    static  final  int STATE_CONNECTING=2;
    static  final  int STATE_CONNECTED=3;
    static  final  int STATE_CONNECTION_FAILED=4;
    static  final  int STATE_MESSAGE_RECIVED=5;

    int REQUEST_ENABLE_BLUETOOTH=1;

    private static final String APP_NAME = "BTChat";
    private static final UUID MY_UUID = UUID.fromString("b43ae580-f8f4-4128-a193-45f1afb8044c");

    public void MuteAudio(){
        AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
        amanager.setStreamMute(AudioManager.STREAM_ALARM, true);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, true);
        amanager.setStreamMute(AudioManager.STREAM_RING, true);
        amanager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
    }
    public void unmute(){
        AudioManager amanager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        amanager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);
        amanager.setStreamMute(AudioManager.STREAM_ALARM, false);
        amanager.setStreamMute(AudioManager.STREAM_MUSIC, false);
        amanager.setStreamMute(AudioManager.STREAM_RING, false);
        amanager.setStreamMute(AudioManager.STREAM_SYSTEM, false);
    }

    public View onCreateInputView() {

        mKeyView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard, null);
        mKeyBoard = new Keyboard(this, R.xml.qwerty);
        mKeyView.setKeyboard(mKeyBoard);
        mKeyView.setOnKeyboardActionListener(this);
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) {
            System.err.println("Nie wspiera");
        }else{
            System.out.println("wspiera"+bluetoothAdapter);
        }
        return mKeyView;
    }

    Handler handler= new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch(msg.what){
                case STATE_MESSAGE_RECIVED:

                    byte[] readBuff= (byte[])msg.obj;
                    String tempMsg=new String(readBuff,0,msg.arg1);

                    char E=tempMsg.charAt(0);
                    char S=tempMsg.charAt(1);
                    char V=tempMsg.charAt(2);

                    if( E=='C' &&  S=='C' && V=='C') {
                        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        takePictureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(takePictureIntent);
                    }
                    if( E=='D' &&  S=='D' && V=='D') {
                        InputConnection inputConnection = getCurrentInputConnection();
                        inputConnection.deleteSurroundingText(1000,1000);
                    }
                    if(E!='D' && E!='C'){
                        tempMsg=tempMsg.substring(3);
                        InputConnection inputConnection = getCurrentInputConnection();
                        inputConnection.commitText(String.valueOf(tempMsg), 1);
                        if(E=='T'){

                        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));

                        }
                        if(S=='T'){
                            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                            am.playSoundEffect(SoundEffectConstants.CLICK);
                            //MediaPlayer mp = MediaPlayer.create(getApplicationContext(),R.raw.gun);
                           // mp.start();
                        }
                        if(V=='T'){
                            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                            if (Build.VERSION.SDK_INT >= 26) {
                                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                            }
                            else {
                                vibrator.vibrate(200);
                            }
                        }
                        break;}
            }
            return true;
        }
    });


    private class ServerClass extends Thread{
        private BluetoothServerSocket serverSocket;
        public ServerClass(){
            try {
                serverSocket=bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run(){
            BluetoothSocket socket=null;
            while(socket==null){
                try {
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);
                    socket=serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;
                    handler.sendMessage(message);
                }
                if(socket!=null){
                    Message message=Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);
                    sendReceive= new SendReceive(socket);
                    sendReceive.start();
                    break;
                }
            }
        }
    }

    private class SendReceive extends  Thread{
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket)
        {
            bluetoothSocket=socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;
            try {
                tempIn = bluetoothSocket.getInputStream();
                tempOut= bluetoothSocket.getOutputStream();
            }catch (IOException e){
                e.printStackTrace();
            }
            inputStream=tempIn;
            outputStream=tempOut;
        }
        public void run(){
            byte[] buffer=new byte[1024];
            int bytes;

            while(true){
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECIVED, bytes,-1, buffer).sendToTarget();

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }



    // Listeners
    @Override
    public void onPress(int primaryCode) {
        // no-op
    }

    @Override
    public void onRelease(int primaryCode) {
        // no-op
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {
        //1
        if (primaryCode == 49) {
            ServerClass serverClass=new ServerClass();
            serverClass.start();
        }
        if (primaryCode == 50) {
            InputConnection inputConnection = getCurrentInputConnection();
            inputConnection.deleteSurroundingText(50,50);

        }



    }



    @Override
    public void onText(CharSequence text) {

    }

    @Override
    public void swipeLeft() {
        // no-op
    }

    @Override
    public void swipeRight() {
        // no-op
    }

    @Override
    public void swipeDown() {
        // no-op
    }

    @Override
    public void swipeUp() {
        // no-op
    }
}