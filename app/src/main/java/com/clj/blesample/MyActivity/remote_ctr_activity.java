package com.clj.blesample.MyActivity;



import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.clj.blesample.R;
import com.clj.blesample.comm.Observer;
import com.clj.blesample.comm.ObserverManager;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class remote_ctr_activity extends AppCompatActivity implements Observer {
    public static final String KEY_DATA = "key_data";
    private BleDevice bleDevice;
    final private String ESP32_SERVICE_UUID = "e41ea377-7671-41aa-bc1a-abc4c38c8106";
    final private String ESP32_CHARACTER_UUID = "cba1d466-344c-4be3-ab3f-189f80dd7518";
    private boolean is_m_character;
    BluetoothGattService m_service;
    private BluetoothGattCharacteristic m_characteristic;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("Tag","Msg==============");//Debug  调试
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_ctrl);

        initData();
        initView();
        //initPage();

        ObserverManager.getInstance().addObserver(this);
    }

    private void initData() {
        bleDevice = getIntent().getParcelableExtra(KEY_DATA); //获取蓝牙设备
        if (bleDevice == null)
            finish();

        m_service = null;
        BluetoothGatt gatt = BleManager.getInstance().getBluetoothGatt(bleDevice);//获取蓝牙服务
        for (BluetoothGattService service : gatt.getServices()) {  //查找指定服务
            String uuid = service.getUuid().toString();
            Log.v("aaa","========="+uuid);
            if (uuid.equals(ESP32_SERVICE_UUID)){ //ESP32的指定服务
                m_service = service;
                break;
            }
        }
        //查找指定特征
        m_characteristic = null;
        is_m_character = false;
        for (BluetoothGattCharacteristic characteristic : m_service.getCharacteristics()) {
            String uuid = characteristic.getUuid().toString();
            if (uuid.equals(ESP32_CHARACTER_UUID)){
                m_characteristic = characteristic;
                is_m_character = true;
                break;
            }
        }
    }
    @Override
    public void disConnected(BleDevice device) {
        if (device != null && bleDevice != null && device.getKey().equals(bleDevice.getKey())) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BleManager.getInstance().clearCharacterCallback(bleDevice);
        ObserverManager.getInstance().deleteObserver(this);
    }

    private void initView() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("无线遥控");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });  //返回

        TextView title_view = findViewById(R.id.txt_title);
        TextView uuid_view = findViewById(R.id.txt_uuid);
        TextView mac_view = findViewById(R.id.txt_mac);

        Button btn_back = findViewById(R.id.btn_get_a_data);
        if(is_m_character) {
            title_view.setText("无线遥控小车");
            btn_back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }


            });

            //前进按钮
            Button btn_front = findViewById(R.id.btn_get_real_time_data);
            btn_front.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                        //发送按下指令
                        String hex = "FF01010102040000";  //dabble 三角形 前进指令
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback);
                    }

                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        //发送松手指令
                        String hex = "FF01010102000000";  //按键松手指令
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback
                        );
                    }
                    return true;
                }



            });

            //后退按钮
            btn_back.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View view, MotionEvent motionEvent) {
                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                        //发送按下指令
                        String hex = "FF01010102100000";  //后退 按压指令
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback);
                    }

                    if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                        //发送松手指令
                        String hex = "FF01010102000000"; //松手
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback
                                );
                    }
                    return true;
                }



            });



        }
        else{
            title_view.setText("不是我的小车无法控制");
        }
        uuid_view.setText("UUID:"+m_service.getUuid().toString());
        mac_view.setText("");
    }


    final BleWriteCallback m_BleWriteCallback = new BleWriteCallback() {

        @Override
        public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("aaa" ,"write success, current: " + current
                            + " total: " + total
                            + " justWrite: " + HexUtil.formatHexString(justWrite, true));
                }
            });
        }

        @Override
        public void onWriteFailure(final BleException exception) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d("bbb", exception.toString());
                }
            });
        }
    };

}
