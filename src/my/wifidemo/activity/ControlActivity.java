package my.wifidemo.activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;

import my.wifidemo.R;
import my.wifidemo.R.id;
import my.wifidemo.observer.ScreenObserver;
import my.wifidemo.observer.ScreenObserver.ScreenStateListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.zerokol.views.JoystickView;
import com.zerokol.views.JoystickView.OnJoystickMoveListener;

/**
 * @author chjy 使用socket连接无人机的TCP服务器
 * 
 * 
 */

public class ControlActivity extends Activity implements OnClickListener {
	public static final String TAG = "RECEIVE_ACTIVITY";
	private int version;
	
	private Button btnLED1On = null;
	private Button btnLED1Off = null;
	private Button btnLED2On = null;
	private Button btnLED2Off = null;
	private Button btnLED3On = null;
	private Button btnLED3Off = null;
	private Button btnStartReceive = null;
	private Button btnOpenCamera = null;
	private Button btnCloseCamera = null;
	private Button btnCaptureScreen = null;
	private Button btnBack=null;
	private ImageView imageViewVideo = null;
	private ImageView imageViewMask=null;
	private TextView ipTextView = null;
	private TextView angleTextView = null;
	private TextView powerTextView = null;
	private TextView directionTextView = null;
	private JoystickView joystickLeft = null;
	
	private Dialog dialog = null;
	private ScreenObserver screenObserver=null;

	// private Socket socket = null;

	private MyHandler myHandler = new MyHandler();
	public static final int REFRESH_VIEW = 0;
	public static final int DISPLY_DIALOG = 1;
	public static final int DISMISS_DIALOG = 2;
	public static final int RESET_BUTTON_STATUS=3;

	// private Button btnLED4On = null;
	// private Button btnLED4Off = null;

	// private EditText field_name; // 接收用户名的组件
	// private String ipstr = "192.168.43.174";

	private static String ipstr = "";
	private static int UDP_SERVER_PORT = 10086;
	private static int UDP_SERVER_PORT_LOCAL=12306;
	
	private ImageReceiveThread imageReceiveThread = null;
	private HeartBeatThread heartBeatThread;
	private ChangeCtrlMsgThread changeCtrlMsgThread;
	private ControlInfoReceiveThread controlInfoReceiveThread;
	
	private Boolean imageRecenable=true;
	private Boolean cameraOpenFlagBoolean = false;
	private Boolean HeartBeatThreadEnable=true;
	private Boolean ctrlInfoThreadEnable=true;
	
	public static String controlMsg="0";
	
	private static WifiManager wifiManager;
	private static WifiManager.MulticastLock multicastLock;
	
	private DatagramSocket datagramSocket;
	
	/**
	 * 方便其他页面可以启动该页面
	 * @param context Context当前上下文
	 * @param ip String 用于连接服务器的IP地址
	 * @param port String 用于连接应用的端口号
	 * */
	public static void startControlActivity(Context context,String ip,String port){
		Intent intent=new Intent(context,ControlActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("IP", ip);
		intent.putExtra("PORT", port);
		context.startActivity(intent);
	}

	@Override
	public void onBackPressed() {
		
		closeControlThreads();
		backToPage();
	}
	
	
	
	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		closeControlThreads();

	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		heartBeatThread=new HeartBeatThread("HEART_BEAT",datagramSocket);
		heartBeatThread.start();
		
		changeCtrlMsgThread = new ChangeCtrlMsgThread("CHANGE_CTRL");
		changeCtrlMsgThread.start();
		
		controlInfoReceiveThread=new ControlInfoReceiveThread("CTRL_INFO",datagramSocket);
		controlInfoReceiveThread.start();
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		
		closeControlThreads();
		super.onDestroy();
	}

