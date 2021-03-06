/*
 * Copyright (C) 2012 Louis Fazen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.alphabetbloc.accessadmin.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * @author Louis Fazen (louis.fazen@gmail.com)
 *
 */
public class UpdateOnCoverage extends BroadcastReceiver {

//	private static final String TAG = "UpdateOnCoverage";
	Context mContext;
	
	public UpdateOnCoverage() {
		// Auto-generated constructor stub
	}

	@Override
	public void onReceive(Context context, Intent intent) {      
		
		try { 
//		Bundle bundle = intent.getExtras();
//	     String message = bundle.getString("alarm_message");
//	     
//	     Intent newIntent = new Intent(context, SetAppPreferences.class);
//	     newIntent.putExtra("alarm_message", message);
//	     newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//	     context.startActivity(newIntent);
	    } catch (Exception e) {
	     Toast.makeText(context, "There was an error somewhere, but we still received an alarm", Toast.LENGTH_SHORT).show();
	     e.printStackTrace();
	 
	    }
		   
		
//        if( "android.intent.action.CHANGE_NETWORK_STATE".equals(intent.getAction())) {
//        	if(Constants.DEBUG) Log.e(TAG, "Double-check: Received intent name: " + intent.toString()); 
// 
//        	 
//         ComponentName comp = new ComponentName(context.getPackageName(), UpdateClockService.class.getName());
//         ComponentName service = context.startService(new Intent().setComponent(comp));
//         if (null == service){
//          if(Constants.DEBUG) Log.e(TAG, "Could not start service: " + comp.toString());
//         }
//        } else {
//         if(Constants.DEBUG) Log.e(TAG, "Received unexpected intent: " + intent.toString());   
//        }
        
	}


}
