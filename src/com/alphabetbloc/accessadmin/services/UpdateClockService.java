package com.alphabetbloc.accessadmin.services;

/*
 * Copyright (C) 2008 The Android Open Source Project
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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.alphabetbloc.accessadmin.R;
import com.alphabetbloc.accessadmin.activities.NtpToastActivity;
import com.alphabetbloc.accessadmin.activities.SetUserPassword;
import com.alphabetbloc.accessadmin.data.Constants;
import com.alphabetbloc.accessadmin.receivers.UpdateClockReceiver;

/**
 * {@hide}
 * 
 * Simple SNTP client class for retrieving network time.
 * 
 * Sample usage:
 * 
 * <pre>
 * SntpClient client = new SntpClient();
 * if (client.requestTime(&quot;pool.ntp.com&quot;)) {
 * 	long now = client.getNtpTime() + SystemClock.elapsedRealtime() - client.getNtpTimeReference();
 * }
 * </pre>
 */

public class UpdateClockService extends IntentService {

	private NotificationManager mNM;
	private int NOTIFICATION = R.string.clock_service_start;

	private static final String TAG = UpdateClockService.class.getSimpleName();

	// private static final int REFERENCE_TIME_OFFSET = 16;
	private static final int ORIGINATE_TIME_OFFSET = 24;
	private static final int RECEIVE_TIME_OFFSET = 32;
	private static final int TRANSMIT_TIME_OFFSET = 40;
	private static final int NTP_PACKET_SIZE = 48;

	private static final int NTP_PORT = 123;
	private static final int NTP_MODE_CLIENT = 3;
	private static final int NTP_VERSION = 3;

	// Number of seconds between Jan 1, 1900 and Jan 1, 1970
	// 70 years plus 17 leap days
	private static final long OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L;

	// system time computed from NTP server response
	private long mNtpTime = -1;

	// value of SystemClock.elapsedRealtime() corresponding to mNtpTime
	private long mNtpTimeReference = -1;

	// ADDED BY ME:;
	private static final long MINIMUM_CLOCK_TIME = 1370713699558L;
	private Context mContext;
	private int timeout = 10000;
	private String host = "pool.ntp.org";

	/**
	 * A constructor is required, and must call the super IntentService(String)
	 * constructor with a name for the worker thread.
	 */
	public UpdateClockService() {
		super("UpdateClockService");
		mContext = this;
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean phoneLocked = settings.getBoolean(Constants.SIM_ERROR_PHONE_LOCKED, false);
		if (phoneLocked) {
			Log.e(TAG, "Skipping System Time Check while device is locked.");
			return;
		}
		mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		showNotification();

		try {
			synchronized (this) {
				Thread requestNtp = new Thread(mRequestNtpTime, "RequestNtpTimeThread");
				requestNtp.start();
				try {
					requestNtp.join();
				} catch (Exception exception) {
					if (Constants.DEBUG)
						Log.e(TAG, "Failed to join to requestNtp");
				}
			}
		} catch (Exception e) {
			if (Constants.DEBUG)
				Log.e(TAG, "HandleIntent try failed: " + e);
		}

		// Check to see if it was able to obtain Ntp time
		if (mNtpTime > 0 && mNtpTimeReference > 0) {
			if (clockNeedsUpdate())
				requestUserUpdate(true);
			else
				cancelUpdateClockAlarms();
			
		} else if (System.currentTimeMillis() < MINIMUM_CLOCK_TIME) {
			
			requestUserUpdate(false);
			if (Constants.DEBUG)
				Log.e(TAG, "Time is very far off... prompting user to reset the time");
			
		} else {
			Log.e(TAG, "Could not obtain the NTP Time. Alarm will continue to run every hour.");
		}

		mNM.cancel(NOTIFICATION);
	}

