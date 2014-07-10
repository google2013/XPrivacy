package biz.bokhorst.xprivacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.NfcAdapter;
import android.os.Binder;
import android.os.Bundle;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.telephony.TelephonyManager;
import android.util.Log;

public class XActivityThread extends XHook {
	private Methods mMethod;
	private String mActionName;

	private static final String GMS_LOCATION_CHANGED = "com.google.android.location.LOCATION";

	private XActivityThread(Methods method, String restrictionName, String actionName) {
		super(restrictionName, method.name(), actionName);
		mMethod = method;
		mActionName = actionName;
	}

	public String getClassName() {
		return "android.app.ActivityThread";
	}

	@Override
	public boolean isVisible() {
		return false;
	}

	private enum Methods {
		handleReceiver
	};

	// @formatter:off

	// private void handleReceiver(ReceiverData data)
	// frameworks/base/core/java/android/app/ActivityThread.java

	// @formatter:on

	@SuppressLint("InlinedApi")
	public static List<XHook> getInstances() {
		List<XHook> listHook = new ArrayList<XHook>();

		// Intent receive: calling
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cCalling,
				Intent.ACTION_NEW_OUTGOING_CALL));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cPhone,
				TelephonyManager.ACTION_PHONE_STATE_CHANGED));

		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cCalling,
				TelephonyManager.ACTION_RESPOND_VIA_MESSAGE));

		// Intent receive: C2DM
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNotifications,
				"com.google.android.c2dm.intent.REGISTRATION"));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNotifications,
				"com.google.android.c2dm.intent.RECEIVE"));

		// Intent receive: NFC
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNfc,
				NfcAdapter.ACTION_ADAPTER_STATE_CHANGED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNfc, NfcAdapter.ACTION_NDEF_DISCOVERED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNfc, NfcAdapter.ACTION_TAG_DISCOVERED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNfc, NfcAdapter.ACTION_TECH_DISCOVERED));

		// Intent receive: SMS
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cMessages,
				Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cMessages,
				Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cMessages,
				Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION));

		// Intent receive: notifications
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cNotifications,
				NotificationListenerService.SERVICE_INTERFACE));

		// Intent receive: package changes
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem, Intent.ACTION_PACKAGE_ADDED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem, Intent.ACTION_PACKAGE_REPLACED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_PACKAGE_RESTARTED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem, Intent.ACTION_PACKAGE_REMOVED));

		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem, Intent.ACTION_PACKAGE_CHANGED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_PACKAGE_DATA_CLEARED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_PACKAGE_FIRST_LAUNCH));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_PACKAGE_FULLY_REMOVED));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_PACKAGE_NEEDS_VERIFICATION));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem, Intent.ACTION_PACKAGE_VERIFIED));

		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE));
		listHook.add(new XActivityThread(Methods.handleReceiver, PrivacyManager.cSystem,
				Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE));

		return listHook;
	}

	@Override
	protected void before(XParam param) throws Throwable {
		String methodName = param.method.getName();
		if (mMethod == Methods.handleReceiver) {
			if (param.args.length > 0 && param.args[0] != null) {
				// Get intent
				Intent intent = null;
				try {
					Field fieldIntent = param.args[0].getClass().getDeclaredField("intent");
					fieldIntent.setAccessible(true);
					intent = (Intent) fieldIntent.get(param.args[0]);
				} catch (Throwable ex) {
					Util.bug(this, ex);
				}

				// Process intent
				if (intent != null) {
					// Check action
					String action = intent.getAction();
					if (mActionName.equals(action)) {
						if (action.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
							// Outgoing call
							Bundle bundle = intent.getExtras();
							if (bundle != null) {
								String phoneNumber = bundle.getString(Intent.EXTRA_PHONE_NUMBER);
								if (phoneNumber != null)
									if (isRestrictedExtra(param, mActionName, phoneNumber))
										intent.putExtra(Intent.EXTRA_PHONE_NUMBER, (String) PrivacyManager
												.getDefacedProp(Binder.getCallingUid(), "PhoneNumber"));
							}
						} else if (action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
							// Incoming call
							Bundle bundle = intent.getExtras();
							if (bundle != null) {
								String phoneNumber = bundle.getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
								if (phoneNumber != null) {
									if (isRestrictedExtra(param, mActionName, phoneNumber))
										intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, (String) PrivacyManager
												.getDefacedProp(Binder.getCallingUid(), "PhoneNumber"));
								}
							}
						} else if (getRestrictionName().equals(PrivacyManager.cSystem)) {
							// Package event
							if (isRestrictedExtra(param, mActionName, intent.getDataString())) {
								String[] packageNames;
								if (action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
										|| action.equals(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE))
									packageNames = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
								else
									packageNames = new String[] { intent.getData().getSchemeSpecificPart() };
								for (String packageName : packageNames)
									if (!XPackageManager.isPackageAllowed(packageName)) {
										finish(param);
										param.setResult(null);
										break;
									}
							}
						} else if (isRestrictedExtra(param, mActionName, intent.getDataString())) {
							finish(param);
							param.setResult(null);
						}
					} else {
						if (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED)) {
							int uid = Binder.getCallingUid();
							Util.log(null, Log.WARN, LocationManager.KEY_LOCATION_CHANGED + " uid=" + uid);
							Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
							Location fakeLocation = PrivacyManager.getDefacedLocation(uid, location);
							if (getRestricted(uid, PrivacyManager.cLocation, "requestLocationUpdates"))
								intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, fakeLocation);
						}

						else if (intent.hasExtra(GMS_LOCATION_CHANGED)) {
							int uid = Binder.getCallingUid();
							Util.log(null, Log.WARN, GMS_LOCATION_CHANGED + " uid=" + uid);
							Location location = (Location) intent.getExtras().get(GMS_LOCATION_CHANGED);
							Location fakeLocation = PrivacyManager.getDefacedLocation(uid, location);
							if (getRestricted(uid, PrivacyManager.cLocation, "GMS.requestLocationUpdates"))
								intent.putExtra(GMS_LOCATION_CHANGED, fakeLocation);
						}
					}
				}
			}

		} else
			Util.log(this, Log.WARN, "Unknown method=" + methodName);
	}

	@Override
	protected void after(XParam param) throws Throwable {
		// Do nothing
	}

	private void finish(XParam param) {
		// unscheduleGcIdler
		if (param.thisObject != null)
			try {
				Method unschedule = param.thisObject.getClass().getDeclaredMethod("unscheduleGcIdler");
				unschedule.setAccessible(true);
				unschedule.invoke(param.thisObject);
			} catch (Throwable ex) {
				Util.bug(this, ex);
			}

		// data.finish
		if (param.args[0] instanceof BroadcastReceiver.PendingResult)
			try {
				BroadcastReceiver.PendingResult pr = (BroadcastReceiver.PendingResult) param.args[0];
				pr.finish();
			} catch (IllegalStateException ignored) {
				// No receivers for action ...
			} catch (Throwable ex) {
				Util.bug(this, ex);
			}
	}
}
