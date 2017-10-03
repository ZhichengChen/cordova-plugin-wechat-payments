[English](README.md)

# cordova-plugin-wechat-payments

微信支付插件，参考http://wex5.com/

# 功能

Send WeChat payment request, only test the android version right now.
发起微信支付，目前仅在android 测试通过。

# 安装

`cordova plugin add https://github.com/ZhichengChen/cordova-plugin-wechat-payments`

# 准备

1. 访问[微信开放平台](https://open.weixin.qq.com)网站，认证开发者;

2. 认证通过之后，创建一个应用，记住你的*AppID*;

3. *应用签名*和*应用包名*填写你所开发的APP 的值，记住它们；

4. 申请微信支付能力；

5. 申请通过后你会收到一封邮件，内含[微信商家平台](https://pay.weixin.qq.com)的账户信息，按照邮件的指示完成商户认证；

6. 在[这个页面里](https://pay.weixin.qq.com/index.php/core/cert/api_cert)，点击设置密钥，API密钥的位置填写32 位密钥，记住你的*商户ID* 和*API密钥*；

# 用例

1. 打开`plugins/android.json`，将$weixin_api_key 修改成你的 *AppID*；

2. 打开`src/android/com/justep/base/Constants.java`, 将 PACKNAME, ACTIVITYCLASSNAME, APPNAME 赋上你的APP 的值，将APPID, PARTNERID, APIKEY 赋值 *AppID*, *商户ID*, *API密钥*;

3. Js 代码

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

4. 测试时请确包名和上面的值一致，请用你的keystore 打包后在使你的应用拥有正确的签名后在测试；

 # FAQ

 # TODO

 # LICENSE
