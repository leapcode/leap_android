/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.blinkt.openvpn;

import java.io.IOException;
import java.util.Vector;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

public class OpenVpnService extends VpnService implements Handler.Callback {
	Handler mHandler;
	private Thread mServiceThread;

	private ParcelFileDescriptor mInterface;

	private Vector<String> mDnslist=new Vector<String>();

	private VpnProfile mProfile;

	private String mDomain=null;

	private Vector<CIDRIP> mRoutes=new Vector<CIDRIP>();

	private CIDRIP mLocalIP;

	private OpenVpnManagementThread mSocketManager;

	private Thread mSocketManagerThread;



	class CIDRIP{
		String mIp;
		int len;
		public CIDRIP(String ip, String mask){
			mIp=ip;
			String[] ipt = mask.split("\\.");
			long netmask=0;

			netmask += Long.parseLong(ipt[0])<< 24;
			netmask += Integer.parseInt(ipt[1])<< 16;
			netmask += Integer.parseInt(ipt[2])<< 8;
			netmask += Integer.parseInt(ipt[3]);

			// Add 33. bit to ensure the loop terminates
			netmask += 1l << 32;
			
			int lenZeros = 0;
			while((netmask & 0x1) == 0) {
				lenZeros++;
				netmask = netmask >> 1;
			}
			// Check if rest of netmask is only 1s
			if(netmask != (0x1ffffffffl >> lenZeros)) {
				// Asume no CIDR, set /32
				len=32;
			} else {
				len =32 -lenZeros; 
			}
			
		}
		@Override
		public String toString() {
			return String.format("%s/%d",mIp,len);
		}
	}

	@Override
	public void onRevoke() {
		mSocketManager.managmentCommand("signal SIGINT\n");
		mServiceThread=null;
		stopSelf();
	};






	private LocalSocket openManagmentInterface() {
		// Could take a while to open connection
		String socketname = (getCacheDir().getAbsolutePath() + "/" +  "mgmtsocket");
		LocalSocket sock = new LocalSocket();
		int tries = 8;

		while(tries > 0 && !sock.isConnected()) {
			try {
				sock.connect(new LocalSocketAddress(socketname,
						LocalSocketAddress.Namespace.FILESYSTEM));
			} catch (IOException e) {
				// wait 300 ms before retrying
				try { Thread.sleep(300);
				} catch (InterruptedException e1) {}

			} 
			tries--;
		}
		return sock;

	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// The handler is only used to show messages.
		if (mHandler == null) {
			mHandler = new Handler(this);
		}

		// Stop the previous session by interrupting the thread.
		if (mSocketManager != null) {
			mSocketManager.managmentCommand("signal SIGINT\n");
		}

		if (mServiceThread!=null) {
			mServiceThread.interrupt();
		}


		// Extract information from the intent.
		String prefix = getPackageName();
		String[] argv = intent.getStringArrayExtra(prefix + ".ARGV");

		String profileUUID = intent.getStringExtra(prefix + ".profileUUID");
		mProfile = ProfileManager.get(profileUUID);

		// Start a new session by creating a new thread.

		OpenVPNThread serviceThread = new OpenVPNThread(this, argv);

		mServiceThread = new Thread(serviceThread, "OpenVPNServiceThread");
		mServiceThread.start();


		// Open the Management Interface
		LocalSocket mgmtsocket =  openManagmentInterface();

		if(mgmtsocket!=null) {
			// start a Thread that handles incoming messages of the managment socket
			mSocketManager = new OpenVpnManagementThread(mProfile,mgmtsocket);
			mSocketManagerThread = new Thread(mSocketManager,"OpenVPNMgmtThread");
			mSocketManagerThread.start();
		}

		return START_STICKY;
	}





	@Override
	public void onDestroy() {
		if (mServiceThread != null) {
			mSocketManager.managmentCommand("signal SIGINT\n");

			mServiceThread.interrupt();
		}
	}

	@Override
	public boolean handleMessage(Message message) {
		if (message != null) {
			Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
		}
		return true;
	}




	public ParcelFileDescriptor openTun() {
		Builder builder = new Builder();

		builder.addAddress(mLocalIP.mIp, mLocalIP.len);

		for (String dns : mDnslist ) {
			builder.addDnsServer(dns);
		}


		for (CIDRIP route:mRoutes) {
			try {
				builder.addRoute(route.mIp, route.len);
			} catch (IllegalArgumentException ia) {
				OpenVPN.logMessage(0, "", getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
			}
		}

		if(mDomain!=null)
			builder.addSearchDomain(mDomain);


		mDnslist.clear();
		mRoutes.clear();


		builder.setSession(mProfile.mName + " - " + mLocalIP);

		// Let the configure Button show the Log
		Intent intent = new Intent(getBaseContext(),LogWindow.class);
		PendingIntent startLW = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
		builder.setConfigureIntent(startLW);
		mInterface = builder.establish();
		return mInterface;

	}

	public void addDNS(String dns) {
		mDnslist.add(dns);		
	}


	public void setDomain(String domain) {
		if(mDomain==null) {
			mDomain=domain;
		}
	}


	public void addRoute(String dest, String mask) {
		mRoutes.add(new CIDRIP(dest, mask));
	}


	public void setLocalIP(String local, String netmask) {
		mLocalIP = new CIDRIP(local, netmask);
		if(mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
			OpenVPN.logMessage(0, "", String.format(getString(R.string.ip_not_cidr, local,netmask)));
		}
	}


	public Handler getHandler() {
		return mHandler;
	}
}