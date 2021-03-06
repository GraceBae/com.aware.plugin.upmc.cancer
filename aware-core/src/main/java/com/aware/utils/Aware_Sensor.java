
package com.aware.utils;

import java.util.ArrayList;
import java.util.Calendar;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ui.PermissionsHandler;

/**
 * Aware_Sensor: Extend to integrate with the framework (extension of Android Service class).
 * @author dferreira
 *
 */
public class Aware_Sensor extends Service {
	
	/**
	 * Debug tag for this sensor
	 */
	public static String TAG = "AWARE Sensor";
	
	/**
	 * Debug flag for this sensor
	 */
	public static boolean DEBUG = false;
	
	public ContextProducer CONTEXT_PRODUCER = null;
	
	/**
	 * Sensor database tables
	 */
	public String[] DATABASE_TABLES = null;
	
	/**
	 * Sensor table fields
	 */
	public String[] TABLES_FIELDS = null;
	
	/**
	 * Context Providers URIs
	 */
	public Uri[] CONTEXT_URIS = null;

    /**
     * Permissions needed for this plugin to run
     */
    public ArrayList<String> REQUIRED_PERMISSIONS = new ArrayList<>();
	
	/**
	 * Sensor is inactive
	 */
	public static final int STATUS_SENSOR_OFF = 0;
	
	/**
	 * Sensor is active
	 */
	public static final int STATUS_SENSOR_ON = 1;

	/**
     * Interface to share context with other applications/addons<br/>
     * You MUST broadcast your contexts here!
     * @author denzil
     */
    public interface ContextProducer {
    	public void onContext();
    }
	
    @Override
    public void onCreate() {
    	super.onCreate();
    	
    	TAG = Aware.getSetting(getApplicationContext(),Aware_Preferences.DEBUG_TAG).length()>0?Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_TAG):TAG;
        DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");
        
        if( DEBUG ) Log.d(TAG, TAG + " sensor created!");
        
        //Register Context Broadcaster
        IntentFilter filter = new IntentFilter();
        filter.addAction(Aware.ACTION_AWARE_CURRENT_CONTEXT);
        filter.addAction(Aware.ACTION_AWARE_SYNC_DATA);
        filter.addAction(Aware.ACTION_AWARE_CLEAR_DATA);
        filter.addAction(Aware.ACTION_AWARE_STOP_SENSORS);
        filter.addAction(Aware.ACTION_AWARE_SPACE_MAINTENANCE);
        registerReceiver(contextBroadcaster, filter);

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();

        //Unregister Context Broadcaster
        unregisterReceiver(contextBroadcaster);
        
