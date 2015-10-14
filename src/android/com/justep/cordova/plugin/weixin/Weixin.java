package com.justep.cordova.plugin.weixin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import net.sourceforge.simcpux.MD5;
import net.sourceforge.simcpux.Util;
import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXTextObject;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.modelpay.PayReq;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.justep.x5.base.Constants;

public class Weixin extends CordovaPlugin{
	public static final String TAG = "Weixin";

	public static final String ERROR_WX_NOT_INSTALLED = "未安装微信";
	public static final String ERROR_ARGUMENTS = "参数错误";

	public static final String KEY_ARG_MESSAGE = "message";
	public static final String KEY_ARG_SCENE = "scene";
	public static final String KEY_ARG_MESSAGE_TITLE = "title";
	public static final String KEY_ARG_MESSAGE_DESCRIPTION = "description";
	public static final String KEY_ARG_MESSAGE_THUMB = "thumb";
	public static final String KEY_ARG_MESSAGE_MEDIA = "media";
	public static final String KEY_ARG_MESSAGE_MEDIA_TYPE = "type";
	public static final String KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL = "webpageUrl";
	public static final String KEY_ARG_MESSAGE_MEDIA_TEXT = "text";

	public static final int TYPE_WX_SHARING_APP = 1;
	public static final int TYPE_WX_SHARING_EMOTION = 2;
	public static final int TYPE_WX_SHARING_FILE = 3;
	public static final int TYPE_WX_SHARING_IMAGE = 4;
	public static final int TYPE_WX_SHARING_MUSIC = 5;
	public static final int TYPE_WX_SHARING_VIDEO = 6;
	public static final int TYPE_WX_SHARING_WEBPAGE = 7;
	public static final int TYPE_WX_SHARING_TEXT = 8;
	
	protected IWXAPI api;
	
	protected static CallbackContext currentCallbackContext;
	
	private String app_id;
	private static String partner_key;
	private static String partner_id;
	private static String app_secret;
	private static String app_key;
	private HashMap<String,PayOrder> payOrderList = new HashMap<String,PayOrder>();
	
	@Override
	public boolean execute(String action, JSONArray args,
			CallbackContext callbackContext) throws JSONException {
		// save the current callback context
		currentCallbackContext = callbackContext;
		// check if installed
		if (!api.isWXAppInstalled()) {
			callbackContext.error(ERROR_WX_NOT_INSTALLED);
			return true;
		}
		if (action.equals("share")) {
			// sharing
			return share(args, callbackContext);
		}else if(action.equals("getAccessToken")){
			return getAccessToken();
		}else if(action.equals("generatePrepayId")){
			return generatePrepayId(args);
		}else if(action.equals("sendPayReq")){
			return sendPayReq(args);
		}
		return false;
	}
	
	private boolean getAccessToken(){
		Log.i(TAG, "pay begin");
		new GetAccessTokenTask().execute();
		return true;
	}
	
	private boolean generatePrepayId(JSONArray args){
		//pay
		try {
			JSONObject prepayInfo = args.getJSONObject(0);
			String accessToken = prepayInfo.getString("accessToken");
			JSONObject productArgs = genProductArgs(prepayInfo);
			if(productArgs !=null){
				new GetPrepayIdTask(accessToken,productArgs).execute();
			}			
		} catch (JSONException e) {
			currentCallbackContext.error("参数格式不正确");
			return false;
		}
		return true;
	}
	
	protected boolean sendPayReq(JSONArray args){
		Log.i(TAG, "pay begin");
		try {
			JSONObject prepayIdObj = args.getJSONObject(0);
			String prepayId = prepayIdObj.getString("prepayId");
			sendPayReq(prepayId);
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error("参数错误");
			return false;
		}
		return true;
	}

