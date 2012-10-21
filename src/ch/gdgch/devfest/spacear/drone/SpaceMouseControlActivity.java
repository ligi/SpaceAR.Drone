package ch.gdgch.devfest.spacear.drone;

import java.io.IOException;
import java.net.InetAddress;


import com.codeminders.ardrone.ARDrone;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

public class SpaceMouseControlActivity extends Activity {

	private UsbManager mManager;
	private UsbDevice mDevice;
	private UsbDeviceConnection mDeviceConnection;
	private UsbInterface mInterface;
	private UsbEndpoint mEndpointIn;
	private PendingIntent mPermissionIntent;
	private TextView conn_tv,nick_tv,roll_tv,yaw_tv,alt_tv,btns_tv,drone_state_tv;
	
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

	private final WaiterThread mWaiterThread = new WaiterThread();

	public long[] translate = new long[3];
	public long[] rotation = new long[3];
	public byte btns;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		

        
		
		setContentView(R.layout.activity_space_mouse_control);

		conn_tv=(TextView)findViewById(R.id.connection);
		
		roll_tv=(TextView)findViewById(R.id.roll);
		nick_tv=(TextView)findViewById(R.id.nick);
		yaw_tv=(TextView)findViewById(R.id.yaw);
		alt_tv=(TextView)findViewById(R.id.alt);
		btns_tv=(TextView)findViewById(R.id.btns);
		drone_state_tv=(TextView)findViewById(R.id.drone_state);
		
		mHandler.post(new UpdateRunnable());
		
		conn_tv.setText("false");
		
		mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);

		// listen for new devices
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		filter.addAction(ACTION_USB_PERMISSION);
		registerReceiver(mUsbReceiver, filter);

		// check for existing devices

		for (UsbDevice device : mManager.getDeviceList().values()) {
			mInterface = findInterface(device);
			if (findEndPoints(mInterface)) {
				setUsbInterface(device, mInterface);
				break;
			}
		}
	}

	private UsbInterface findInterface(UsbDevice device) {
		int count = device.getInterfaceCount();
		for (int i = 0; i < count; i++) {
			UsbInterface intf = device.getInterface(i);
			if (device.getVendorId() == 1133) // 3Dx devices
			{
				conn_tv.setText("found");
				
				return intf;
			}
		}
		return null;
	}

	protected boolean findEndPoints(UsbInterface intf) {
		int nEndPoints = intf.getEndpointCount();
		for (int i = 0; i < intf.getEndpointCount(); i++) {
			UsbEndpoint ep = intf.getEndpoint(i);
			int eptype = ep.getType();
			if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
				if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
				} else {
					mEndpointIn = ep;
					return true;
				}
			}
		}
		return false;
	}

	// Sets the current USB device and interface
	private boolean setUsbInterface(UsbDevice device, UsbInterface intf) {
		if (mDeviceConnection != null) {
			if (mInterface != null) {
				mDeviceConnection.releaseInterface(mInterface);
				mInterface = null;
			}
			mDeviceConnection.close();
			mDevice = null;
			mDeviceConnection = null;
		}

		if (device != null && intf != null) {
			conn_tv.setText("found and conecting");
			
			mManager.requestPermission(device, mPermissionIntent);
			UsbDeviceConnection connection = mManager.openDevice(device);
			if (connection != null) {
				if (connection.claimInterface(intf, true)) {
					mDevice = device;
					mDeviceConnection = connection;

					mInterface = intf;

					conn_tv.setText("found reading thread started");
		
					(new DroneStarter()).execute(SpaceMouseControlActivity.drone); 
			        
					mWaiterThread.start();
					return true;
				} else {
					connection.close();
				}
			} else {
			}
		}

		return false;
	}

	public int bytesToInt(byte lsb, byte msb) {
		int x = (((int) (msb) << 8) & 0xff00) | (((int) lsb) & 0x00ff);

		// Negative?
		if ((x & 0x8000) != 0)
			x |= 0xffff0000;

		return x;
	}

	Handler mHandler=new Handler();
	
	enum DroneState {
		CONNECTING,
		CONNECTED,
		LANDED,
		FLYING
	}
	
	DroneState drone_state=DroneState.CONNECTING;
	
	class UpdateRunnable implements Runnable {

		@Override
		public void run() {
			nick_tv.setText("" + translate[1]);
			roll_tv.setText("" + translate[0]);
			alt_tv.setText("" + translate[2]);
			
			yaw_tv.setText("" + rotation[2]);
			
			btns_tv.setText("" + btns);
			
			drone_state_tv.setText(""+drone_state);

			
			if (drone_state==DroneState.FLYING) 
	        try {
	        	
				drone.move(translate[0]/600f, translate[1]/600f,translate[2]/1200, rotation[2]/360f);
			} catch (IOException e1) {
			}
			
			if ((btns==1)&&(drone_state==DroneState.CONNECTED || drone_state==DroneState.LANDED)) {
				
				try {
					drone.clearEmergencySignal();
					Log.i(TAG,"trim");
	                drone.trim();
	                Log.i(TAG,"takeof");
	                drone.takeOff();
	                
	                drone_state=DroneState.FLYING;
	                
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
			}
			
			if ((btns==2)&&(drone_state==DroneState.FLYING)) {
				
				try {
					drone.land();
					
					drone_state=DroneState.LANDED;
	                
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
			}
		}
		
	}
	
	private class WaiterThread extends Thread {
		public boolean mStop;

		public void run() {

			while (true) {
				byte[] bytes = new byte[7];
				int TIMEOUT = 0;
				int length = 7;
				int result;

				result = mDeviceConnection.bulkTransfer(mEndpointIn, bytes,
						length, TIMEOUT);

				
				// Translation packet comes in before rotation packet. Wait
				// until you have both before
				// doing anything with the data

				if (bytes[0] == 1) // Translation packet
				{
					translate[0] = bytesToInt(bytes[1], bytes[2]);
					translate[1] = bytesToInt(bytes[3], bytes[4]);
					translate[2] = bytesToInt(bytes[5], bytes[6]);
				}

				else if (bytes[0] == 2) // Rotation packet
				{
					rotation[0] = bytesToInt(bytes[1], bytes[2]);
					rotation[1] = bytesToInt(bytes[3], bytes[4]);
					rotation[2] = bytesToInt(bytes[5], bytes[6]);

					String dataString = new String();
					dataString = String.format("t:%d %d %d   r: %d %d %d ",
							translate[0], translate[1], translate[2], rotation[0], rotation[1], rotation[2]);
					// log(dataString);
				}

				else if (bytes[0] == 3) // Button packet
				{
					btns=bytes[1];
				}
				mHandler.post(new UpdateRunnable());
				
			}

		}
	}

	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				UsbDevice device = (UsbDevice) intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				UsbDevice device = intent
						.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				String deviceName = device.getDeviceName();
				if (mDevice != null && mDevice.equals(deviceName)) {
				}
			} else if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbDevice device = (UsbDevice) intent
							.getParcelableExtra(UsbManager.EXTRA_DEVICE);

					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						if (device != null) {
						}
					} else {
					}
				}
			}
		}
	};


    // drone stuff
    public final static String TAG="SpaceMouseControl";
    
    private static final long CONNECTION_TIMEOUT = 10000;
    
    final static byte[]                     DEFAULT_DRONE_IP  = { (byte) 192, (byte) 168, (byte) 1, (byte) 1 };

	static ARDrone drone;


    private class DroneStarter extends AsyncTask<ARDrone, Integer, Boolean> {
        
        @Override
        protected Boolean doInBackground(ARDrone... drones) {
            ARDrone drone = drones[0];
            try {
            	Log.i(TAG,"connecting to drone");
            	drone = new ARDrone(InetAddress.getByAddress(DEFAULT_DRONE_IP), 10000, 60000);
                SpaceMouseControlActivity.drone = drone; // passing in null objects will not pass object refs
                drone.connect();
                drone.clearEmergencySignal();
                drone.waitForReady(CONNECTION_TIMEOUT);
                drone.playLED(1, 10, 4);
                //drone.addImageListener(MainActivity.mainActivity);
                //drone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY);
                drone.setCombinedYawMode(false);
                
                return true;
            } catch (Exception e) {
            	Log.i(TAG,"connecting fail " + e);
                try {
                	
                    drone.clearEmergencySignal();
                    drone.clearImageListeners();
                    drone.clearNavDataListeners();
                    drone.clearStatusChangeListeners();
                    drone.disconnect();
                } catch (Exception e1) {
                }
              
            }
            return false;
        }

        

        
        protected void onPostExecute(Boolean success) {
        	Log.i(TAG,"connecting post " + success.booleanValue());
            if (success.booleanValue()) {
            	  try
                  {
            		  
            		  drone_state=DroneState.CONNECTED;
                      
                  } catch(Throwable e)
                  {
                      e.printStackTrace();
                  }
                /*state.setTextColor(Color.GREEN);
                state.setText("Connected");
                connectButton.setEnabled(false);
                mainActivity.showButtons();
                */
            } else {
            }
        }
    }
}