        if(DEBUG) Log.d(TAG, TAG + " sensor terminated...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Ask the user all required permissions
        final ArrayList<String> missing = new ArrayList<>();
        for( String p : REQUIRED_PERMISSIONS ) {
            int permission_access = ContextCompat.checkSelfPermission(getApplicationContext(), p);
            if( permission_access != PackageManager.PERMISSION_GRANTED ) {
                missing.add(p);
            }
        }
        if( missing.size() > 0 ) {
            Intent permissionRequest = new Intent(this, PermissionsHandler.class);
            permissionRequest.putExtra( PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, missing.toArray(new String[missing.size()]) );
            permissionRequest.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP );
            startActivity(permissionRequest);
        }
        return super.onStartCommand(intent, flags, startId);
    }
    
	/**
     * AWARE Context Broadcaster<br/>
     * - ACTION_AWARE_CURRENT_CONTEXT: returns current plugin's context
     * - ACTION_AWARE_SYNC_DATA: push content provider data remotely
     * - ACTION_AWARE_CLEAR_DATA: clears local and remote database
     * - ACTION_AWARE_STOP_SENSORS: stops this sensor
     * - ACTION_AWARE_SPACE_MAINTENANCE: clears old data from content providers
     * @author denzil
     */
    public class ContextBroadcaster extends BroadcastReceiver {
    	@Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(Aware.ACTION_AWARE_CURRENT_CONTEXT) ) {
                if( CONTEXT_PRODUCER != null ) {
                    CONTEXT_PRODUCER.onContext();
                }
            }
            if( intent.getAction().equals(Aware.ACTION_AWARE_SYNC_DATA) && Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
            	if( DATABASE_TABLES != null && TABLES_FIELDS != null && CONTEXT_URIS != null) {
            		for( int i=0; i<DATABASE_TABLES.length; i++ ) {
            			Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                        webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
            			webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                		webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[i]);
                		webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[i].toString());
                		context.startService(webserviceHelper);
            		}
            	} else {
            		if( Aware.DEBUG ) Log.d(TAG,"No database to backup!");
            	}
            }
            if( intent.getAction().equals(Aware.ACTION_AWARE_CLEAR_DATA)) {
            	if( DATABASE_TABLES != null && CONTEXT_URIS != null ) {
            		for( int i=0; i<DATABASE_TABLES.length; i++) {
	            		//Clear locally
	            		context.getContentResolver().delete(CONTEXT_URIS[i], null, null);
	            		if( Aware.DEBUG ) Log.d(TAG,"Cleared " + CONTEXT_URIS[i].toString());
	            		
	            		//Clear remotely
	            		if( Aware.getSetting(context, Aware_Preferences.STATUS_WEBSERVICE).equals("true") ) {
		            		Intent webserviceHelper = new Intent(context, WebserviceHelper.class);
                            webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_CLEAR_TABLE);
		            		webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
		            		context.startService(webserviceHelper);
	            		}
            		}
            	}
            }
            if(intent.getAction().equals(Aware.ACTION_AWARE_STOP_SENSORS)) {
                if( Aware.DEBUG ) Log.d(TAG, TAG + " stopped");
                stopSelf();
            }
            
            String frequency_old = Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA);
            if(frequency_old.length() == 0) Aware.setSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA, 0);
            
            if(intent.getAction().equals(Aware.ACTION_AWARE_SPACE_MAINTENANCE) && ! Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA).equals("0") ) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());
                
                switch(Integer.parseInt(Aware.getSetting(getApplicationContext(), Aware_Preferences.FREQUENCY_CLEAN_OLD_DATA))) {
                    case 1: //weekly
                        if( DATABASE_TABLES != null && CONTEXT_URIS != null ) {
                            cal.add(Calendar.DAY_OF_YEAR, -7);
                            if( Aware.DEBUG ) Log.d(TAG, TAG + " cleaning locally any data older than last week (yyyy/mm/dd): "+cal.get(Calendar.YEAR)+'/'+(cal.get(Calendar.MONTH)+1)+'/'+cal.get(Calendar.DAY_OF_MONTH));
                            for( int i=0; i<DATABASE_TABLES.length; i++) {
                                //Clear locally
                                String where = "timestamp < " + cal.getTimeInMillis(); 
                                int rowsDeleted = context.getContentResolver().delete(CONTEXT_URIS[i], where, null);
                                if( Aware.DEBUG ) Log.d(TAG,"Cleaned " +rowsDeleted+ " from " + CONTEXT_URIS[i].toString());
                            }
                        }
                        break;
                    case 2: //monthly
                        if( DATABASE_TABLES != null && CONTEXT_URIS != null ) {
                            cal.add(Calendar.MONTH, -1);
                            if( Aware.DEBUG ) Log.d(TAG, TAG + " cleaning locally any data older than last month (yyyy/mm/dd): "+cal.get(Calendar.YEAR)+'/'+(cal.get(Calendar.MONTH)+1)+'/'+cal.get(Calendar.DAY_OF_MONTH));
                            for( int i=0; i<DATABASE_TABLES.length; i++) {
                                //Clear locally
                                String where = "timestamp < " + cal.getTimeInMillis(); 
                                int rowsDeleted = context.getContentResolver().delete(CONTEXT_URIS[i], where, null);
                                if( Aware.DEBUG ) Log.d(TAG,"Cleaned " +rowsDeleted+ " from " + CONTEXT_URIS[i].toString());
                            }
                        }
                        break;
                }
            }
        }
    }
    private ContextBroadcaster contextBroadcaster = new ContextBroadcaster();
    
    @Override
    public IBinder onBind(Intent intent) {
    	return null;
    }
}
