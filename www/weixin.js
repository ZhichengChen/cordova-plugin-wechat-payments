/**
	var weixin = navigator.weixin;
    weixin.generatePrepayId(
        {"body":"x5",
         "feeType":"1",
         "notifyUrl":"http://www.justep.com",
         "totalFee":"1",
         "traceId":'123456',
         "tradeNo":"123456789"},function(prepayId){
                console.log('prepayId:' + prepayId);
                weixin.sendPayReq(prepayId,function(){
                    console.log('prepayId success');
                    alert("success");
                },function(message){
                    alert("sendPayReq:"+ message);
                });
         },function(message){
            alert("getPrepayId:" + message);
         });
	
	weixin.share({
	      message: {
	         title: "Message Title",
	         description: "Message Description(optional)",
	         mediaTagName: "Media Tag Name(optional)",
	         thumb: "http://YOUR_THUMBNAIL_IMAGE",
	         media: {
	             type: weixin.Type.WEBPAGE,   // webpage
	             webpageUrl: "https://www.justep.com"    // webpage
	         }
	     },
	     scene: weixin.Scene.TIMELINE   // share to Timeline
	  }, function () {
	     alert("Success");
	  }, function (reason) {
	      alert("Failed: " + reason);
	  });
	
**/
var exec = require('cordova/exec');

module.exports = {
    Scene: {
        SESSION:  0, // 聊天界面
        TIMELINE: 1, // 朋友圈
        FAVORITE: 2  // 收藏
    },
    Type: {
        APP:     1,
        EMOTION: 2,
        FILE:    3,
        IMAGE:   4,
        MUSIC:   5,
        VIDEO:   6,
        WEBPAGE: 7
    },
    share: function (message, onSuccess, onError) {
        exec(onSuccess, onError, "Weixin", "share", [message]);
    },
    getAccessToken: function(onSuccess,onError){
        var weixinPluginValue = localStorage.getItem('cordova.weixinPlugin');
        if(weixinPluginValue != null){
            weixinPluginValue = JSON.parse(weixinPluginValue);
            if(new Date().getTime()/1000 - weixinPluginValue.timeStamp/1000 > 7100){
                this.getRemoteAccessToken(onSuccess,onError);
            }else{
                if(onSuccess){
                    onSuccess.call(this,weixinPluginValue.accessToken);
                }
            }
        }else{
            this.getRemoteAccessToken(onSuccess,onError);
        }
    },
    getRemoteAccessToken:function(onSuccess,onError){
        exec(function(accessToken){
                var weixinPluginValue = {
                    accessToken:accessToken,
                    timeStamp:new Date().getTime()
                };
                localStorage.setItem('cordova.weixinPlugin',JSON.stringify(weixinPluginValue));
                if(onSuccess){
                    onSuccess(accessToken);
                }
            }, onError, "Weixin", "getAccessToken", []);
    },
    generatePrepayId: function(payInfo,onSuccess,onError){
        exec(onSuccess, onError, "Weixin", "generatePrepayId", [payInfo]);
    },
    sendPayReq: function(prepayId,onSuccess,onError){
        exec(onSuccess, onError, "Weixin", "sendPayReq", [{"prepayId":prepayId}]);
    }
};