	protected void getWXAPI() {
		if (api == null) {
			app_id = Constants.APPID;//webView.getProperty("weixinappid", "");
			api = WXAPIFactory.createWXAPI(webView.getContext(), app_id, true);
			Boolean registered = api.registerApp(Constants.APPID);//webView.getProperty("weixinappid", ""));
		}
	}

	protected boolean share(JSONArray args, CallbackContext callbackContext)
			throws JSONException {
		// check if # of arguments is correct
		if (args.length() != 1) {
			callbackContext.error(ERROR_ARGUMENTS);
		}

		final JSONObject params = args.getJSONObject(0);
		final SendMessageToWX.Req req = new SendMessageToWX.Req();
		req.transaction = String.valueOf(System.currentTimeMillis());

		if (params.has(KEY_ARG_SCENE)) {
			req.scene = params.getInt(KEY_ARG_SCENE);
		} else {
			req.scene = SendMessageToWX.Req.WXSceneTimeline;
		}

		// run in background
		cordova.getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				try {
					req.message = buildSharingMessage(params.getJSONObject(KEY_ARG_MESSAGE));
				} catch (JSONException e) {
					e.printStackTrace();
					currentCallbackContext.error(e.getMessage());
				}
				Boolean sended = api.sendReq(req);
				if(sended){
					currentCallbackContext.success();
				}else{
					currentCallbackContext.error("发送失败");
				}
			}
		});
		return true;
	}

	protected WXMediaMessage buildSharingMessage(JSONObject message)
			throws JSONException {
		URL thumbnailUrl = null;
		Bitmap thumbnail = null;

		try {
			thumbnailUrl = new URL(message.getString(KEY_ARG_MESSAGE_THUMB));
			thumbnail = BitmapFactory.decodeStream(thumbnailUrl
					.openConnection().getInputStream());

		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		WXMediaMessage wxMediaMessage = new WXMediaMessage();
		wxMediaMessage.title = message.getString(KEY_ARG_MESSAGE_TITLE);
		wxMediaMessage.description = message
				.getString(KEY_ARG_MESSAGE_DESCRIPTION);
		if (thumbnail != null) {
			wxMediaMessage.setThumbImage(thumbnail);
		}

		// media parameters
		WXMediaMessage.IMediaObject mediaObject = null;
		JSONObject media = message.getJSONObject(KEY_ARG_MESSAGE_MEDIA);

		// check types
		int type = media.has(KEY_ARG_MESSAGE_MEDIA_TYPE) ? media
				.getInt(KEY_ARG_MESSAGE_MEDIA_TYPE) : TYPE_WX_SHARING_WEBPAGE;
		switch (type) {
		case TYPE_WX_SHARING_APP:
			break;

		case TYPE_WX_SHARING_EMOTION:
			break;

		case TYPE_WX_SHARING_FILE:
			break;

		case TYPE_WX_SHARING_IMAGE:
			break;

		case TYPE_WX_SHARING_MUSIC:
			break;

		case TYPE_WX_SHARING_VIDEO:
			break;
			
		case TYPE_WX_SHARING_TEXT:
			mediaObject = new WXTextObject();
			((WXTextObject)mediaObject).text = media.getString(KEY_ARG_MESSAGE_MEDIA_TEXT);
			break;

		case TYPE_WX_SHARING_WEBPAGE:
		default:
			mediaObject = new WXWebpageObject();
			((WXWebpageObject) mediaObject).webpageUrl = media
					.getString(KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL);
		}
		wxMediaMessage.mediaObject = mediaObject;
		return wxMediaMessage;
	}
	
	
	// 支付
	
	
	private String genPackage(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(partner_key); // 注意：不能hardcode在客户端，建议genPackage这个过程都由服务器端完成
		
		// 进行md5摘要前，params内容为原始内容，未经过url encode处理
		String packageSign = MD5.getMessageDigest(sb.toString().getBytes()).toUpperCase();
		
		return URLEncodedUtils.format(params, "utf-8") + "&sign=" + packageSign;
	}
	
	
	
	
	private class GetAccessTokenTask extends AsyncTask<Void, Void, GetAccessTokenResult> {

		
		@Override
		protected void onPreExecute() {
			Log.i(TAG, "获取accessToken");
			Toast.makeText(cordova.getActivity(), "启动微信支付环境", Toast.LENGTH_LONG).show();
		}

		@Override
		protected void onPostExecute(GetAccessTokenResult result) {
			
			if (result.localRetCode == LocalRetCode.ERR_OK) {
				Log.d(TAG, "onPostExecute, accessToken = " + result.accessToken);
				currentCallbackContext.success(result.accessToken);
			} else {
				currentCallbackContext.error(result.errCode);
			}
		}

		@Override
		protected GetAccessTokenResult doInBackground(Void... params) {
			GetAccessTokenResult result = new GetAccessTokenResult();
			String url = String.format("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
					app_id, app_secret);
			Log.d(TAG, "get access token, url = " + url);
			
			byte[] buf = Util.httpGet(url);
			if (buf == null || buf.length == 0) {
				result.localRetCode = LocalRetCode.ERR_HTTP;
				return result;
			}
			
			String content = new String(buf);
			result.parseFrom(content);
			Log.d(TAG, "get access token, result = " + content);
			return result;
		}
	}
	
	private class GetPrepayIdTask extends AsyncTask<Void, Void, GetPrepayIdResult> {
		private String accessToken;
		private JSONObject productArgs;
		
		public GetPrepayIdTask(String accessToken,JSONObject productArgs) {
			this.accessToken = accessToken;
			this.productArgs = productArgs;
		}
		
		@Override
		protected void onPreExecute() {
			Log.i(TAG, "正在获取订单id");
		}

		@Override
		protected void onPostExecute(GetPrepayIdResult result) {
			Toast.makeText(cordova.getActivity(), "正在生成预支付订单", Toast.LENGTH_LONG).show();
			if (result.localRetCode == LocalRetCode.ERR_OK) {
				JSONObject prepay = new JSONObject();
				try {
					String prepayId = result.prepayId;
					prepay.put("prepayId",prepayId);
					PayOrder payOrder = new PayOrder();
					payOrder.setPrepayId(prepayId);
					payOrder.setTimeStamp(productArgs.getLong("timestamp"));
					payOrder.setNonceStr(productArgs.getString("noncestr"));
					payOrder.setAccessToken(accessToken);
					payOrder.setProductArgs(productArgs.toString());
					payOrder.setPackeageValue(productArgs.getString("package"));
					Log.i(TAG, "payOrder:"+payOrder);
					payOrderList.put(prepayId,payOrder);
					currentCallbackContext.success(prepayId);
				} catch (JSONException e) {
					e.printStackTrace();
					currentCallbackContext.error(result.errCode);
				}
			} else {
				currentCallbackContext.error(result.errCode);
				//Toast.makeText(cordova.getActivity(),result.localRetCode.name(), Toast.LENGTH_LONG).show();
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected GetPrepayIdResult doInBackground(Void... params) {
			String url = String.format("https://api.weixin.qq.com/pay/genprepay?access_token=%s", accessToken);
			String entity = productArgs.toString();
			
			Log.d(TAG, "doInBackground, url = " + url);
			Log.d(TAG, "doInBackground, entity = " + entity);
			
			GetPrepayIdResult result = new GetPrepayIdResult();
			
			byte[] buf = Util.httpPost(url, entity);
			if (buf == null || buf.length == 0) {
				result.localRetCode = LocalRetCode.ERR_HTTP;
				return result;
			}
			
			String content = new String(buf);
			Log.d(TAG, "doInBackground, content = " + content);
			result.parseFrom(content);
			return result;
		}
	}

	private static enum LocalRetCode {
		ERR_OK, ERR_HTTP, ERR_JSON, ERR_OTHER
	}
	
	private static class GetAccessTokenResult {
		
		private static final String TAG = "GetAccessTokenResult";
		
		public LocalRetCode localRetCode = LocalRetCode.ERR_OTHER;
		public String accessToken;
		public int expiresIn;
		public int errCode;
		public String errMsg;
		
		public void parseFrom(String content) {

			if (content == null || content.length() <= 0) {
				Log.e(TAG, "parseFrom fail, content is null");
				localRetCode = LocalRetCode.ERR_JSON;
				return;
			}
			
			try {
				JSONObject json = new JSONObject(content);
				if (json.has("access_token")) { // success case
					accessToken = json.getString("access_token");
					expiresIn = json.getInt("expires_in");
					localRetCode = LocalRetCode.ERR_OK;
				} else {
					errCode = json.getInt("errcode");
					errMsg = json.getString("errmsg");
					localRetCode = LocalRetCode.ERR_JSON;
				}
				
			} catch (Exception e) {
				localRetCode = LocalRetCode.ERR_JSON;
			}
		}
	}
	
	private static class GetPrepayIdResult {
		private static final String TAG = "GetPrepayIdResult";
		public LocalRetCode localRetCode = LocalRetCode.ERR_OTHER;
		public String prepayId;
		public int errCode;
		public String errMsg;
		
		public void parseFrom(String content) {
			if (content == null || content.length() <= 0) {
				Log.e(TAG, "parseFrom fail, content is null");
				localRetCode = LocalRetCode.ERR_JSON;
				return;
			}
			
			try {
				JSONObject json = new JSONObject(content);
				if (json.has("prepayid")) { // success case
					prepayId = json.getString("prepayid");
					localRetCode = LocalRetCode.ERR_OK;
				} else {
					localRetCode = LocalRetCode.ERR_JSON;
				}
				errCode = json.getInt("errcode");
				errMsg = json.getString("errmsg");
			} catch (Exception e) {
				localRetCode = LocalRetCode.ERR_JSON;
			}
		}
	}
	
	private String genNonceStr() {
		Random random = new Random();
		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
	}
	
	private long genTimeStamp() {
		return System.currentTimeMillis() / 1000;
	}
	
//	/**
//	 * 建议 traceid 字段包含用户信息及订单信息，方便后续对订单状态的查询和跟踪
//	 */
//	public String getTraceId() {
//		return "traceId_" + genTimeStamp(); 
//	}
//	
//	/**
//	 * 注意：商户系统内部的订单号,32个字符内、可包含字母,确保在商户系统唯一
//	 */
//	public String genOutTradNo() {
//		Random random = new Random();
//		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
//	}
	
	private String genSign(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();
		
		int i = 0;
		for (; i < params.size() - 1; i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append(params.get(i).getName());
		sb.append('=');
		sb.append(params.get(i).getValue());
		
		String sha1 = Util.sha1(sb.toString());
		Log.d(TAG, "genSign, sha1 = " + sha1);
		return sha1;
	}
	
	private JSONObject genProductArgs(JSONObject args) {
		JSONObject json = new JSONObject();
		try {
			json.put("appid", app_id);
			json.put("traceid", args.getString("traceId"));
			String nonceStr = genNonceStr();
			json.put("noncestr", nonceStr);
			Log.i(TAG, "nonceStr:" + nonceStr);
			List<NameValuePair> packageParams = new LinkedList<NameValuePair>();
			packageParams.add(new BasicNameValuePair("bank_type", "WX"));
			packageParams.add(new BasicNameValuePair("body", args.getString("body")));
			packageParams.add(new BasicNameValuePair("fee_type", args.getString("feeType")));
			packageParams.add(new BasicNameValuePair("input_charset", "UTF-8"));
			packageParams.add(new BasicNameValuePair("notify_url", args.getString("notifyUrl")));
			packageParams.add(new BasicNameValuePair("out_trade_no",args.getString("tradeNo")));
			packageParams.add(new BasicNameValuePair("partner", partner_id));
			packageParams.add(new BasicNameValuePair("spbill_create_ip",Util.getIpAddress()));
			packageParams.add(new BasicNameValuePair("total_fee", args.getString("totalFee")));
			String packageValue = genPackage(packageParams);
			json.put("package", packageValue);
			Long timeStamp = genTimeStamp();
			Log.i(TAG, "timeStamp:" + timeStamp);
			json.put("timestamp", timeStamp);
			List<NameValuePair> signParams = new LinkedList<NameValuePair>();
			signParams.add(new BasicNameValuePair("appid", app_id));
			signParams.add(new BasicNameValuePair("appkey", app_key));
			signParams.add(new BasicNameValuePair("noncestr", nonceStr));
			signParams.add(new BasicNameValuePair("package", packageValue));
			signParams.add(new BasicNameValuePair("timestamp", String.valueOf(timeStamp)));
			signParams.add(new BasicNameValuePair("traceid", args.getString("traceId")));
			json.put("app_signature", genSign(signParams));
			json.put("sign_method", "sha1");
		} catch (Exception e) {
			Log.e(TAG, "genProductArgs fail, ex = " + e.getMessage());
			currentCallbackContext.error("支付参数不正确");
			return null;
		}
		return json;
	}
	
	private void sendPayReq(String prepayId) {
		PayOrder payOrder = payOrderList.get(prepayId);
		if(payOrder == null){
			currentCallbackContext.error("不存在prepayId对应的支付订单");
			return;
		}
		final PayReq req = new PayReq();
		req.appId = app_id;
		req.partnerId = partner_id;
		req.prepayId = prepayId;
		req.nonceStr = payOrder.getNonceStr();
		req.timeStamp = String.valueOf(payOrder.getTimeStamp());
		req.packageValue = "Sign=" + payOrder.getPackeageValue();
		
		List<NameValuePair> signParams = new LinkedList<NameValuePair>();
		signParams.add(new BasicNameValuePair("appid", req.appId));
		signParams.add(new BasicNameValuePair("appkey", app_key));
		signParams.add(new BasicNameValuePair("noncestr", req.nonceStr));
		signParams.add(new BasicNameValuePair("package", req.packageValue));
		signParams.add(new BasicNameValuePair("partnerid", partner_id));
		signParams.add(new BasicNameValuePair("prepayid", req.prepayId));
		signParams.add(new BasicNameValuePair("timestamp", req.timeStamp));
		req.sign = genSign(signParams);
		// 在支付之前，如果应用没有注册到微信，应该先调用IWXMsg.registerApp将应用注册到微信
		
		// run in background
		cordova.getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				Boolean sended = api.sendReq(req);
				if(!sended){
					currentCallbackContext.error("发送支付请求失败");
				}
			}
		});
		//currentCallbackContext.success("{\"partnerId\":\""+req.partnerId+"\",\"prepayId\":\""+req.prepayId+"\"}");
	}

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		partner_key = Constants.PARTNERKEY;//webView.getProperty("partner_key", "");
		partner_id = Constants.PARTNERID;//webView.getProperty("partner_id", "");
		app_secret = Constants.APPSECRET;//webView.getProperty("app_secret", "");
		app_key = Constants.APPKEY;//webView.getProperty("app_key", "");
		getWXAPI();
		this.onWeixinResp(cordova.getActivity().getIntent());
	}
	
	private void onWeixinResp(Intent intent) {
		Bundle extras =  intent.getExtras();
		if(extras!=null){
			String intentType = extras.getString("intentType");
			if("com.justep.cordova.plugin.weixin.Weixin".equals(intentType)){
				if(currentCallbackContext != null){
					currentCallbackContext.success(extras.getInt("weixinPayRespCode"));
				}
			}
		}
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		Log.i(TAG, "onNewIntent");
		this.onWeixinResp(intent);
	}
}
