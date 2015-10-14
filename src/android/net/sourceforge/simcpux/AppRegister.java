package net.sourceforge.simcpux;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.justep.cordova.Config;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

public class AppRegister extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		Config.init();
		final IWXAPI msgApi = WXAPIFactory.createWXAPI(context, null);
		// 将该app注册到微信
		msgApi.registerApp(Config.getPreferences().getString("weixinappid", ""));
	}
}