	/** Called when the activity is first created. */
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 隐藏虚拟按键
		int version = android.os.Build.VERSION.SDK_INT;
		final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
				| View.KEEP_SCREEN_ON;
		// This work only for android 4.4+
		if (version >= Build.VERSION_CODES.KITKAT) {

			getWindow().getDecorView().setSystemUiVisibility(flags);

			// Code below is to handle presses of Volume up or Volume down.
			// Without this, after pressing volume buttons, the navigation bar
			// will
			// show up and won't hide
			final View decorView = getWindow().getDecorView();
			decorView
					.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {

						public void onSystemUiVisibilityChange(int visibility) {
							if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
								decorView.setSystemUiVisibility(flags);
							}
						}
					});
		}

		setContentView(R.layout.activity_control);
		
		btnLED1On = (Button) findViewById(R.id.ButtonLED1On);
		btnLED1Off = (Button) findViewById(R.id.ButtonLED1Off);
		btnLED2On = (Button) findViewById(R.id.ButtonLED2On);
		btnLED2Off = (Button) findViewById(R.id.ButtonLED2Off);
		btnLED3On = (Button) findViewById(R.id.ButtonLED3On);
		btnLED3Off = (Button) findViewById(R.id.ButtonLED3Off);
		btnStartReceive = (Button) findViewById(R.id.ButtonStartRecevie);
		btnOpenCamera = (Button) findViewById(R.id.ButtonOpenCamera);
		btnCloseCamera = (Button) findViewById(R.id.ButtonCloseCamera);
		btnCaptureScreen = (Button) findViewById(R.id.buttonCapturePic);
		btnBack=(Button)findViewById(R.id.buttonBack);
		imageViewVideo = (ImageView) findViewById(R.id.VideoImage);
		imageViewMask=(ImageView)findViewById(R.id.imageViewbg);
		ipTextView = (TextView) findViewById(R.id.textViewip);
		angleTextView = (TextView) findViewById(R.id.textViewAngle);
		powerTextView = (TextView) findViewById(R.id.textViewPower);
		directionTextView = (TextView) findViewById(R.id.textViewDirection);
		joystickLeft = (JoystickView) findViewById(R.id.joystickLeft);

		btnLED1On.setOnClickListener(this);

		btnLED1Off.setOnClickListener(this);

		btnLED2On.setOnClickListener(this);

		btnLED2Off.setOnClickListener(this);

		btnLED3On.setOnClickListener(this);

		btnLED3Off.setOnClickListener(this);

		btnStartReceive.setOnClickListener(this);

		btnOpenCamera.setOnClickListener(this);

		btnCloseCamera.setOnClickListener(this);

		btnCaptureScreen.setOnClickListener(this);
		
		btnBack.setOnClickListener(this);

		joystickLeft.setOnJoystickMoveListener(joystickLeftListener, JoystickView.DEFAULT_LOOP_INTERVAL);
		
		screenObserver= new ScreenObserver(getApplicationContext());
		screenObserver.requestScreenStateUpdate(screenStateListener);
		//监听屏幕的锁屏状态
		
		Bundle bundle = getIntent().getExtras();
		ipstr = bundle.getString("IP");
		UDP_SERVER_PORT = Integer.parseInt(bundle.getString("PORT").trim());
		//获得IP地址和端口号
		
		ipTextView.setText(ipstr);
		
		wifiManager=(WifiManager) this
				.getSystemService(Context.WIFI_SERVICE);
		multicastLock= wifiManager.createMulticastLock("test wifi");
		//打开wifi广播索
		
		heartBeatThread=new HeartBeatThread("HEART_BEAT",datagramSocket);
		heartBeatThread.start();
		
		changeCtrlMsgThread = new ChangeCtrlMsgThread("CHANGE_CTRL");
		changeCtrlMsgThread.start();
		
		controlInfoReceiveThread=new ControlInfoReceiveThread("CTRL_INFO",datagramSocket);
		controlInfoReceiveThread.start();
				
		//开启心跳线程，确认飞机和遥控端的连接状态
		

	}

	/** 设置布局中按钮的点击事件 **/
	public void onClick(View view) {
		switch (view.getId()) {
		case R.id.ButtonLED1On:
			sendUDPCommand("LED_OPEN1",datagramSocket);
			break;
		case R.id.ButtonLED1Off:
			sendUDPCommand(new byte[]{(byte)0xaa,(byte)0xaf,(byte)0x01,(byte)0x00,
					(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,
					(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,},
					datagramSocket);
			break;
		case R.id.ButtonLED2On:
			sendUDPCommand("LED_OPEN2",datagramSocket);
			
			
			imageViewVideo.setDrawingCacheEnabled(true);
			Bitmap detectBitmap = Bitmap.createBitmap(imageViewVideo
					.getDrawingCache());
			imageViewVideo.setDrawingCacheEnabled(false);
			
			DetectFaceAsyncTask detectFaceAsyncTask=new DetectFaceAsyncTask();
			detectFaceAsyncTask.setBitmap(detectBitmap);
			detectFaceAsyncTask.execute();
			
           // new DetectFaceAsyncTask(detectBitmap).execute();

			break;
		case R.id.ButtonLED2Off:
			sendUDPCommand("TCP_EXCEPTION",datagramSocket);
			break;
		case R.id.ButtonLED3On:
			sendUDPCommand("JDQ_OPEN",datagramSocket);
			break;
		case R.id.ButtonLED3Off:
			sendUDPCommand("JDQ_CLOSE",datagramSocket);
			break;
		case R.id.ButtonOpenCamera: {

			btnCloseCamera.setVisibility(View.VISIBLE);
			btnStartReceive.setVisibility(View.VISIBLE);
			btnOpenCamera.setVisibility(View.GONE);

			OpenCameraThread OpenCameraThread = new OpenCameraThread(
					"OPENCAMRERA_THREAD");
			OpenCameraThread.start();
			Log.i(TAG, "[btnOpenCamera]Thread create");
			break;
		}
		case R.id.ButtonStartRecevie: {
			btnStartReceive.setVisibility(View.GONE);
			imageReceiveThread = new ImageReceiveThread("IMAGERECEIVE_THREAD");
			imageReceiveThread.start();
			break;
		}
		case R.id.ButtonCloseCamera: {
			btnCloseCamera.setVisibility(View.GONE);
			btnOpenCamera.setVisibility(View.VISIBLE);
			btnStartReceive.setVisibility(View.GONE);
			CloseCameraThread thread = new CloseCameraThread(
					"CLOSECAMERA_THREAD");
			thread.start();
			break;
		}
		
		case R.id.buttonBack:{
			backToPage();
			break;
		}

		case R.id.buttonCapturePic: {
			try {
				String path = getSDPath();
				File dir=new File(path+"/Capture/");
				
				if(!dir.exists()){
					dir.mkdir();					
				}		    
				imageViewVideo.setDrawingCacheEnabled(true);
				Bitmap caputreBitmap = Bitmap.createBitmap(imageViewVideo
						.getDrawingCache());
				
				SimpleDateFormat formatter = new SimpleDateFormat(
						"yyyyMMdd_HHmmss");
				Date curDate = new Date(System.currentTimeMillis());// 获取当前时间
				String fileName = formatter.format(curDate);
				
				File file = new File(path + "/Capture/", fileName+".jpg");
				boolean isFileCreated=file.createNewFile();
				
				if(isFileCreated){
					FileOutputStream out = new FileOutputStream(file);
					caputreBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
					out.flush();
					out.close();					
					Log.i(TAG, "已经保存图片");	
				}
				
				Animation animation=AnimationUtils.loadAnimation(this, R.anim.abc_fade_out);
				imageViewVideo.startAnimation(animation);
				imageViewVideo.setDrawingCacheEnabled(false);

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}catch (Exception e){
				e.printStackTrace();
				Log.e(TAG, e.toString());
			}
			break;
		}
		default:
			break;
		}
	}
	
	/**
	 * 设置左摇杆的移动事件
	 * */
	
	OnJoystickMoveListener joystickLeftListener= new OnJoystickMoveListener(){

		public void onValueChanged(int angle, int power, int direction) {
			// TODO Auto-generated method stub
			angleTextView.setText(" " + String.valueOf(angle) + "°");
			powerTextView.setText(" " + String.valueOf(power) + "%");
			//changeCtrlMsgThread.getHandler().sendEmptyMessage(1);
			synchronized (controlMsg) {
				controlMsg="1";
			}
			switch (direction) {
			case JoystickView.FRONT:
				directionTextView.setText("N");
				break;
			case JoystickView.FRONT_RIGHT:
				directionTextView.setText("NE");
				break;
			case JoystickView.RIGHT:
				directionTextView.setText("E");
				break;
			case JoystickView.RIGHT_BOTTOM:
				directionTextView.setText("SE");
				break;
			case JoystickView.BOTTOM:
				directionTextView.setText("S");
				break;
			case JoystickView.BOTTOM_LEFT:
				directionTextView.setText("NW");
				break;
			case JoystickView.LEFT:
				directionTextView.setText("W");
				break;
			case JoystickView.LEFT_FRONT:
				directionTextView.setText("S");
				break;
			default:
				directionTextView.setText("C");
			}
		}
	};
	
	/**
	 * 监听屏幕的锁屏状态并且执行操作
	 * */
	ScreenStateListener screenStateListener=new ScreenStateListener() {
		
		public void onScreenOn() {
			// TODO Auto-generated method stub		
			
		}
		
		public void onScreenOff() {
			// TODO Auto-generated method stub
			CloseCameraThread closThread=new CloseCameraThread("CLOSECAM_LOCKSCREEN");
			closThread.start();		
			closeControlThreads();
			
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			resetButtonStatus();

		}
	};
	
	/**
	 * 发送心跳包的线程，确认飞机和手机的连接状态
	 * */
	class HeartBeatThread extends HandlerThread{

		private DatagramSocket ds=null;
		private String udpMsg="";
		private String ctrString="";
		private int count=0;
		//public Handler mHandler;
		public HeartBeatThread(String name,DatagramSocket datagramSocket) {
			super(name);
			// TODO Auto-generated constructor stub
			ds=datagramSocket;
		}
		

		@Override
		public void run(){
			
			
			try {
				if(ds==null){
					
					ds = new DatagramSocket(UDP_SERVER_PORT_LOCAL);
				}
				
				InetAddress serverAddr = InetAddress.getByName(ipstr);
				while(true && HeartBeatThreadEnable){
					
					DatagramPacket dp;
					udpMsg=controlMsg;
					dp = new DatagramPacket(udpMsg.getBytes(), udpMsg.length(),
							serverAddr, UDP_SERVER_PORT);
					ds.send(dp);
					count=(count+1)%2;
			/*		Log.i(TAG,udpMsg);
					Log.i(TAG,ctrString);
*/
					
					synchronized (controlMsg) {
						controlMsg="0";
					}
					sleep(125);
				}
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (ds != null) {
					ds.close();
				}
			}
		//	Looper.loop();
			
		}

	}

	/**
	 * 修改飞机控制信号的线程
	 * */
	class ChangeCtrlMsgThread extends HandlerThread{

		
		private Handler handler;
		public Handler getHandler() {
			return handler;
		}

		public ChangeCtrlMsgThread(String name) {
			super(name);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Looper.prepare();
			handler = new Handler(){

				@Override
				public void handleMessage(Message msg) {
					// TODO Auto-generated method stub
					super.handleMessage(msg);
					synchronized (controlMsg) {
						controlMsg="1";
					}
				}
				
			};
			Looper.loop();
			super.run();
		}
		
	}
	/**
	 * Modification The thread to open camera
	 **/
	class OpenCameraThread extends HandlerThread {

		private Socket socket = null;

		public OpenCameraThread(String name) {
			super(name);
		}

		@Override
		public void run() {
	
			
			
			Log.i(TAG, "[OpenCameraThread] thread ID: "
					+ Thread.currentThread().getId());

			displayDialog("正在连接摄像头 IP: " + ipstr);

			// TODO Auto-generated method stub
			try {

				if (socket == null) {
					socket = new Socket();
					socket.connect(
							new InetSocketAddress(ipstr, UDP_SERVER_PORT),
							30000);
				}
				// 创建连接的套接字，设置服务器连接的超时时间

				Log.i(TAG, "[OpenCameraThread]socket created ，thread ID:"
						+ Thread.currentThread().getId());
				if (socket.isConnected()) {

					Log.i(TAG, "[OpenCameraThread]Host connected");
					OutputStream outputStream;
					try {
						byte[] enableCamera = { (byte) 0x5a, (byte) 0xa5,
								(byte) 0xc3, (byte) 0x3c };
						outputStream = socket.getOutputStream();
						outputStream.write(enableCamera);
						outputStream.close();
						socket.close();
						synchronized (cameraOpenFlagBoolean) {
							cameraOpenFlagBoolean = true;
						}
						Log.i(TAG, "[OpenCameraThread]Command sent");
						dismissDialog();
						displayDialog("摄像头已打开 IP: " + ipstr);

						// 讲打开摄像头的字节码指令发送给摄像头
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					dismissDialog();
					displayDialog("服务器连接失败");
					resetButtonStatus();
				}
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
				dismissDialog();
				displayDialog("服务器连接超时");
				resetButtonStatus();

			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				dismissDialog();
				displayDialog("服务器连接失败");
				resetButtonStatus();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				dismissDialog();
				displayDialog("摄像头指令发送失败");
				resetButtonStatus();
			}
			
		}
	}

	/** 
	 * 关闭摄像头的线程 
	 * */
	class CloseCameraThread extends HandlerThread {

		public CloseCameraThread(String name) {
			super(name);
		}
		@Override
		public void run() {

			synchronized (cameraOpenFlagBoolean) {
				cameraOpenFlagBoolean = false;
			}
		}

	}
	
	/**
	 * 接收图像的线程
	 * */

	class ImageReceiveThread extends HandlerThread {

		private Socket socket = null;
		BufferedInputStream inputStream = null;

		public ImageReceiveThread(String name) {
			super(name);
		}

		@Override
		public void run() {
			Log.i(TAG, "[ImageReceiveThread]Current thread id: "
					+ Thread.currentThread().getId());

			try {
				if (socket == null) {
					socket = new Socket();
					socket.connect(
							new InetSocketAddress(ipstr, UDP_SERVER_PORT),
							30000);
					socket.setSoTimeout(10000);
				}
				// 创建连接的套接字，设置服务器连接的超时时间，以及每次读取的超时时间

				if (socket.isConnected()) {
					dismissDialog();
					displayDialog("开始传输视频");
				}

				while (socket.isConnected() && cameraOpenFlagBoolean 
						&& imageRecenable) {
				
					if (socket.getInputStream().available() < 4) {
						continue;
					}
					// socket在没有接收齐4个字节的数据之前退出循环

					int bodyLength = 0;
					bodyLength = getBodylength(socket.getInputStream());
					// 获取本次接受到的jpg图片的长度

					if (bodyLength > 0 && bodyLength < 65535) {

						int sum = 0;
						int increment = 4;
						InputStream inputStream = socket.getInputStream();
						byte[] buffer = new byte[bodyLength];
						
						
						inputStream.read(buffer,sum,increment);
						sum+=increment;
						
						if(buffer[0]!=-1 ||
								buffer[1]!=-40){						
							Log.i(TAG, "kek");
							continue;
						}
						
						while (sum < bodyLength) {
							inputStream.read(buffer, sum, increment);
							sum += increment;
						}

						inputStream.read(buffer, sum, bodyLength - sum);

						// 从流中读取数据到缓冲区当中

						Message message = new Message();
						message.what = REFRESH_VIEW;
						message.obj = buffer;
						message.arg1 = bodyLength;
						myHandler.sendMessage(message);
						// 将接受到的数据发送给handler提交给UI线程刷新UI
					}
				}
				if (cameraOpenFlagBoolean == false && socket.isConnected()) {

					byte[] disbleCamera = { (byte) 0xaa, (byte) 0x55,
							(byte) 0xcc, (byte) 0x33 };

					socket.shutdownInput();
					OutputStream outputStream = socket.getOutputStream();
					outputStream.write(disbleCamera);
					outputStream.close();
					socket.close();

					Message message = new Message();
					message.what = REFRESH_VIEW;
					byte[] r = { (byte) 0xff };
					message.obj = r;
					message.arg1 = 1;
					myHandler.sendMessage(message);
					// 将View的图像清除

					displayDialog("连接已关闭");
				}
			} catch (SocketTimeoutException e) {
				// TODO: handle exception
				dismissDialog();
				displayDialog("服务器连接超时");
				sendTCPException(datagramSocket);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				dismissDialog();
				displayDialog("主机地址错误");
				sendTCPException(datagramSocket);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				dismissDialog();
				displayDialog("读写数据时出现问题");
				sendTCPException(datagramSocket);

			}catch(ArrayIndexOutOfBoundsException e){ 
				e.printStackTrace();
				dismissDialog();
				displayDialog("数组溢出");
				sendTCPException(datagramSocket);
			}
			finally {
				try {
					socket.close();
					resetButtonStatus();
				} catch (IOException e) {
					e.printStackTrace();
					dismissDialog();
					displayDialog("关闭主机时出现问题");
					resetButtonStatus();
					sendTCPException(datagramSocket);
				}
			}
		}
	}

	
	class ControlInfoReceiveThread extends HandlerThread{

		DatagramSocket datagramSocket=null;
		
		public ControlInfoReceiveThread(String name,DatagramSocket ds) {
			super(name);
			// TODO Auto-generated constructor stub
			datagramSocket=ds;
			
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub

			try {
				
				sleep(500);
				
				if(datagramSocket==null){
					
					datagramSocket = new DatagramSocket(UDP_SERVER_PORT_LOCAL);
					
				}
				byte[] controlDataByte = new byte[128];
				datagramSocket.setSoTimeout(200);
				datagramSocket.setBroadcast(true);
				DatagramPacket datagramPacket = new DatagramPacket(
						controlDataByte, controlDataByte.length);
				InetAddress address=InetAddress.getByName(ipstr);
				datagramSocket.connect(address, UDP_SERVER_PORT);
				
				
				Log.i(TAG,""+datagramSocket.getLocalPort()+" "+
				datagramSocket.getLocalSocketAddress());
				

				Log.i(TAG, "[UDPSOCKET]is connected "+datagramSocket.getRemoteSocketAddress()+datagramSocket.isConnected());
				while (ctrlInfoThreadEnable==true && datagramSocket.isConnected()) {				
					try {
						Log.i(TAG, "[UDPSOCKET] in to the while");
					//	multicastLock.acquire();
						datagramSocket.receive(datagramPacket);
					//	multicastLock.release();

						String dataString = new String(datagramPacket.getData(),
								datagramPacket.getOffset(),
								datagramPacket.getLength());
						Log.i(TAG, "[UDPreceive]"+dataString);
					}catch(SocketTimeoutException e){
						//Log.i(TAG, "[UDPSOCKET]timeout");
					}
					catch (IOException e) {
						// TODO: handle exception
					//	Log.i(TAG, "[UDPreceive]"+e);
					}catch (Exception e) {
						// TODO: handle exception
					}finally{
					//	multicastLock.release();

					}
				}
				
				if(datagramSocket!=null)datagramSocket.close();
			}
			catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}finally{
				if(datagramSocket!=null)datagramSocket.close();
			}
			super.run();
		}
		
		
	}
	class MyHandler extends Handler {
		public MyHandler() {

		}

		public MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg.what == REFRESH_VIEW) {
				byte[] buffer = (byte[]) msg.obj;
				int bodyLength = msg.arg1;
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inSampleSize = 2;
				options.inMutable=true;
				options.inPreferredConfig=Bitmap.Config.RGB_565;
				Bitmap bitmap = BitmapFactory.decodeByteArray(buffer, 0,
						bodyLength, options);
				
			/*	
				DetectFaceAsyncTask detectFaceAsyncTask=new DetectFaceAsyncTask();
				detectFaceAsyncTask.setBitmap(bitmap);
				detectFaceAsyncTask.execute();
				*/
				imageViewVideo.setImageBitmap(bitmap);
				
				// 将从线程中获取的数据展示在UI的imageview当中。
			} else if (msg.what == DISPLY_DIALOG) {
				String messageString = (String) msg.obj;
				dialog = new AlertDialog.Builder(ControlActivity.this).setMessage(
						messageString).create();
				dialog.show();
				// 在Dialog中显示消息
			} else if (msg.what == DISMISS_DIALOG) {
				if (dialog != null) {
					dialog.dismiss();
				}
				// 将DialogDismiss
			}else if (msg.what==RESET_BUTTON_STATUS){
				btnCloseCamera.setVisibility(View.GONE);
				btnOpenCamera.setVisibility(View.VISIBLE);
				btnStartReceive.setVisibility(View.GONE);	
				//重置按钮状态
			}
		}
	}

	/**
	 * 在子线程中将消息发送给UI线程显示Diaglog
	 * 
	 * @param messageString
	 *            String 需要在dialog中显示的消息内容
	 * **/
	private void displayDialog(String messageString) {
		Message message = new Message();
		message.what = DISPLY_DIALOG;
		message.obj = messageString;
		myHandler.sendMessage(message);
	}

	/**
	 * 将主线程的dialog清除
	 */
	private void dismissDialog() {
		Message message = new Message();
		message.what = DISMISS_DIALOG;
		myHandler.sendMessage(message);
	}
	
	/**
	 * 重置按钮布局
	 */
	private void resetButtonStatus() {
		Message message = new Message();
		message.what = RESET_BUTTON_STATUS;
		myHandler.sendMessage(message);
	}


	/**
	 * 在输入流中获取下一张图片的图片长度
	 * 
	 * @return bodyLength int 返回图片长度
	 * @param inputStream
	 *            InputStream 指定的输入流
	 * */
	private int getBodylength(InputStream inputStream) {
		byte[] buffer = new byte[4];
		try {
			inputStream.read(buffer, 0, 4);
			// 读取接受到的输入流的前四个字节
			ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
			byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			int bodyLength = byteBuffer.getInt();
			// 将接受到的前四个字节以小端储存的形式解析为整形获取本体长度。
			if (bodyLength < 0 || bodyLength > 65535) {
				// return getBodylength(inputStream);
			}
			// 处理获取数据流长度失败的情况
			return bodyLength;
			// 返回获取当前字节流表示的图片的长度
			// Log.i(TAG,""+bodyLength);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.i(TAG, "length error");
			return -1;
		}

	}

	/**
	 * 使用UDP向开发板发送信号
	 * 
	 * @param command
	 *            String 需要发送的信号
	 * */
	private void sendUDPCommand(final String command,final DatagramSocket datagramSocket) {

		new Thread() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				String udpMsg = command;
				DatagramSocket ds = datagramSocket;
				try {
					if (ds == null) {
						ds = new DatagramSocket(UDP_SERVER_PORT_LOCAL);
					}
					InetAddress serverAddr = InetAddress.getByName(ipstr);
					DatagramPacket dp;
					dp = new DatagramPacket(udpMsg.getBytes(), udpMsg.length(),
							serverAddr, UDP_SERVER_PORT);
					ds.send(dp);
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (ds != null) {
						ds.close();
					}
				}
				super.run();
			}

		}.start();
		;
	}
	
	/**
	 * 使用UDP向开发板发送信号
	 * 
	 * @param data
	 *            byte[] 需要发送的字节码
	 * */
	private void sendUDPCommand(final byte[] data,final DatagramSocket datagramSocket) {

		new Thread() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
			//	String udpMsg = command;
				DatagramSocket ds = datagramSocket;
				try {
					if(ds==null){
						
						ds = new DatagramSocket(UDP_SERVER_PORT_LOCAL);
					}
					InetAddress serverAddr = InetAddress.getByName(ipstr);
					DatagramPacket dp;
					dp = new DatagramPacket(data, data.length,
							serverAddr, UDP_SERVER_PORT);
					ds.send(dp);
				} catch (SocketException e) {
					e.printStackTrace();
				} catch (UnknownHostException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (ds != null) {
						ds.close();
					}
				}
				super.run();
			}

		}.start();
		;
	}

	/**
	 * 使用UDP向开发板发送TCP错误
	 * 
	 * */
	private void sendTCPException(DatagramSocket datagramSocket) {
		String udpMsg = "TCP_EXCEPTION";
		DatagramSocket ds = null;
		
		ds=datagramSocket;
		try {
			if(ds==null)
				{ds = new DatagramSocket(UDP_SERVER_PORT_LOCAL);}
			InetAddress serverAddr = InetAddress.getByName(ipstr);
			DatagramPacket dp;
			dp = new DatagramPacket(udpMsg.getBytes(), udpMsg.length(),
					serverAddr, UDP_SERVER_PORT);
			ds.send(dp);
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (ds != null) {
				ds.close();
			}
		}
	}
	/**
	 * 改变线程的标记位关闭相应的线程
	 * */
	private void closeControlThreads(){
		synchronized (imageRecenable) {
			imageRecenable = false;
		}
		synchronized (HeartBeatThreadEnable) {
			HeartBeatThreadEnable=false;
		}
		synchronized (ctrlInfoThreadEnable) {
			ctrlInfoThreadEnable=false;
		}
	}

	/** 获取SD卡路径 */
	public String getSDPath() {
		File sdDir = null;
		boolean sdCardExist = Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
		if (sdCardExist) {
			sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
		}
		return sdDir.toString();

	}
	
	/**
	 * 
	 * 返回主页
	 *  
	 * */
	private void backToPage(){
		CloseCameraThread cameraThread = new CloseCameraThread(
				"CLOSE_CAMERATHREAD");
		cameraThread.start();
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Intent intent = new Intent();
		intent.setClass(this, MainActivity.class);
		startActivity(intent);
		finish();
	}
	
	/**
	 * 开启异步任务进行人脸识别*/
	
	private class DetectFaceAsyncTask extends AsyncTask<String, Integer, Integer>
    {
        private Bitmap bitmap;
        private Bitmap faceBitmap;
        private Bitmap maskBitmap;
        private static final int N_MAX=2;
       
        
        private DetectFaceAsyncTask()
        {
        }
        private DetectFaceAsyncTask(Bitmap bitmap)
        {
            this.bitmap = bitmap;
        }

        @Override
        protected Integer doInBackground(String... arg0)
        {
            return detectFace(this.bitmap);
        }

        @Override
        protected void onPostExecute(Integer faceCount)
        {
            super.onPostExecute(faceCount);
            if(faceCount>0){
            	imageViewMask.setImageBitmap(this.maskBitmap);
            	
            }
            else{
            	
            	imageViewMask.setImageResource(R.drawable.bg_mask);

            }
            //将方框绘制在MASK层上面
            Toast.makeText(getApplicationContext(), "检测到人脸: " + faceCount, Toast.LENGTH_SHORT)
                    .show();
            
            
        }
        
        public void setBitmap(Bitmap bmp){
        	this.bitmap=bmp;
        }

        
        /**
         * 步任务处理图像的具体过程
         * @param bitmap Bitmap将需要被识别的图片加载进异步任务当中
         *  */
        
        private int detectFace(Bitmap bitmap)
        {
            Log.i(TAG, "Begin face detect");
            if (bitmap == null)
            {
               //bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test);
            	return 0;
            }

            this.faceBitmap = bitmap.copy(Config.RGB_565, true);
            int width = this.faceBitmap.getWidth();
            int height = this.faceBitmap.getHeight();
            Log.i(TAG, "待检测图像: w = " + width + ", h = " + height);
           
            int detectedFaceCount = 0;
            
            Log.i(TAG, "Start face detect");
            FaceDetector.Face[] faces = new FaceDetector.Face[N_MAX];
            FaceDetector faceDetector = new FaceDetector(width, height, N_MAX);
            detectedFaceCount = faceDetector.findFaces(this.faceBitmap, faces);
            Log.i(TAG, "检测到人脸：" + detectedFaceCount);
            Log.i(TAG, "Stop face detect");
            
            drawFaces(faces, detectedFaceCount);
            
            Log.i(TAG, "End face detect");

            return detectedFaceCount;
        }
        
        /**
         * 在检测到的人脸上面绘制方框
         * */

        private void drawFaces(FaceDetector.Face[] faces, int detectedFaceCount)
        {
            for (int i = 0; i < detectedFaceCount; i++)
            {
            	Resources res=getResources();               
            	BitmapFactory.Options options = new BitmapFactory.Options();
            	options.inMutable=true;
            	maskBitmap=BitmapFactory.decodeResource(res, R.drawable.bg_mask,options);
            	
            	float scalex =(float) (1920.0/faceBitmap.getWidth());
            	float scaley =(float) (1080.0/faceBitmap.getHeight());

                Face f = faces[i];
                PointF midPoint = new PointF();
                float dis = f.eyesDistance();
                f.getMidPoint(midPoint);
                int dd = (int) (dis);
                Point eyeLeft = new Point((int) ((midPoint.x - dis / 2)*scalex), (int) (midPoint.y*scaley));
                Point eyeRight = new Point((int) ((midPoint.x + dis / 2)*scalex), (int) ((midPoint.y)*scaley));
                Rect faceRect = new Rect((int) ((midPoint.x - dd)*scalex), (int)( (midPoint.y - dd)*scaley),
                        (int) ((midPoint.x + dd)*scalex), (int) ((midPoint.y + dd)*scaley));
                Log.i(TAG, scalex+" "+scaley + "左眼坐标 x = " + eyeLeft.x + ", y = " + eyeLeft.y);

                //在画面遮罩层上绘制方框
                Canvas canvas = new Canvas(maskBitmap);
                Paint p = new Paint();
                p.setAntiAlias(true);
                p.setStrokeWidth(8);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(Color.GREEN);
                canvas.drawCircle(eyeLeft.x, eyeLeft.y, 20, p);
                canvas.drawCircle(eyeRight.x, eyeRight.y, 20, p);
                canvas.drawRect(faceRect, p);
            }
        }
    }

	@SuppressLint("NewApi")
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (version >= Build.VERSION_CODES.KITKAT && hasFocus) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}
	}

}