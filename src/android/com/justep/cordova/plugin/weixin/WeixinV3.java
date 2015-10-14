package com.justep.cordova.plugin.weixin;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.sourceforge.simcpux.MD5;
import net.sourceforge.simcpux.Util;

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
import org.xmlpull.v1.XmlPullParser;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXTextObject;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.modelpay.PayReq;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;
import com.justep.x5.base.Constants;

public class WeixinV3 extends CordovaPlugin{
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
	
	private static String app_id;
	
	private static String partner_id;
	private static String api_key;
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
		}else if(action.equals("generatePrepayId")){
			return generatePrepayId(args);
		}else if(action.equals("sendPayReq")){
			return sendPayReq(args);
		}
		return false;
	}
	
	
	
	private boolean generatePrepayId(JSONArray args){
		//pay
		try {
			JSONObject prepayInfo = args.getJSONObject(0);
			String packageParams = genProductArgs(prepayInfo);
			new GetPrepayIdTask(packageParams).execute();
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
			api = WXAPIFactory.createWXAPI(webView.getContext(), app_id, true);
			api.registerApp(app_id);
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
	
	
	private String genProductArgs(JSONObject args) {
		try {
			String	nonceStr = genNonceStr();
            List<NameValuePair> packageParams = new LinkedList<NameValuePair>();
			packageParams.add(new BasicNameValuePair("appid", app_id));
			packageParams.add(new BasicNameValuePair("body", args.getString("body")));
			packageParams.add(new BasicNameValuePair("mch_id", partner_id));
			packageParams.add(new BasicNameValuePair("nonce_str", nonceStr));
			packageParams.add(new BasicNameValuePair("notify_url", args.getString("notifyUrl")));
			packageParams.add(new BasicNameValuePair("out_trade_no",args.getString("tradeNo")));
			packageParams.add(new BasicNameValuePair("spbill_create_ip",Util.getIpAddress()));
			packageParams.add(new BasicNameValuePair("total_fee", args.getString("totalFee")));
			packageParams.add(new BasicNameValuePair("trade_type", "APP"));

			String sign = genPackageSign(packageParams);
			packageParams.add(new BasicNameValuePair("sign", sign));
		    String xmlstring =toXml(packageParams);
			return new String(xmlstring.toString().getBytes(), "ISO8859-1");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private String toXml(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();
		sb.append("<xml>");
		for (int i = 0; i < params.size(); i++) {
			sb.append("<"+params.get(i).getName()+">");


			sb.append(params.get(i).getValue());
			sb.append("</"+params.get(i).getName()+">");
		}
		sb.append("</xml>");

		Log.e("orion",sb.toString());
		return sb.toString();
	}
	
	
	
	private class GetPrepayIdTask extends AsyncTask<Void, Void, Map<String,String>> {
		String packageParams;
		
		public GetPrepayIdTask(String packageParams) {
			this.packageParams = packageParams;
		}

		@Override
		protected void onPostExecute(Map<String,String> result) {
			Toast.makeText(cordova.getActivity(), "正在生成预支付订单", Toast.LENGTH_LONG).show();
			String prepayId = result.get("prepay_id");
			if(prepayId !=null && !prepayId.equals("")){
				currentCallbackContext.success(prepayId);
			}else{
				currentCallbackContext.error(result.get("return_code"));
			}
		}
		
		@Override
		protected void onPreExecute() {
			Log.i(TAG, "正在获取订单id");
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
		}

		@Override
		protected Map<String,String>  doInBackground(Void... params) {
			String url = String.format("https://api.mch.weixin.qq.com/pay/unifiedorder");
			byte[] buf = Util.httpPost(url, packageParams);
			String content = new String(buf);
			Map<String,String> xml=decodeXml(content);
			return xml;
		}
	}
	
	public Map<String,String> decodeXml(String content) {
		try {
			Map<String, String> xml = new HashMap<String, String>();
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(new StringReader(content));
			int event = parser.getEventType();
			while (event != XmlPullParser.END_DOCUMENT) {

				String nodeName=parser.getName();
				switch (event) {
					case XmlPullParser.START_DOCUMENT:

						break;
					case XmlPullParser.START_TAG:

						if("xml".equals(nodeName)==false){
							//实例化student对象
							xml.put(nodeName,parser.nextText());
						}
						break;
					case XmlPullParser.END_TAG:
						break;
				}
				event = parser.next();
			}

			return xml;
		} catch (Exception e) {
			currentCallbackContext.error("获取与支付订单失败！");
		}
		return null;

	}
	
	private String genNonceStr() {
		Random random = new Random();
		return MD5.getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
	}
	
	private long genTimeStamp() {
		return System.currentTimeMillis() / 1000;
	}
	
	private String genPackageSign(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();
		
		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(api_key);
		
		String packageSign = MD5.getMessageDigest(sb.toString().getBytes(Charset.forName("utf-8"))).toUpperCase();
		
		return packageSign;
	}
	
	private String genAppSign(List<NameValuePair> params) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < params.size(); i++) {
			sb.append(params.get(i).getName());
			sb.append('=');
			sb.append(params.get(i).getValue());
			sb.append('&');
		}
		sb.append("key=");
		sb.append(api_key);
        
		String appSign = MD5.getMessageDigest(sb.toString().getBytes()).toUpperCase();
		return appSign;
	}
	
	private PayReq genPayReq(String prepay_id) {
		PayReq req = new PayReq();
		req.appId = app_id;
		req.partnerId = partner_id;
		req.prepayId = prepay_id;
		req.packageValue = "Sign=WXPay";
		req.nonceStr = genNonceStr();
		req.timeStamp = String.valueOf(genTimeStamp());


		List<NameValuePair> signParams = new LinkedList<NameValuePair>();
		signParams.add(new BasicNameValuePair("appid", req.appId));
		signParams.add(new BasicNameValuePair("noncestr", req.nonceStr));
		signParams.add(new BasicNameValuePair("package", req.packageValue));
		signParams.add(new BasicNameValuePair("partnerid", req.partnerId));
		signParams.add(new BasicNameValuePair("prepayid", req.prepayId));
		signParams.add(new BasicNameValuePair("timestamp", req.timeStamp));

		req.sign = genAppSign(signParams);
		return req;
	}
	private void sendPayReq(String prepayId) {
		api.registerApp(app_id);
		
		final PayReq req = genPayReq(prepayId);
		cordova.getThreadPool().execute(new Runnable() {
			@Override
			public void run() {
				Boolean sended = api.sendReq(req);
				if(!sended){
					currentCallbackContext.error("发送支付请求失败");
				}
			}
		});
	}
	
	

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		app_id = Constants.APPID;//webView.getProperty("weixin_appid", "");
		partner_id = Constants.PARTNERID;//webView.getProperty("weixin_partner_id", "");
		api_key = Constants.APIKEY;//webView.getProperty("weixin_api_key", "");
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
