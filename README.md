# cordova-plugin-wechat-payments

Wexin payments plugin import from http://wex5.com/

#Feature

Send WeChat payment request, only test the android version right now.

#Install

`cordova plugin add https://github.com/ZhichengChen/cordova-plugin-weixin`

#Requirements

1. Go to [WeChat Open Platfrom](https://open.weixin.qq.com), authentication WeChat developer;

2. After authenticationed, create a mobile application, remeber your **AppID**;

3. Modify the app's **application sign** and **application packagename** to yours then remeber them;

4. Apply the WeChat payments capability;

5. Your will received the [WeCaht Merchant Platform](https://pay.weixin.qq.com) account by email after the apply approved, follow the instraction in the email finish your authentication;

6. Fill the api key with your application sign, and remeber your **merchat id**;

#Usage

1. Open the `plugins/android.json`, change the $weixin_api_key to your *AppID*;

2. Open the `src/android/com/justep/base/Constants.java`, fill the PACKNAME, ACTIVITYCLASSNAME, APPNAME with proper value, and fill the APPID, PARTNERID, APIKEY with *AppId*, *merchat id*, *application sign*;

3. 

	  function randomString(len) {
	  　　var chars = '1234567890';
	  　　var maxPos = chars.length;
	  　　var pwd = '';
	  　　for (i = 0; i < len; i++) {
	  　　　　pwd += chars.charAt(Math.floor(Math.random() * maxPos));
	  　　}
	  　　return pwd;
	  }  

	  function pay() {
	    var weixin = navigator.weixin;
	    var notifyUrl = "http://www.justep.com";
	    var traceID = randomString(6);
	    var traceNo = randomString(9);

	    weixin.generatePrepayId({
	            "body" : "x5外卖",
	            "feeType" : "1",
	            "notifyUrl" : notifyUrl,
	            "totalFee" : "1",
	            "traceId" : traceID,
	            "tradeNo" : traceNo
	        }, function(prepayId) {
	            weixin.sendPayReq(prepayId, function(message) {
	                console.log(message);
	                var responseCode = parseInt(message);
	                if (responseCode === 0) {
	                    alert("微信支付成功");
	                } else if (!isNaN(responseCode)) {
	                    alert("微信支付失败(-13)"+responseCode);
	                } else {
	                    alert("微信支付失败(-10)");
	                }
	            }, function(message) {
	                alert("微信支付失败(-10)");
	            });
	        }, function(message) {
	            console.log(message);
	            alert("微信支付失败(-11)");
	        }
	    );
	  }

4. Make sure the package name is same with *application packagename*, and release your application with the keystore.

 #FAQ

 #TODO

 #LICENSE