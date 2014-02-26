package net.londatiga.android.bluebamboo;

import net.londatiga.android.bluebamboo.R;
import net.londatiga.android.bluebamboo.pockdata.PocketPos;

import net.londatiga.android.bluebamboo.util.DateUtil;
import net.londatiga.android.bluebamboo.util.FontDefine;
import net.londatiga.android.bluebamboo.util.Printer;
import net.londatiga.android.bluebamboo.util.StringUtil;
import net.londatiga.android.bluebamboo.util.Util;
import net.londatiga.android.bluebamboo.util.DataConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;

import android.os.Build;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;

/**
 * Demo Blue Bamboo P25 Thermal Printer.
 * 
 * @author Lorensius W. L. T <lorenz@londatiga.net>
 *
 */
public class MainActivity extends Activity {
	private Button mConnectBtn;
	private Button mEnableBtn;
	private Button mPrintDemoBtn;
	private Button mPrintBarcodeBtn;
	private Button mPrintImageBtn;
	private Button mPrintReceiptBtn;
	private Button mPrintTextBtn;
	private Spinner mDeviceSp;	
	
	private ProgressDialog mProgressDlg;
	private ProgressDialog mConnectingDlg;
	
	private BluetoothAdapter mBluetoothAdapter;
	
	private P25Connector mConnector;
	
