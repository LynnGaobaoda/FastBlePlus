package com.clj.blesample.MyActivity;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.clj.blesample.R;
import com.clj.blesample.comm.Observer;
import com.clj.blesample.comm.ObserverManager;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class data_show_activity extends AppCompatActivity implements Observer {
    public static final String KEY_DATA = "key_data";
    private BleDevice bleDevice;
    final private String ESP32_SERVICE_UUID = "e41ea377-7671-41aa-bc1a-abc4c38c8106";
    final private String ESP32_CHARACTER_UUID = "cba1d466-344c-4be3-ab3f-189f80dd7518";
    private boolean is_m_character;
    BluetoothGattService m_service;
    private BluetoothGattCharacteristic m_characteristic;

    //控件
    TextView  edit_text_time;
    TextView text_temperature;

     byte[] RxBuffer; //下位机通知数据缓存
     int _data_cnt ;
     int state ;
     int _arg_row ,_buffer_ptr;
     boolean framestart ;
     long  receive_time;
     int i ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("Tag","Msg==============");//Debug  调试
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_show);

        initData();
        initView();
        //initPage();

        ObserverManager.getInstance().addObserver(this);
    }

    private void initData() {
        RxBuffer = new byte[16] ;
        _data_cnt = 0;
        state = 0;
        _arg_row = 0;
        _buffer_ptr=0;
        framestart = false;
        receive_time=0;
        i = 0;

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
        toolbar.setTitle("数据显示");
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


        edit_text_time = findViewById(R.id.Text_real_time);
        edit_text_time.setText("0");
        text_temperature = findViewById(R.id.textView_temperature);
        text_temperature.setText("0");
        if(is_m_character) {
            title_view.setText("无线遥控小车");

            //开关实时数据按钮
            Button btn_get_real_data = findViewById(R.id.btn_get_real_time_data);
            btn_get_real_data.setText("打开实时数据");
            btn_get_real_data.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    if (btn_get_real_data.getText().toString().equals("打开实时数据")) {
                        btn_get_real_data.setText("关闭实时数据");
                        //发送按下指令
                        String hex = "FF0A010102010000";  //打开实时数据
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback);

                    } else {
                        btn_get_real_data.setText("打开实时数据");
                        String hex = "FF0A010102020000";  //关闭实时数据
                        BleManager.getInstance().write(
                                bleDevice,
                                ESP32_SERVICE_UUID,
                                ESP32_CHARACTER_UUID,
                                HexUtil.hexStringToBytes(hex),
                                m_BleWriteCallback);
                    }
                }
            });

            //更新一次温度数据按钮
            Button btn_get_a_data = findViewById(R.id.btn_get_a_data);
            btn_get_a_data.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //发送 获取温度数据 指令
                    String hex = "FF0A020102010000";
                    BleManager.getInstance().write(
                            bleDevice,
                            ESP32_SERVICE_UUID,
                            ESP32_CHARACTER_UUID,
                            HexUtil.hexStringToBytes(hex),
                            m_BleWriteCallback);

                }
            });

            String hex = "FF0A010102020000";  //默认 关闭实时数据发送
            BleManager.getInstance().write(
                    bleDevice,
                    ESP32_SERVICE_UUID,
                    ESP32_CHARACTER_UUID,
                    HexUtil.hexStringToBytes(hex),
                    m_BleWriteCallback);

            //开启下位机通知，接收数据
            BleManager.getInstance().notify(
                    bleDevice,
                    ESP32_SERVICE_UUID,
                    ESP32_CHARACTER_UUID,
                    new BleNotifyCallback() {

                        @Override
                        public void onNotifySuccess() {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //Toast.makeText(data_show_activity.this,"打开实时数据成功",Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onNotifyFailure(final BleException exception) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
//                                                Toast.makeText(data_show_activity.this,"打开实时数据失败:"+exception.toString(),Toast.LENGTH_LONG).show();
//                                                Log.v("aa",exception.toString());
                                }
                            });
                        }
                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //todo 解析下位机 实时数据（通知数据）
                                    //addText(txt, HexUtil.formatHexString(characteristic.getValue(), true));
                                    byte [] ESP32_data = m_characteristic.getValue();

                                    for (int i = 0;i<ESP32_data.length;i++){
                                        ESP32_Data_Receive_Prepare(ESP32_data[i]);
                                    }
                                }
                            });
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


    private  void ESP32_Data_Receive_Prepare(byte data){
    if((System.currentTimeMillis() - receive_time)>200 && receive_time != 0 && framestart)
        {
            state=_data_cnt=_buffer_ptr=_arg_row=0;
            framestart = false;
            return; //超时
        }
        receive_time = System.currentTimeMillis();
        if(state==0&& data== (byte)0xFF)  //帧起始标志
        {
            state=1;
            _data_cnt=_buffer_ptr=_arg_row=0;
            framestart =true;
            RxBuffer[0]=data;
        }
        else if(state==1 && framestart)  //Module 模式
        {

            state=2;
            RxBuffer[1]=data;
        }
        else if(state==2) // functions
        {
            state=3;
            RxBuffer[2]=data;
        }
        else if(state==3 && framestart)
        {

            //arg数据 行数 如 1 ==1行
            //2 3    2行4列 后面共8个数据，包含结尾的0
            state = 4;
            _arg_row = data;
            _buffer_ptr = 0;
            _data_cnt = _arg_row; ///数据长度信息 所占 字节数
            RxBuffer[3]=data;	//数据长度信息 所占 字节数

        }
        else if(state==4 && framestart)  //获取数据长度信息
        {

            RxBuffer[4+_buffer_ptr++]=data;
            if (_arg_row == 0 && _buffer_ptr == 1){ //数据长度信息=0 则data[5]也为0 请求连接帧
                state = 6;// 判断结束
                return;
            }
            _data_cnt = (data+1);  //获取实际数据《目前只 支持1Byte的数据长度信息》
            state = 5;

        }
        else if((state==5)  && framestart) //只有 有数据才会到state5
        {
            _data_cnt--;
            RxBuffer[4+_buffer_ptr++]=data;

            if(_data_cnt == 1){ //剩最后一个 00结束符号 交给 6 判断
                state = 6;
            }

        }
        else if(state==6 && framestart)  //数据读完，处理数据
        {

            RxBuffer[4+_buffer_ptr++]=data; //最后一位00
            ESP32_Data_Receive_Anl(RxBuffer,4+_buffer_ptr);//处理数据
            state=_data_cnt=_buffer_ptr=_arg_row=0;
            framestart=false;

        }
        else {
            state = _data_cnt = _buffer_ptr = _arg_row = 0;
            framestart=false;
        }
    }

    private void ESP32_Data_Receive_Anl(byte [] data, int length){
        byte moduleID,functionID;
        moduleID = data[1];
        functionID = data[2];

        if((data[0] != (byte)0xff)) return;
//        Log.v("ccccc", ""+ data[0]);
        if( moduleID == (byte)0x00){ //刷新连接、PinStateMonitor

        }
        else if(moduleID == (byte)0x01){ //实时数据
            if(functionID == (byte)0x01){ //时间数据
                int time_data = 0,buf_data=0;
                buf_data = data[5];time_data |= buf_data<<24;
                buf_data = data[6];time_data |= buf_data<<16;
                buf_data = data[7];time_data |= buf_data<<8;
                buf_data = data[8];time_data |= buf_data<<0;
                edit_text_time.setText(String.valueOf(time_data));
            }
            else if(functionID == (byte)0x02){ //轮盘功能 Joystick
                //cotrol_motor_by_angle_speed(((data1 >> 3)*15), (data1&0x07));
            }
            else if(functionID == (byte)0x03){ //ACC模式，实际为0x02 IOS未更新
            }

        }
        else if(moduleID == (byte)0x02){ //非实时数据
            if(functionID == (byte)0x01){  //温度数据
                int temperature_data = 0,buf_data=0;
                temperature_data = data[6];buf_data |= temperature_data;
                text_temperature.setText(String.valueOf(buf_data));
            }

        }
        else if(moduleID == (byte)0x0A){ //LED Brightness Control

        }
        else if(moduleID == (byte)0x04){ //LED Brightness Control

        }
        else{

        }

    }



}
