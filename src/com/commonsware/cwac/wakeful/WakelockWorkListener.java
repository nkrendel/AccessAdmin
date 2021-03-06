/***
  Copyright (c) 2009-11 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.commonsware.cwac.wakeful;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.alphabetbloc.accessadmin.data.Constants;
import com.alphabetbloc.accessadmin.services.DeviceAdminService;

public class WakelockWorkListener implements WakefulIntentService.AlarmListener {
	public void scheduleAlarms(AlarmManager mgr, PendingIntent pi, Context ctxt) {

		// We use prefs to save alarms because nothing stored after FC or reboot
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		int alarmtype = prefs.getInt(Constants.SAVED_DEVICE_ADMIN_WORK, 0);
		long trigger = getAlarmTriggerTime(alarmtype);
		long interval = getAlarmInterval(ctxt, alarmtype);

		// Use time since boot to 1. fire the alarm now (much later than boot)
		// 2. fire the alarm just after boot (default is 45 seconds)
		mgr.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, interval, pi);
	}

	public void sendWakefulWork(Context ctxt) {
		WakefulIntentService.sendWakefulWork(ctxt, DeviceAdminService.class);
	}

	private long getAlarmTriggerTime(int alarmtype) {
		// could also put this in a preference, but not so important
		long trigger = 0;
		switch (alarmtype) {
		case Constants.HOLD_DEVICE_LOCKED:
		case Constants.LOCK_RANDOM_PWD:
		case Constants.RESET_PWD_TO_SMS_PWD:
		case Constants.SEND_ADMIN_ID:
		case Constants.RESET_ADMIN_ID:
		case Constants.WIPE_DATA:
		case Constants.WIPE_ODK_DATA:
		case Constants.VERIFY_SIM:
		case Constants.LOCK_SCREEN:
		case Constants.FACTORY_RESET:
		case Constants.CANCEL_ALARMS:
			trigger = SystemClock.elapsedRealtime();
			break;
		case Constants.SEND_SMS:
		case Constants.SEND_GPS:
		default:
			trigger = SystemClock.elapsedRealtime() + (1000 * 60 * 5);
			break;
		}
		return trigger;

	}

	private long getAlarmInterval(Context ctxt, int alarmtype) {
		// store these in individual prefs so that they 1. can be different
		// 2. could allow device admin to change them in new activity
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		long interval = Constants.ALARM_INTERVAL_MEDIUM;
		switch (alarmtype) {
		case Constants.SEND_SMS:
			interval = prefs.getLong(Constants.ALARM_SEND_SMS, Constants.ALARM_INTERVAL_LONG);
			break;
		case Constants.SEND_GPS:
			interval = prefs.getLong(Constants.ALARM_SEND_GPS, Constants.ALARM_INTERVAL_MEDIUM);
			break;
		case Constants.VERIFY_SIM:
			interval = prefs.getLong(Constants.ALARM_VERIFY_SIM, Constants.ALARM_INTERVAL_LONG);
			break;
		case Constants.LOCK_SCREEN:
			interval = prefs.getLong(Constants.ALARM_LOCK_SCREEN, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.WIPE_DATA:
			interval = prefs.getLong(Constants.ALARM_WIPE_DATA, Constants.ALARM_INTERVAL_MEDIUM);
			break;
		case Constants.WIPE_ODK_DATA:
			interval = prefs.getLong(Constants.ALARM_WIPE_ODK_DATA, Constants.ALARM_INTERVAL_MEDIUM);
			break;
		case Constants.RESET_TO_DEFAULT_PWD:
			interval = prefs.getLong(Constants.ALARM_RESET_TO_DEFAULT_PWD, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.RESET_ADMIN_ID:
			interval = prefs.getLong(Constants.ALARM_RESET_ADMIN_ID, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.LOCK_RANDOM_PWD:
			interval = prefs.getLong(Constants.ALARM_LOCK_RANDOM_PWD, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.HOLD_DEVICE:
			interval = prefs.getLong(Constants.ALARM_HOLD_DEVICE, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.HOLD_DEVICE_LOCKED:
			interval = prefs.getLong(Constants.ALARM_HOLD_DEVICE_LOCKED, Constants.ALARM_INTERVAL_ALWAYS);
			break;
		case Constants.STOP_HOLD_DEVICE:
			interval = prefs.getLong(Constants.ALARM_STOP_HOLD_DEVICE, Constants.ALARM_INTERVAL_SHORT);
			break;	
		case Constants.RESET_PWD_TO_SMS_PWD:
			interval = prefs.getLong(Constants.ALARM_RESET_PWD_TO_SMS_PWD, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.SEND_ADMIN_ID:
			interval = prefs.getLong(Constants.ALARM_SEND_ADMIN_ID, Constants.ALARM_INTERVAL_SHORT);
			break;
		case Constants.FACTORY_RESET:
			interval = prefs.getLong(Constants.ALARM_FACTORY_RESET, Constants.ALARM_INTERVAL_MEDIUM);
			break;
		case Constants.CANCEL_ALARMS:
			interval = prefs.getLong(Constants.ALARM_CANCEL_ALARMS, Constants.ALARM_INTERVAL_SHORT);
			break;
		default:
			interval = Constants.ALARM_INTERVAL_MEDIUM;
			break;
		}

		return interval;
	}

	public long getMaxAge(Context ctxt) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctxt);
		int alarmtype = prefs.getInt(Constants.SAVED_DEVICE_ADMIN_WORK, 0);
		long interval = getAlarmInterval(ctxt, alarmtype);
		return (interval / 2);
	}
}