	private void showNotification() {
		CharSequence tickerText = getText(R.string.clock_service_ticker);
		CharSequence dropdownText = getText(R.string.clock_service_text);
		Notification notification = new Notification(R.drawable.clock_update, tickerText, System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, SetUserPassword.class), 0);
		notification.setLatestEventInfo(this, getText(R.string.clock_service_label), dropdownText, contentIntent);
		mNM.notify(NOTIFICATION, notification);
	}

	private Runnable mRequestNtpTime = new Runnable() {

		public void run() {

			DatagramSocket socket = null;
			try {
				socket = new DatagramSocket();
				socket.setSoTimeout(timeout);
				InetAddress address = InetAddress.getByName(host);
				byte[] buffer = new byte[NTP_PACKET_SIZE];
				DatagramPacket request = new DatagramPacket(buffer, buffer.length, address, NTP_PORT);

				// set mode = 3 (client) and version = 3
				// mode is in low 3 bits of first byte
				// version is in bits 3-5 of first byte
				buffer[0] = NTP_MODE_CLIENT | (NTP_VERSION << 3);

				// get current time and write it to the request packet
				long requestTime = System.currentTimeMillis();
				long requestTicks = SystemClock.elapsedRealtime();
				writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime);

				socket.send(request);

				// read the response
				DatagramPacket response = new DatagramPacket(buffer, buffer.length);
				socket.receive(response);
				long responseTicks = SystemClock.elapsedRealtime();
				long responseTime = requestTime + (responseTicks - requestTicks);
				socket.close();

				// extract the results
				long originateTime = readTimeStamp(buffer, ORIGINATE_TIME_OFFSET);
				long receiveTime = readTimeStamp(buffer, RECEIVE_TIME_OFFSET);
				long transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET);

				long clockOffset = ((receiveTime - originateTime) + (transmitTime - responseTime)) / 2;

				mNtpTime = responseTime + clockOffset;
				mNtpTimeReference = responseTicks;

			} catch (Exception e) {
				if (Constants.DEBUG)
					Log.d(TAG, "RequestNtpTime failed... caught Exception: " + e);
			}

			finally {
				if (socket != null) {
					socket.close();
				}
			}
		}

	};

	/**
	 * Compares the NTPTime with the SystemTime. Returns true if the difference
	 * is greater than 200 seconds.
	 */
	private boolean clockNeedsUpdate() {

		long realTime = mNtpTime + (SystemClock.elapsedRealtime() - mNtpTimeReference);
		long systemTime = System.currentTimeMillis();

		long delta = Math.abs(realTime - systemTime);
		if (delta > (1000 * 60 * 10)) {
			if (Constants.DEBUG)
				Log.e(TAG, "clockNeedsUpdate is true b/c delta is: " + delta);
			return true;

		} else {
			if (Constants.DEBUG)
				Log.e(TAG, "Delta is less than 120000 (2 min) already... no need to reset time and is: " + delta);
			return false;
		}
	}

	/**
	 * Prompt the user to set the system clock to correct date and time (rather
	 * than rely on root permissions).
	 */
	private void requestUserUpdate(boolean knownNtpTime) {
		
		//Set up appropriate strings for alert dialog
		String messageBody = "";
		String dateString = "";
		
		if (knownNtpTime) {
			long now = mNtpTime + (SystemClock.elapsedRealtime() - mNtpTimeReference);
			Date date = new Date();
			date.setTime(now);
			messageBody = mContext.getString(R.string.set_datetime);
			dateString = new SimpleDateFormat("EEE, MMM dd, yyyy 'at' KK:mm a ' ('HH:mm')'").format(date);
		} else {
			messageBody = mContext.getString(R.string.set_datetime_unknown);
			dateString = "";
		}
		
		//show date and time preferences
		try {
			Intent timeIntent = new Intent(android.provider.Settings.ACTION_DATE_SETTINGS);
			timeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			this.startActivity(timeIntent);
		} catch (Exception e) {
			Log.e(TAG, "Could not launch Date and Time Settings on this device.");
		}
		
		//Show alert dialog
		Intent i = new Intent(mContext, NtpToastActivity.class);
		i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		i.putExtra(NtpToastActivity.NTP_MESSAGE_BODY, messageBody);
		i.putExtra(NtpToastActivity.NTP_MESSAGE_DATE, dateString);
		mContext.startActivity(i);

	}

	/**
	 * Cancel any updateclock alarms if the time is now within ten minutes of
	 * being accurate. Otherwise, continue to prompt the user to change the
	 * alarm.
	 */
	private void cancelUpdateClockAlarms() {
		Intent i = new Intent(mContext, UpdateClockReceiver.class);
		PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, 0);
		AlarmManager aM = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
		aM.cancel(pi);
		if (Constants.DEBUG)
			Log.e(TAG, "Cancelled UpdateClock Alarms");
	}

	@Override
	public void onDestroy() {
		if (Constants.DEBUG)
			Log.e(TAG, "onDestroy has been called!");
		super.onDestroy();
	}

	// ///////////// CHECK TIME FUNCTIONS /////////////////////////
	/**
	 * Reads an unsigned 32 bit big endian number from the given offset in the
	 * buffer.
	 */
	private long read32(byte[] buffer, int offset) {
		byte b0 = buffer[offset];
		byte b1 = buffer[offset + 1];
		byte b2 = buffer[offset + 2];
		byte b3 = buffer[offset + 3];

		// convert signed bytes to unsigned values
		int i0 = ((b0 & 0x80) == 0x80 ? (b0 & 0x7F) + 0x80 : b0);
		int i1 = ((b1 & 0x80) == 0x80 ? (b1 & 0x7F) + 0x80 : b1);
		int i2 = ((b2 & 0x80) == 0x80 ? (b2 & 0x7F) + 0x80 : b2);
		int i3 = ((b3 & 0x80) == 0x80 ? (b3 & 0x7F) + 0x80 : b3);

		return ((long) i0 << 24) + ((long) i1 << 16) + ((long) i2 << 8) + (long) i3;
	}

	/**
	 * Reads the NTP time stamp at the given offset in the buffer and returns it
	 * as a system time (milliseconds since January 1, 1970).
	 */
	private long readTimeStamp(byte[] buffer, int offset) {
		long seconds = read32(buffer, offset);
		long fraction = read32(buffer, offset + 4);
		return ((seconds - OFFSET_1900_TO_1970) * 1000) + ((fraction * 1000L) / 0x100000000L);
	}

	/**
	 * Writes system time (milliseconds since January 1, 1970) as an NTP time
	 * stamp at the given offset in the buffer.
	 */
	private void writeTimeStamp(byte[] buffer, int offset, long time) {
		long seconds = time / 1000L;
		long milliseconds = time - seconds * 1000L;
		seconds += OFFSET_1900_TO_1970;

		// write seconds in big endian format
		buffer[offset++] = (byte) (seconds >> 24);
		buffer[offset++] = (byte) (seconds >> 16);
		buffer[offset++] = (byte) (seconds >> 8);
		buffer[offset++] = (byte) (seconds >> 0);

		long fraction = milliseconds * 0x100000000L / 1000L;
		// write fraction in big endian format
		buffer[offset++] = (byte) (fraction >> 24);
		buffer[offset++] = (byte) (fraction >> 16);
		buffer[offset++] = (byte) (fraction >> 8);
		// low order bits should be random data
		buffer[offset++] = (byte) (Math.random() * 255.0);
	}

	// ///////////// LOGGING /////////////////////////
	/**
	 * Convert a millisecond duration to a string format (for the sake of
	 * logging purposes only)
	 * 
	 * @param millis
	 *            A duration to convert to a string form
	 * @return A string of the form "X Days Y Hours Z Minutes A Seconds".
	 */
	public static String getDuration(long millis) {
		if (millis < 0) {
			// throw new
			// IllegalArgumentException("Duration must be greater than zero!");
			return "requested time is negative";
		}

		int years = (int) (millis / (1000 * 60 * 60 * 24 * 365.25));
		millis -= (years * (1000 * 60 * 60 * 24 * 365.25));
		int days = (int) ((millis / (1000 * 60 * 60 * 24)) % 365.25);
		millis -= (days * (1000 * 60 * 60 * 24));
		int hours = (int) ((millis / (1000 * 60 * 60)) % 24);
		millis -= (hours * (1000 * 60 * 60));
		int minutes = (int) ((millis / (1000 * 60)) % 60);
		millis -= (minutes * (1000 * 60));
		int seconds = (int) (millis / 1000) % 60;

		StringBuilder sb = new StringBuilder(64);
		sb.append(years);
		sb.append(" Years ");
		sb.append(days);
		sb.append(" Days ");
		sb.append(hours);
		sb.append(" Hours ");
		sb.append(minutes);
		sb.append(" Minutes ");
		sb.append(seconds);
		sb.append(" Seconds");

		return (sb.toString());
	}

}
