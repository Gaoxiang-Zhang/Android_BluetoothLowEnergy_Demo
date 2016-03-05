package com.example.mobile.bledemo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    private Context context;

    static final String DEBUG_TAG = "debug_tag";

    BluetoothAdapter bluetoothAdapter;

    private TextView data = null;
    private Button refreshButton, scanButton, stopButton;
    private ListView listView = null;
    private ArrayAdapter<String> arrayAdapter = null;
    private List<BluetoothDevice> deviceList;
    private List<String> deviceName;

    private boolean mScanning = true;
    private boolean mConnected = false;
    private Handler mHandler;

    private static final long SCAN_PERIOD = 10000;

    private BluetoothLeService mBluetoothLeService;
    private String mDeviceAddress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;

        initInterface();

        initBluetooth();
    }

    private void initInterface(){
        data = (TextView)findViewById(R.id.text_transferred);

        scanButton = (Button)findViewById(R.id.scan_button);
        stopButton = (Button)findViewById(R.id.stop_button);
        refreshButton = (Button)findViewById(R.id.refresh_button);

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                arrayAdapter.clear();
                deviceList.clear();
                deviceName.clear();
                scanLeDevice(true);
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(false);
            }
        });

        deviceList = new ArrayList<>();
        deviceName = new ArrayList<>();
        listView = (ListView)findViewById(R.id.list_view);
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,deviceName);
        listView.setAdapter(arrayAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = deviceList.get(position);
                if(device == null){
                    return;
                }
                Intent intent = new Intent(context, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                if(mScanning){
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
                startActivity(intent);
            }
        });
    }

    private boolean initBluetooth(){
        // initialize bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        // check if bluetooth supported
        if(bluetoothAdapter == null){
            Log.d(DEBUG_TAG, "Bluetooth not supported");
            return false;
        }
        // check if ble not supported
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Log.d(DEBUG_TAG, "BLE not supported");
            return false;
        }
        // if bluetooth has not been enabled, try enabling it
        if(!bluetoothAdapter.isEnabled()){
            bluetoothAdapter.enable();
        }
        mHandler = new Handler();
        scanLeDevice(true);
        return true;
    }

    private void scanLeDevice(final boolean enable){
        if(enable){
            // stops scanning after a pre-defined period
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);
            mScanning = true;
            bluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            bluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback(){
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!deviceList.contains(device)) {
                        deviceList.add(device);
                        deviceName.add(device.getName());
                        arrayAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if(!mBluetoothLeService.initialize()){
                Log.d(DEBUG_TAG, "Unable to initialize Bluetooth");
                finish();
            }
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                Log.d(DEBUG_TAG, data);
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter(){
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onPause(){
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
    }
}