	private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<BluetoothDevice>();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);		
		
		mConnectBtn			= (Button) findViewById(R.id.btn_connect);
		mEnableBtn			= (Button) findViewById(R.id.btn_enable);
		mPrintDemoBtn 		= (Button) findViewById(R.id.btn_print_demo);
		mPrintBarcodeBtn 	= (Button) findViewById(R.id.btn_print_barcode);
		mPrintImageBtn 		= (Button) findViewById(R.id.btn_print_image);
		mPrintReceiptBtn 	= (Button) findViewById(R.id.btn_print_receipt);
		mPrintTextBtn		= (Button) findViewById(R.id.btn_print_text);
		mDeviceSp 			= (Spinner) findViewById(R.id.sp_device);
		
		mBluetoothAdapter	= BluetoothAdapter.getDefaultAdapter();
				
		if (mBluetoothAdapter == null) {
			showUnsupported();
		} else {
			if (!mBluetoothAdapter.isEnabled()) {
				showDisabled();
			} else {
				showEnabled();
				
				Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
				
				if (pairedDevices != null) {
					mDeviceList.addAll(pairedDevices);
					
					updateDeviceList();
				}
			}
			
			mProgressDlg 	= new ProgressDialog(this);
			
			mProgressDlg.setMessage("Scanning...");
			mProgressDlg.setCancelable(false);
			mProgressDlg.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
			    @Override
			    public void onClick(DialogInterface dialog, int which) {
			        dialog.dismiss();
			        
			        mBluetoothAdapter.cancelDiscovery();
			    }
			});
			
			mConnectingDlg 	= new ProgressDialog(this);
			
			mConnectingDlg.setMessage("Connecting...");
			mConnectingDlg.setCancelable(false);
			
			mConnector 		= new P25Connector(new P25Connector.P25ConnectionListener() {
				
				@Override
				public void onStartConnecting() {
					mConnectingDlg.show();
				}
				
				@Override
				public void onConnectionSuccess() {
					mConnectingDlg.dismiss();
					
					showConnected();
				}
				
				@Override
				public void onConnectionFailed(String error) {
					mConnectingDlg.dismiss();
				}
				
				@Override
				public void onConnectionCancelled() {
					mConnectingDlg.dismiss();
				}
				
				@Override
				public void onDisconnected() {
					showDisonnected();
				}
			});
			
			//enable bluetooth
			mEnableBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View v) {					
					Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
						
					startActivityForResult(intent, 1000);					
				}
			});
			
			//connect/disconnect
			mConnectBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View arg0) {
					connect();			
				}
			});
			
			//print demo text
			mPrintDemoBtn.setOnClickListener(new View.OnClickListener() {			
				@Override
				public void onClick(View v) {
					printDemoContent();
				}
			});
			
			//print text
			mPrintTextBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View arg0) {
					InputTextDialog dialog = new InputTextDialog(MainActivity.this, new InputTextDialog.OnOkClickListener() {				
						@Override
						public void onPrintClick(String text) {
							printText(text);
						}
					});
					
					dialog.show();
				}
			});
			
			//print image bitmap
			mPrintImageBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View arg0) {
					printImage();
				}
			});
			
			//print barcode 1D or 2D
			mPrintBarcodeBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View arg0) {
					printBarcode();
				}
			});
			
			//print struk
			mPrintReceiptBtn.setOnClickListener(new View.OnClickListener() {				
				@Override
				public void onClick(View arg0) {
					printStruk();
				}
			});
		}
		
		IntentFilter filter = new IntentFilter();
		
		filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		
		registerReceiver(mReceiver, filter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_scan) {
			mBluetoothAdapter.startDiscovery();
		}
		
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPause() {
		if (mBluetoothAdapter != null) {
			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}			
		}
		
		if (mConnector != null) {
			try {
				mConnector.disconnect();
			} catch (P25ConnectionException e) {
				e.printStackTrace();
			}
		}
		
		super.onPause();
	}
	
	@Override
	public void onDestroy() {
		unregisterReceiver(mReceiver);
		
		super.onDestroy();
	}
	
	private String[] getArray(ArrayList<BluetoothDevice> data) {
		String[] list = new String[0];
		
		if (data == null) return list;
		
		int size	= data.size();
		list		= new String[size];
		
		for (int i = 0; i < size; i++) {
			list[i] = data.get(i).getName();
		}
		
		return list;	
	}
	
	private void showToast(String message) {
		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
	}
	
	private void updateDeviceList() {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.simple_spinner_item, getArray(mDeviceList));
		
		adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item);
		
		mDeviceSp.setAdapter(adapter);
		mDeviceSp.setSelection(0);
	}
	
	private void showDisabled() {
		showToast("Bluetooth disabled");
		
		mEnableBtn.setVisibility(View.VISIBLE);		
		mConnectBtn.setVisibility(View.GONE);
		mDeviceSp.setVisibility(View.GONE);
	}
	
	private void showEnabled() {		
		showToast("Bluetooth enabled");
		
		mEnableBtn.setVisibility(View.GONE);		
		mConnectBtn.setVisibility(View.VISIBLE);
		mDeviceSp.setVisibility(View.VISIBLE);
	}
	
	private void showUnsupported() {
		showToast("Bluetooth is unsupported by this device");

		mConnectBtn.setEnabled(false);		
		mPrintDemoBtn.setEnabled(false);
		mPrintBarcodeBtn.setEnabled(false);
		mPrintImageBtn.setEnabled(false);
		mPrintReceiptBtn.setEnabled(false);
		mPrintTextBtn.setEnabled(false);
		mDeviceSp.setEnabled(false);
	}
	
	private void showConnected() {
		showToast("Connected");
		
		mConnectBtn.setText("Disconnect");
		
		mPrintDemoBtn.setEnabled(true);
		mPrintBarcodeBtn.setEnabled(true);
		mPrintImageBtn.setEnabled(true);
		mPrintReceiptBtn.setEnabled(true);
		mPrintTextBtn.setEnabled(true);
		
		mDeviceSp.setEnabled(false);
	}
	
	private void showDisonnected() {
		showToast("Disconnected");
		
		mConnectBtn.setText("Connect");
		
		mPrintDemoBtn.setEnabled(false);
		mPrintBarcodeBtn.setEnabled(false);
		mPrintImageBtn.setEnabled(false);
		mPrintReceiptBtn.setEnabled(false);
		mPrintTextBtn.setEnabled(false);
		
		mDeviceSp.setEnabled(true);
	}
	
	private void connect() {
		if (mDeviceList == null || mDeviceList.size() == 0) {
			return;
		}
		
		BluetoothDevice device = mDeviceList.get(mDeviceSp.getSelectedItemPosition());
		
		if (device.getBondState() == BluetoothDevice.BOND_NONE) {
			try {
				createBond(device);
			} catch (Exception e) {
				showToast("Failed to pair device");
				
				return;
			}
		}
		
		try {
			if (!mConnector.isConnected()) {
				mConnector.connect(device);
			} else {
				mConnector.disconnect();
				
				showDisonnected();
			}
		} catch (P25ConnectionException e) {
			e.printStackTrace();
		}
	}
	
	private void createBond(BluetoothDevice device) throws Exception { 
        
        try {
            Class<?> cl 	= Class.forName("android.bluetooth.BluetoothDevice");
            Class<?>[] par 	= {};
            
            Method method 	= cl.getMethod("createBond", par);
            
            method.invoke(device);
            
        } catch (Exception e) {
            e.printStackTrace();
            
            throw e;
        }
    }
	
	private void sendData(byte[] bytes) {
		try {
			mConnector.sendData(bytes);
		} catch (P25ConnectionException e) {
			e.printStackTrace();
		}
	}
	
	private void printStruk() {
		String titleStr	= "STRUK PEMBAYARAN TAGIHAN LISTRIK" + "\n\n";
		
		StringBuilder contentSb	= new StringBuilder();
		
		contentSb.append("IDPEL     : 435353535435353" + "\n");
		contentSb.append("NAMA      : LORENSIUS WLT" + "\n");
		contentSb.append("TRF/DAYA  : 50/12244 VA" + "\n");
		contentSb.append("BL/TH     : 02/14" + "\n");
		contentSb.append("ST/MTR    : 0293232" + "\n");
		contentSb.append("RP TAG    : Rp. 100.000" + "\n");
		contentSb.append("JPA REF   :" + "\n");
		
		StringBuilder content2Sb = new StringBuilder();
		
		content2Sb.append("ADM BANK  : Rp. 1.600" + "\n");
		content2Sb.append("RP BAYAR  : Rp. 101.600,00" + "\n");
		
		String jpaRef	= "XXXX-XXXX-XXXX-XXXX" + "\n";
		String message	= "PLN menyatakan struk ini sebagai bukti pembayaran yang sah." + "\n";
		String message2	= "Rincian tagihan dapat diakses di www.pln.co.id Informasi Hubungi Call Center: "
						+ "123 Atau Hub PLN Terdekat: 444" + "\n";
		
		long milis		= System.currentTimeMillis();
		String date		= DateUtil.timeMilisToString(milis, "dd-MM-yy / HH:mm")  + "\n\n";
		
		byte[] titleByte	= Printer.printfont(titleStr, FontDefine.FONT_24PX,FontDefine.Align_CENTER, 
								(byte)0x1A, PocketPos.LANGUAGE_ENGLISH);
		
		byte[] content1Byte	= Printer.printfont(contentSb.toString(), FontDefine.FONT_24PX,FontDefine.Align_LEFT, 
								(byte)0x1A, PocketPos.LANGUAGE_ENGLISH);
		
		byte[] refByte		= Printer.printfont(jpaRef, FontDefine.FONT_24PX,FontDefine.Align_CENTER,  (byte)0x1A, 
								PocketPos.LANGUAGE_ENGLISH);
		
		byte[] messageByte	= Printer.printfont(message, FontDefine.FONT_24PX,FontDefine.Align_CENTER,  (byte)0x1A, 
								PocketPos.LANGUAGE_ENGLISH);
		
		byte[] content2Byte	= Printer.printfont(content2Sb.toString(), FontDefine.FONT_24PX,FontDefine.Align_LEFT,  
								(byte)0x1A, PocketPos.LANGUAGE_ENGLISH);
		
		byte[] message2Byte	= Printer.printfont(message2, FontDefine.FONT_24PX,FontDefine.Align_CENTER,  (byte)0x1A, 
								PocketPos.LANGUAGE_ENGLISH);
		
		byte[] dateByte		= Printer.printfont(date, FontDefine.FONT_24PX,FontDefine.Align_LEFT, (byte)0x1A, 
								PocketPos.LANGUAGE_ENGLISH);
		
		byte[] totalByte	= new byte[titleByte.length + content1Byte.length + refByte.length + messageByte.length +
		                	           content2Byte.length + message2Byte.length + dateByte.length];
		
		
		int offset = 0;
		System.arraycopy(titleByte, 0, totalByte, offset, titleByte.length);
		offset += titleByte.length;
		
		System.arraycopy(content1Byte, 0, totalByte, offset, content1Byte.length);
		offset += content1Byte.length;

		System.arraycopy(refByte, 0, totalByte, offset, refByte.length);
		offset += refByte.length;
		
		System.arraycopy(messageByte, 0, totalByte, offset, messageByte.length);
		offset += messageByte.length;
		
		System.arraycopy(content2Byte, 0, totalByte, offset, content2Byte.length);
		offset += content2Byte.length;
		
		System.arraycopy(message2Byte, 0, totalByte, offset, message2Byte.length);
		offset += message2Byte.length;
		
		System.arraycopy(dateByte, 0, totalByte, offset, dateByte.length);
		
		byte[] senddata = PocketPos.FramePack(PocketPos.FRAME_TOF_PRINT, totalByte, 0, totalByte.length);

		sendData(senddata);	
	}
	
	private void printDemoContent(){
		   
		/*********** print head*******/
		String receiptHead = "************************" 
				+ "   P25/M Test Print"+"\n"
				+ "************************"
				+ "\n";
		
		long milis		= System.currentTimeMillis();
		
		String date		= DateUtil.timeMilisToString(milis, "MMM dd, yyyy");
		String time		= DateUtil.timeMilisToString(milis, "hh:mm a");
		
		String hwDevice	= Build.MANUFACTURER;
		String hwModel	= Build.MODEL;
		String osVer	= Build.VERSION.RELEASE;
		String sdkVer	= String.valueOf(Build.VERSION.SDK_INT);
		
		StringBuffer receiptHeadBuffer = new StringBuffer(100);
		
		receiptHeadBuffer.append(receiptHead);
		receiptHeadBuffer.append(Util.nameLeftValueRightJustify(date, time, DataConstants.RECEIPT_WIDTH) + "\n");
		
		receiptHeadBuffer.append(Util.nameLeftValueRightJustify("Device:", hwDevice, DataConstants.RECEIPT_WIDTH) + "\n");
		
		receiptHeadBuffer.append(Util.nameLeftValueRightJustify("Model:",  hwModel, DataConstants.RECEIPT_WIDTH) + "\n");
		receiptHeadBuffer.append(Util.nameLeftValueRightJustify("OS ver:", osVer, DataConstants.RECEIPT_WIDTH) + "\n");
		receiptHeadBuffer.append(Util.nameLeftValueRightJustify("SDK:", sdkVer, DataConstants.RECEIPT_WIDTH));
		receiptHead = receiptHeadBuffer.toString();
		
		byte[] header = Printer.printfont(receiptHead + "\n", FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		
			
		/*********** print English text*******/
		StringBuffer sb = new StringBuffer();
		for(int i=1; i<128; i++)
			sb.append((char)i);
		String content = sb.toString().trim();
		
		byte[] englishchartext24 			= Printer.printfont(content + "\n",FontDefine.FONT_24PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		byte[] englishchartext32			= Printer.printfont(content + "\n",FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		byte[] englishchartext24underline	= Printer.printfont(content + "\n",FontDefine.FONT_24PX_UNDERLINE,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		
		//2D Bar Code
		byte[] barcode = StringUtil.hexStringToBytes("1d 6b 02 0d 36 39 30 31 32 33 34 35 36 37 38 39 32");
		
		
		/*********** print Tail*******/
		String receiptTail =  "Test Completed" + "\n"
				+ "************************" + "\n";
		
		String receiptWeb =  "** www.londatiga.net ** " + "\n\n\n";
		
		byte[] foot = Printer.printfont(receiptTail,FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		byte[] web	= Printer.printfont(receiptWeb,FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		
		byte[] totladata =  new byte[header.length + englishchartext24.length + englishchartext32.length + englishchartext24underline.length + 
		                              + barcode.length
		                             + foot.length + web.length
		                             ];
	 	int offset = 0;
		System.arraycopy(header, 0, totladata, offset, header.length);
		offset += header.length;
		
		System.arraycopy(englishchartext24, 0, totladata, offset, englishchartext24.length);
		offset+= englishchartext24.length;
		
		System.arraycopy(englishchartext32, 0, totladata, offset, englishchartext32.length);
		offset+=englishchartext32.length;
		
		System.arraycopy(englishchartext24underline, 0, totladata, offset, englishchartext24underline.length);
		offset+=englishchartext24underline.length;
		
		System.arraycopy(barcode, 0, totladata, offset, barcode.length);
		offset+=barcode.length;

		System.arraycopy(foot, 0, totladata, offset, foot.length);
		offset+=foot.length;
		
		System.arraycopy(web, 0, totladata, offset, web.length);
		offset+=web.length;
		
		byte[] senddata = PocketPos.FramePack(PocketPos.FRAME_TOF_PRINT, totladata, 0, totladata.length);

		sendData(senddata);		
	}
	
	private void printText(String text) {
		byte[] line 	= Printer.printfont(text + "\n\n", FontDefine.FONT_32PX, FontDefine.Align_CENTER, (byte) 0x1A, 
							PocketPos.LANGUAGE_ENGLISH);
		byte[] senddata = PocketPos.FramePack(PocketPos.FRAME_TOF_PRINT, line, 0, line.length);

		sendData(senddata);		
	}
	
	private void print1DBarcode() {
		String content	= "6901234567892";
	
		//1D barcode format (hex): 1d 6b 02 0d + barcode data
		
		byte[] formats	= {(byte) 0x1d, (byte) 0x6b, (byte) 0x02, (byte) 0x0d};
		byte[] contents	= content.getBytes();
		
		byte[] bytes	= new byte[formats.length + contents.length];
		
		System.arraycopy(formats, 0, bytes, 0, formats.length);
		System.arraycopy(contents, 0, bytes, formats.length, contents.length);
		
		sendData(bytes);
		
		byte[] newline 	= Printer.printfont("\n\n",FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		
		sendData(newline);
	}
	
	private void print2DBarcode() {
		String content 	= "Lorenz Blog - www.londatiga.net";
		
		//2D barcode format (hex) : 1d 6b 10 00 00 00 00 00 1f + barcode data
		
		byte[] formats	= {(byte) 0x1d, (byte) 0x6b, (byte) 0x10, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, 
						   (byte) 0x00, (byte) 0x1f};
		byte[] contents	= content.getBytes();
		byte[] bytes	= new byte[formats.length + contents.length];
		
		System.arraycopy(formats, 0, bytes, 0, formats.length);
		System.arraycopy(contents, 0, bytes, formats.length, contents.length);
		
		sendData(bytes);
		
		byte[] newline 	= Printer.printfont("\n\n",FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
		
		sendData(newline);
	}
	
	private void printImage() {
		try {		
			//image must be in monochrome bitmap
			//format: 1B 58 31 0B 30 + image data
			//where = 1B 58 31 = image format
			//   0B 30 = width x height (tes.bmp: 84x48 pixel)
			//   0B = image width/8 -> 84/8 = 11 (in decimal) -> 0B (in hex)
			//   30 = image height = 48 (in decimal) -> 30 in hexa
			//see: http://bluebamboo.helpserve.com/index.php?/Knowledgebase/Article/View/48
			
			byte[] formats	= {(byte) 0x1B, (byte) 0x58, (byte) 0x31, (byte) 0x13, (byte) 0x8D};
			//byte[] image 	= FileOperation.getBytesFromAssets(MainActivity.this, "tes.bmp");
			byte[] image	= StringUtil.hexStringToBytes("FF FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 A8 00 00 00 14 00 00 00 00 00 00 00 00 7F FF FF 9F DF E0 03 FE 0F F0 01 FF 80 00 00 00 00 00 00 00 6A D0 DB B5 7B 60 0E 03 8C B0 03 41 C0 00 00 00 00 00 00 00 40 E0 C0 70 30 20 0C 00 C8 08 06 00 30 00 00 00 00 00 00 00 60 60 C0 70 70 30 18 00 68 18 0C 00 30 00 00 00 00 00 00 00 40 C0 C0 E0 30 20 10 00 68 18 0C 00 10 00 00 00 00 00 00 00 C0 60 60 60 60 20 30 20 38 10 08 18 18 00 00 00 00 00 00 00 40 E0 E0 00 30 60 10 30 2C 18 08 18 18 00 00 00 00 00 00 00 40 40 E0 00 70 20 30 70 38 10 08 1C 08 00 00 00 00 00 00 00 40 E0 60 00 60 20 30 30 28 18 08 18 18 00 00 01 C0 00 00 00 C0 60 E0 00 70 22 20 30 38 10 18 18 08 00 00 02 00 00 00 00 40 E0 E0 60 F0 3F 30 30 38 1F 88 18 18 00 70 04 0C 00 00 00 C0 C0 E0 40 70 13 F0 30 38 05 C8 1C 0C 00 90 00 18 00 00 00 C0 00 B0 60 E0 00 E0 70 38 00 78 18 08 01 10 04 38 00 00 01 60 03 A0 60 70 00 70 30 18 00 38 18 18 06 90 04 30 00 00 01 40 03 B0 60 F0 00 60 30 38 00 18 18 0F F8 88 04 30 00 00 02 40 00 90 20 F0 20 70 30 38 08 38 1C 18 00 08 0C 20 00 00 02 40 C0 B0 60 A0 30 20 30 38 18 18 18 08 00 C4 08 70 00 00 0C E0 60 D0 01 B0 30 70 30 28 18 18 18 18 00 48 08 60 00 00 08 40 E0 50 60 B0 30 60 30 3C 0C 38 18 09 F0 60 18 E0 00 00 10 40 C0 D0 01 B0 30 70 70 28 1C 18 1C 0F 4A 92 20 00 00 00 30 60 60 D8 01 20 30 60 30 38 18 18 18 18 00 62 59 60 00 00 40 40 C0 90 01 B0 70 70 30 28 18 30 18 18 00 18 80 01 00 00 40 40 00 D8 01 30 00 70 00 38 00 1C 00 18 00 05 10 00 00 00 80 60 01 88 01 30 00 D8 00 68 00 3C 00 30 00 09 20 00 00 00 00 C0 01 98 03 30 00 D8 00 C8 00 66 00 30 06 06 20 18 00 03 03 62 0F 09 23 31 03 FE 01 CC 01 C7 80 E0 01 04 42 92 00 02 0C 7F FC 0F FE 3F FE FB EF 4F FF 81 D7 C0 00 8C 40 00 00 02 10 00 08 02 00 20 07 F9 7C 40 30 00 3D 00 00 0C 40 18 00 04 20 00 06 00 80 20 0F F8 00 40 00 00 10 00 00 08 C0 00 00 04 C0 C0 01 00 00 00 0F FD 00 40 10 00 20 00 00 00 00 3C 00 00 80 18 20 80 80 20 0F FD 00 40 10 00 40 00 00 08 40 00 00 03 80 04 40 C0 00 20 1F FF 00 40 10 00 80 00 00 08 41 CC 00 01 80 00 00 41 00 10 1F 8E 80 00 08 00 80 00 00 08 03 EF 00 01 C0 00 00 20 00 20 1F CF 00 60 00 01 00 04 00 04 83 F9 80 00 00 00 00 01 00 00 3F CF 40 00 0C 01 00 04 00 02 00 0F 80 00 40 00 00 18 00 10 3B DE C0 60 00 02 00 02 00 02 80 00 80 00 80 00 00 09 00 20 73 FE 80 00 06 02 00 02 00 02 00 80 40 00 00 00 00 00 00 00 73 FC 60 20 00 04 00 10 00 01 00 C0 20 00 80 00 00 09 00 20 71 FC 60 60 02 08 00 01 00 01 00 00 20 00 00 00 00 0A 00 20 F1 FC 00 00 02 10 00 00 00 01 00 00 40 00 80 00 00 04 00 11 F8 F8 20 20 00 10 0E 02 00 01 00 00 20 00 80 00 00 05 00 20 FA FC 00 20 01 00 31 80 00 01 00 00 60 01 00 04 01 04 00 21 FA B8 30 20 01 24 20 80 21 01 00 80 00 01 00 00 02 0A 00 21 FE 2A 30 20 00 40 40 5F C1 01 01 00 10 00 00 02 06 06 00 21 BE 41 10 20 00 C0 C0 60 00 01 00 01 10 01 00 00 09 03 00 02 9F 89 18 00 00 80 80 00 15 40 82 00 08 01 00 22 09 02 00 22 7F 80 40 20 00 83 00 40 20 00 C1 80 04 02 00 40 10 0D 00 24 7F C8 08 20 00 4E 00 40 80 00 20 20 02 02 00 08 01 51 00 24 FF D0 24 10 00 70 00 21 00 02 10 40 02 02 00 80 11 81 00 28 7F F0 14 20 00 00 00 40 00 00 10 00 02 02 00 00 20 80 80 48 77 F0 08 20 00 20 00 48 00 0C 08 00 01 02 03 E0 20 41 00 50 3F F0 02 A0 00 C0 00 83 00 39 48 40 01 06 1C 10 00 40 80 30 7E F0 06 10 01 00 00 80 A2 D0 8B C8 00 C8 00 08 20 21 00 60 3F E0 03 60 00 00 00 01 95 10 84 FF 00 18 00 04 20 31 00 40 3F E0 03 20 00 C0 00 81 27 F8 44 28 00 10 00 00 20 0E 00 40 17 E0 01 20 00 00 01 A3 04 00 20 80 00 20 00 00 20 02 00 40 1F C0 00 B0 00 20 02 14 0A 08 44 41 00 00 C0 00 C0 01 00 40 1F 80 01 80 00 C0 1B 09 05 08 40 00 00 40 82 08 90 01 00 80 8A E0 00 B0 01 00 E0 00 08 90 24 08 00 01 82 78 00 07 00 82 00 0C 00 A0 02 07 01 8A C5 10 04 00 00 8B 02 08 10 08 00 88 00 03 00 B0 04 38 02 11 09 08 18 10 00 84 04 0C 2E 04 00 90 00 00 C0 A0 1B C0 04 02 08 20 01 00 00 99 06 04 51 EC 01 20 00 00 50 60 7A 00 00 14 13 00 00 24 01 11 08 0C 50 0B 81 41 74 00 10 40 C0 00 04 00 10 10 01 10 01 00 1A 08 10 00 80 85 0B 00 08 A3 00 00 02 20 13 20 01 09 02 11 02 0A 10 00 71 08 00 C0 04 6C 00 00 02 46 30 10 00 02 02 11 0A 1A 00 00 0B 10 00 20 02 48 00 00 01 C2 22 20 00 00 02 31 02 10 10 00 04 30 00 10 00 50 00 00 00 04 21 00 00 00 00 21 0B 0A 10 00 06 40 00 08 01 E0 00 00 00 00 21 20 03 00 03 40 10 09 10 00 04 40 00 04 00 80 00 00 00 00 00 40 00 6C 00 80 99 01 10 00 04 00 00 02 00 80 00 00 00 04 20 00 00 02 00 01 09 09 00 00 04 01 00 02 18 80 00 00 00 00 60 00 00 10 00 01 01 99 10 00 00 07 B8 00 00 40 00 00 00 03 80 00 00 00 00 00 08 14 20 00 04 07 FC 00 08 40 00 00 00 00 00 00 00 40 00 01 08 84 C0 00 04 44 02 00 00 20 00 00 00 00 00 00 00 04 00 00 88 88 00 00 00 F0 02 18 00 20 00 00 00 00 00 00 00 00 00 00 B0 04 00 00 04 FC C1 78 00 20 00 00 00 00 00 00 01 10 00 00 40 88 00 00 04 FB BA FC 00 30 00 00 00 00 00 00 00 14 00 00 00 88 00 00 08 F0 10 FC 00 30 00 00 00 00 00 00 01 08 00 00 00 98 00 00 0C FE 03 FC 00 18 00 00 00 00 00 00 00 00 00 00 00 60 00 00 00 7F BF F8 00 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 3F FF C0 00 04 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 0F 7F 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 02 18 00 00 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 08 40 00 00 19 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 07 6D 00 00 5D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 0E 18 80 00 D9 00 00 00 00 00 00 00 00 00 00 00 00 00 00 18 00 01 00 00 F6 80 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 01 00 00 48 00 00 00 00 00 00 00 5D 00 00 00 00 00 00 10 08 02 00 00 E5 80 00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 00 00 70 80 00 00 00 00 00 01 00 00 00 00 00 00 00 10 02 00 00 00 FF 80 00 00 00 00 00 00 01 00 00 00 00 00 00 08 00 00 00 00 7F C0 00 00 00 00 00 00 08 00 00 00 00 00 00 10 50 00 01 00 FF 80 00 00 00 00 00 00 00 00 00 00 00 00 00 0A F0 02 F6 40 FF C0 00 00 00 00 00 00 00 00 00 00 00 00 00 19 E6 01 E7 00 FF 80 00 00 00 00 00 00 00 00 00 00 00 00 00 19 FC 00 FC 00 FF C0 00 00 00 00 00 00 00 00 00 00 00 00 00 38 10 00 30 00 FF E0 00 00 00 00 00 00 00 00 00 00 00 00 00 38 00 00 00 00 FF E0 00 00 00 00 00 00 00 00 00 00 00 00 00 38 00 00 00 01 FF E0 00 00 00 00 00 01 00 00 00 00 00 00 00 3C 10 00 00 01 FF C0 00 00 00 00 00 00 02 00 00 00 00 00 00 7F 78 00 00 33 FF E0 00 00 00 00 00 00 00 00 00 00 00 00 0B FF FF 6F EF FB FF F8 00 00 00 00 00 00 00 00 00 00 00 00 0F FF FF FF FF FB F2 78 00 00 00 00 00 00 00 00 00 00 00 00 0C 07 80 C0 70 0F 80 1C 00 00 00 00 00 00 00 00 00 00 00 00 08 07 00 C0 70 1F 00 0E 00 00 00 00 00 00 00 00 00 00 00 00 0C 07 80 C0 70 1E 00 03 00 00 00 00 00 00 1C 00 00 00 00 00 0C 07 01 C0 60 3C 00 03 00 00 00 00 00 03 00 00 00 00 00 00 0C 07 01 C0 60 1C 06 03 00 00 00 00 00 00 1E 00 00 00 00 00 0C 00 01 C0 60 3C 0E 03 00 00 00 00 00 00 60 00 00 00 00 00 04 00 01 C0 20 3C 06 03 00 00 00 00 00 01 C0 00 00 00 00 00 0E 00 01 80 60 7C 06 03 00 00 00 00 00 00 01 00 00 00 00 00 04 00 01 C0 40 78 06 01 00 00 00 00 00 00 00 00 00 00 00 00 06 00 03 C0 40 7C 06 03 80 00 00 00 00 00 0E 00 00 00 00 00 06 07 03 C0 00 7C 06 03 00 00 00 00 00 00 00 00 00 00 00 00 06 07 03 80 00 FC 06 03 00 00 00 00 00 00 00 00 00 00 00 00 06 07 03 C0 00 F8 06 03 00 00 00 00 00 00 00 00 00 00 00 00 06 02 03 C0 00 FC 07 03 00 00 00 00 00 00 00 00 00 00 00 00 02 07 03 C0 01 FC 06 03 00 00 00 00 00 03 00 00 00 00 00 00 06 02 07 80 00 FC 06 01 00 00 00 00 00 00 00 00 00 00 00 00 03 02 03 C0 01 F8 06 03 00 00 00 00 00 00 00 00 00 00 00 00 03 02 07 C0 00 FC 0E 03 00 00 00 00 00 00 00 00 00 00 00 00 03 02 07 C0 40 FC 06 03 00 00 00 00 00 00 00 00 00 00 00 00 03 02 07 80 40 FC 06 01 00 00 00 00 00 00 00 00 00 00 00 00 03 00 0E C0 00 78 06 03 80 00 00 00 00 01 00 00 00 00 00 00 03 00 07 C0 60 2C 06 01 00 00 00 00 00 02 03 00 00 00 00 00 03 00 04 40 60 7C 06 03 00 00 00 00 00 00 06 00 00 00 00 00 01 00 0C C0 60 3C 06 01 80 00 00 00 00 00 7C 00 00 00 00 00 01 80 0C C0 60 38 06 03 00 00 00 00 00 00 60 00 00 00 00 00 01 00 0C C0 70 3C 06 03 00 00 00 00 00 00 C0 00 00 00 00 00 01 80 0C 80 70 18 06 03 00 00 00 00 00 00 0C 00 00 00 00 00 01 80 0C 40 70 18 06 03 00 00 00 00 00 01 80 00 00 00 00 00 01 FE F8 FF FF DF EF DF 00 00 00 00 00 00 01 00 00 00 00 00 00 FF F8 FF EF FF FF FE 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00");
			byte[] bytes	= new byte[formats.length + image.length];

			System.arraycopy(formats, 0, bytes, 0, formats.length);
			System.arraycopy(image, 0, bytes, formats.length, image.length);
			
			//bluebamboo logo
			//byte[] bytes = {(byte)0x1B,(byte)0x58,(byte)0x31,(byte)0x24,(byte)0x2D,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x1B,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x39,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x38,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x7C,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0F,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x7E,(byte)0x20,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x10,(byte)0xC0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x3F,(byte)0x10,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x37,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x06,(byte)0x9F,(byte)0x88,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x25,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0C,(byte)0x4F,(byte)0xF0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x27,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x1E,(byte)0x27,(byte)0xE6,(byte)0x00,(byte)0x03,(byte)0xFF,(byte)0xFC,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xF8,(byte)0x7F,(byte)0xFF,(byte)0x1E,(byte)0x00,(byte)0x7D,(byte)0xFF,(byte)0xFE,(byte)0x0F,(byte)0xFF,(byte)0xC1,(byte)0xFF,(byte)0xF8,(byte)0x25,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x3F,(byte)0x93,(byte)0xCD,(byte)0x00,(byte)0x03,(byte)0xFF,(byte)0xFE,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0x7F,(byte)0xFF,(byte)0x9F,(byte)0x00,(byte)0x7D,(byte)0xFF,(byte)0xFF,(byte)0x1F,(byte)0xFF,(byte)0xE3,(byte)0xFF,(byte)0xFC,(byte)0x10,(byte)0xC0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x1F,(byte)0xC9,(byte)0x98,(byte)0x80,(byte)0x03,(byte)0xFF,(byte)0xFF,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xFF,(byte)0xFF,(byte)0x9F,(byte)0x00,(byte)0xFD,(byte)0xFF,(byte)0xFF,(byte)0x3F,(byte)0xFF,(byte)0xE3,(byte)0xFF,(byte)0xFE,(byte)0x0F,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xCF,(byte)0xE4,(byte)0x3C,(byte)0x60,(byte)0x03,(byte)0xC0,(byte)0x0F,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xFF,(byte)0xFF,(byte)0x9F,(byte)0x80,(byte)0xFD,(byte)0xFF,(byte)0xFF,(byte)0xBF,(byte)0xFF,(byte)0xF7,(byte)0xFF,(byte)0xFE,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0xA7,(byte)0xF2,(byte)0x3F,(byte)0x30,(byte)0x03,(byte)0x80,(byte)0x07,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0x00,(byte)0x1C,(byte)0xE0,(byte)0x03,(byte)0x9F,(byte)0x81,(byte)0xFD,(byte)0xC0,(byte)0x07,(byte)0xB8,(byte)0x00,(byte)0xF7,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0x93,(byte)0xFC,(byte)0x3F,(byte)0x98,(byte)0x03,(byte)0x80,(byte)0x07,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0x00,(byte)0x1C,(byte)0xE0,(byte)0x03,(byte)0x9F,(byte)0xC3,(byte)0xFD,(byte)0xC0,(byte)0x07,(byte)0xB8,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xC9,(byte)0xF9,(byte)0x9F,(byte)0xCC,(byte)0x03,(byte)0xFF,(byte)0xFE,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xE0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xE0,(byte)0x03,(byte)0x9F,(byte)0xC3,(byte)0xFD,(byte)0xFF,(byte)0xFF,(byte)0x38,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xE4,(byte)0x73,(byte)0x4F,(byte)0xE4,(byte)0x03,(byte)0xFF,(byte)0xFE,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xE0,(byte)0x07,(byte)0xFF,(byte)0xF8,(byte)0xE7,(byte)0xFF,(byte)0x9D,(byte)0xE7,(byte)0xBD,(byte)0xFF,(byte)0xFF,(byte)0x38,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xE2,(byte)0x72,(byte)0x27,(byte)0xFC,(byte)0x03,(byte)0xFF,(byte)0xFE,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xE0,(byte)0x07,(byte)0xFF,(byte)0xF8,(byte)0xE7,(byte)0xFF,(byte)0x9D,(byte)0xE7,(byte)0xBD,(byte)0xFF,(byte)0xFF,(byte)0x38,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0xF1,(byte)0x07,(byte)0x13,(byte)0xF8,(byte)0x03,(byte)0xFF,(byte)0xFF,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0xFF,(byte)0xE0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xE7,(byte)0xFF,(byte)0x9C,(byte)0xFF,(byte)0x3D,(byte)0xFF,(byte)0xFF,(byte)0x38,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0xF9,(byte)0x8F,(byte)0x89,(byte)0xF0,(byte)0x03,(byte)0xC0,(byte)0x07,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0x00,(byte)0x1E,(byte)0xE7,(byte)0xFF,(byte)0x9C,(byte)0xFF,(byte)0x3D,(byte)0xC0,(byte)0x07,(byte)0xB8,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xFF,(byte)0x8F,(byte)0xC4,(byte)0xE0,(byte)0x03,(byte)0x80,(byte)0x07,(byte)0x78,(byte)0x00,(byte)0x70,(byte)0x00,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0x00,(byte)0x1E,(byte)0xE0,(byte)0x03,(byte)0x9C,(byte)0x7E,(byte)0x3D,(byte)0xC0,(byte)0x03,(byte)0xB8,(byte)0x00,(byte)0x77,(byte)0x80,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x7E,(byte)0x27,(byte)0xE2,(byte)0x00,(byte)0x03,(byte)0xC0,(byte)0x07,(byte)0x78,(byte)0x00,(byte)0x78,(byte)0x01,(byte)0xEF,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0x00,(byte)0x1E,(byte)0xE0,(byte)0x03,(byte)0x9C,(byte)0x3E,(byte)0x3D,(byte)0xE0,(byte)0x07,(byte)0xBC,(byte)0x00,(byte)0xF7,(byte)0xC0,(byte)0x1E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x3C,(byte)0xD3,(byte)0xF1,(byte)0x00,(byte)0x03,(byte)0xFF,(byte)0xFF,(byte)0x7F,(byte)0xFF,(byte)0x3F,(byte)0xFF,(byte)0xEF,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xE0,(byte)0x03,(byte)0x9C,(byte)0x3C,(byte)0x3D,(byte)0xFF,(byte)0xFF,(byte)0xBF,(byte)0xFF,(byte)0xF3,(byte)0xFF,(byte)0xFE,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x19,(byte)0xC9,(byte)0xFA,(byte)0x00,(byte)0x03,(byte)0xFF,(byte)0xFF,(byte)0x7F,(byte)0xFF,(byte)0x3F,(byte)0xFF,(byte)0xCF,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xFC,(byte)0xE0,(byte)0x03,(byte)0x9C,(byte)0x18,(byte)0x3D,(byte)0xFF,(byte)0xFF,(byte)0x1F,(byte)0xFF,(byte)0xE3,(byte)0xFF,(byte)0xFC,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0xE4,(byte)0xFC,(byte)0x00,(byte)0x03,(byte)0xFF,(byte)0xFE,(byte)0x7F,(byte)0xFF,(byte)0x1F,(byte)0xFF,(byte)0x8F,(byte)0xFF,(byte)0xF0,(byte)0x07,(byte)0xFF,(byte)0xF8,(byte)0xE0,(byte)0x03,(byte)0x9C,(byte)0x18,(byte)0x3D,(byte)0xFF,(byte)0xFF,(byte)0x0F,(byte)0xFF,(byte)0xC1,(byte)0xFF,(byte)0xF8,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xF2,(byte)0x78,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x07,(byte)0xFF,(byte)0xC0,(byte)0xC0,(byte)0x01,(byte)0x9C,(byte)0x00,(byte)0x19,(byte)0xFF,(byte)0xF8,(byte)0x03,(byte)0xFF,(byte)0x00,(byte)0x3F,(byte)0xE0,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03,(byte)0xF9,(byte)0x30,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x01,(byte)0xFC,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xFE,(byte)0x40,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x3F,(byte)0x80,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x3F,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x1F,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x0E,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,};
	
			sendData(bytes);
			
			byte[] newline 	= Printer.printfont("\n\n",FontDefine.FONT_32PX,FontDefine.Align_CENTER,(byte)0x1A,PocketPos.LANGUAGE_ENGLISH);
			
			sendData(newline);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void printBarcode() { 
		String[] types = {"1D Barcode", "2D Barcode"};
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    builder.setTitle("Choose Barcode")
	           .setItems(types, new DialogInterface.OnClickListener() {
	               public void onClick(DialogInterface dialog, int which) {
		               if (which == 0) {
		            	   print1DBarcode();
		               } else {
		            	   print2DBarcode();
		               }
	           }
	    });
	    
	    builder.create().show();
	}
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	    public void onReceive(Context context, Intent intent) {	    	
	        String action = intent.getAction();
	        
	        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
	        	final int state 	= intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
	        	
	        	if (state == BluetoothAdapter.STATE_ON) {
	        		showEnabled();
	        	} else if (state == BluetoothAdapter.STATE_OFF) {
		        	showDisabled();
	        	}
	        } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
	        	mDeviceList = new ArrayList<BluetoothDevice>();
				
				mProgressDlg.show();
	        } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	mProgressDlg.dismiss();
	        	
	        	updateDeviceList();
	        } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	        	BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		        
	        	mDeviceList.add(device);
	        	
	        	showToast("Found device " + device.getName());
	        } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
	        	 final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
	        	 
	        	 if (state == BluetoothDevice.BOND_BONDED) {
	        		 showToast("Paired");
	        		 
	        		 connect();
	        	 }
	        }
	    }
	};
	
}