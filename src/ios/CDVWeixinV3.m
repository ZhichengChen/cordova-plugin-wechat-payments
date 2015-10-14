#import "CDVWeixinV3.h"
#import "WXUtil.h"
#import "WXHttpUtil.h"
#import "ApiXml.h"


@implementation CDVWeixinV3


#pragma mark "API"

-(void)pluginInitialize{
    CDVViewController *viewController = (CDVViewController *)self.viewController;
    self.app_id = [viewController.settings objectForKey:@"weixin_appid"];
    self.api_key = [viewController.settings objectForKey:@"weixin_api_key"];
    self.partner_id = [viewController.settings objectForKey:@"weixin_partner_id"];
    self.prepayUrl =@"https://api.mch.weixin.qq.com/pay/unifiedorder";
    
}


-(void) prepareForExec:(CDVInvokedUrlCommand *)command{
    [WXApi registerApp:self.app_id];
    self.currentCallbackId = command.callbackId;
    if (![WXApi isWXAppInstalled])
    {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"未安装微信"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        [self endForExec];
        return;
    }
}

-(NSDictionary *)checkArgs:(CDVInvokedUrlCommand *) command{
    // check arguments
    NSDictionary *params = [command.arguments objectAtIndex:0];
    if (!params)
    {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"参数错误"] callbackId:command.callbackId];
        
        [self endForExec];
        return nil;
    }
    return params;
}



-(void) endForExec{
    self.currentCallbackId = nil;
}


- (void)share:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *params = [self checkArgs:command];
    if(params == nil){
        return;
    }
    SendMessageToWXReq* req = [[SendMessageToWXReq alloc] init];
    // check the scene
    if ([params objectForKey:@"scene"])
    {
        req.scene = [[params objectForKey:@"scene"] integerValue];
    }else{
        req.scene = WXSceneTimeline;
    }
    // message or text?
    NSDictionary *message = [params objectForKey:@"message"];
    if (message){
        req.bText = NO;
        // async
        [self.commandDelegate runInBackground:^{
            req.message = [self buildSharingMessage:message];
            [WXApi sendReq:req];
        }];
    }else{
        req.bText = YES;
        req.text = [params objectForKey:@"text"];
        [WXApi sendReq:req];
    }
}


- (void)sendPayReq:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *params = [self checkArgs:command];
    if(params == nil){
        return;
    }
    NSString *prepayId = params[@"prepayId"];
    // 获取预支付订单id，调用微信支付sdk
    if (prepayId){
        
        NSString *timeStamp =[self genTimeStamp];
        // 调起微信支付
        PayReq *request   = [[PayReq alloc] init];
        //request.appid = self.app_id;
        request.partnerId = self.partner_id;
        request.prepayId  = prepayId;
        request.package   = @"Sign=WXPay";
        request.nonceStr = [self genNonceStr];
        request.timeStamp = [timeStamp intValue];
        
        
        // 构造参数列表
        NSMutableDictionary *params = [NSMutableDictionary dictionary];
        [params setObject:self.app_id forKey:@"appid"];
        [params setObject:request.nonceStr forKey:@"noncestr"];
        [params setObject:request.package forKey:@"package"];
        [params setObject:request.partnerId forKey:@"partnerid"];
        [params setObject:request.prepayId forKey:@"prepayid"];
        [params setObject:timeStamp forKey:@"timestamp"];
        request.sign = [self genSign:params];
        
        // 在支付之前，如果应用没有注册到微信，应该先调用 [WXApi registerApp:appId] 将应用注册到微信
        [WXApi sendReq:request];
    }
};

- (NSString *)genNonceStr
{
    return [WXUtil md5:[NSString stringWithFormat:@"%d", arc4random() % 10000]];
};

- (NSString *)genTimeStamp {
    return [NSString stringWithFormat:@"%.0f", [[NSDate date] timeIntervalSince1970]];
};
//提交预支付
- (void)generatePrepayId:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *prepayInfo = [self checkArgs:command];
    if(prepayInfo == nil){
        return;
    }
    NSString *prepayid = nil;
    
    //获取提交支付
    NSString *postData      = [self getProductArgs:prepayInfo];
    
    //发送请求post xml数据
    NSData *res = [WXHttpUtil httpSend:self.prepayUrl method:@"POST" data:postData];
    
    XMLHelper *xml  = [XMLHelper alloc];
    
    //开始解析
    [xml startParse:res];
    
    NSMutableDictionary *resParams = [xml getDict];
    
    //判断返回
    NSString *result_code   = [resParams objectForKey:@"result_code"];

    if( [result_code isEqualToString:@"SUCCESS"]) {
        //验证业务处理状态
        prepayid    = [resParams objectForKey:@"prepay_id"];
       
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:prepayid];
        [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
        return;
        
    }else{
        NSString *errcode = resParams[@"errcode"];
        if(errcode == nil){
            errcode = @"生成预支付订单失败";
        }
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errcode];
        [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
        
        
    }
};





    
    
    



- (void)onResp:(BaseResp *)resp{
    CDVPluginResult *result = nil;
    BOOL success = NO;
    if([resp isKindOfClass:[SendMessageToWXResp class]]){
        switch (resp.errCode)
        {
            case WXSuccess:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                success = YES;
            break;
            
            case WXErrCodeCommon:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"普通错误类型"];
            break;
            
            case WXErrCodeUserCancel:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"用户点击取消并返回"];
            break;
            
            case WXErrCodeSentFail:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"发送失败"];
            break;
            
            case WXErrCodeAuthDeny:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"授权失败"];
            break;
            
            case WXErrCodeUnsupport:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"微信不支持"];
            break;
        }
        if (!result)
        {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Unknown"];
        }
        [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
    }else if ([resp isKindOfClass:[PayResp class]])
    {
        PayResp *response = (PayResp *)resp;
        
        
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[NSString stringWithFormat:@"%d",response.errCode]];
        [self.commandDelegate sendPluginResult:result callbackId:[self currentCallbackId]];
    }
    [self endForExec];
}

#pragma mark "CDVPlugin Overrides"
- (void)handleOpenURL:(NSNotification *)notification{
    NSURL* url = [notification object];
    if ([url isKindOfClass:[NSURL class]] && [url.scheme isEqualToString:self.app_id])
    {
        [WXApi handleOpenURL:url delegate:self];
    }
}

- (WXMediaMessage *)buildSharingMessage:(NSDictionary *)message{
    WXMediaMessage *wxMediaMessage = [WXMediaMessage message];
    wxMediaMessage.title = [message objectForKey:@"title"];
    wxMediaMessage.description = [message objectForKey:@"description"];
    wxMediaMessage.mediaTagName = [message objectForKey:@"mediaTagName"];
    [wxMediaMessage setThumbImage:[self getUIImageFromURL:[message objectForKey:@"thumb"]]];
    
    
    // media parameters
    id mediaObject = nil;
    NSDictionary *media = [message objectForKey:@"media"];
    
    // check types
    NSInteger type = [[media objectForKey:@"type"] integerValue];
    switch (type)
    {
        case CDVWXSharingTypeApp:
        break;
    
        case CDVWXSharingTypeEmotion:
        break;
        
        case CDVWXSharingTypeFile:
        break;
        
        case CDVWXSharingTypeImage:
        break;
        
        case CDVWXSharingTypeMusic:
        break;
        
        case CDVWXSharingTypeVideo:
        break;
        
        case CDVWXSharingTypeWebPage:
        default:
        mediaObject = [WXWebpageObject object];
        ((WXWebpageObject *)mediaObject).webpageUrl = [media objectForKey:@"webpageUrl"];
    }

    wxMediaMessage.mediaObject = mediaObject;
    return wxMediaMessage;
}

- (UIImage *)getUIImageFromURL:(NSString *)thumb
{
    NSURL *thumbUrl = [NSURL URLWithString:thumb];
    NSData *data = nil;
    
    if ([thumbUrl isFileURL])
    {
        // local file
        data = [NSData dataWithContentsOfFile:thumb];
    }
    else
    {
        data = [NSData dataWithContentsOfURL:thumbUrl];
    }

    return [UIImage imageWithData:data];
}

// 构造订单参数列表
- (NSString *)getProductArgs:(NSDictionary *)payInfo{
        NSString *nonceStr = [self genNonceStr];
        
        // 构造订单参数列表
        NSMutableDictionary *packageParams = [NSMutableDictionary dictionary];
        [packageParams setObject:self.app_id forKey:@"appid"];
        [packageParams setObject:payInfo[@"body"] forKey:@"body"];
        [packageParams setObject:self.partner_id forKey:@"mch_id"];
        [packageParams setObject:nonceStr forKey:@"nonce_str"];
        [packageParams setObject:payInfo[@"notifyUrl"] forKey:@"notify_url"];
        [packageParams setObject:payInfo[@"tradeNo"] forKey:@"out_trade_no"];
        [packageParams setObject:[WXUtil getIPAddress:YES] forKey:@"spbill_create_ip"];
        [packageParams setObject:payInfo[@"totalFee"] forKey:@"total_fee"];
        [packageParams setObject:@"APP" forKey:@"trade_type"];
        [packageParams setObject:[self genSign:packageParams] forKey:@"sign"];
        
    return [self toXml:packageParams];
    
};
    
- (NSString*)toXml:(NSDictionary*)dictionary{
        NSMutableString *xml = [[NSMutableString alloc] initWithString:@""];
        [xml appendString:@"<xml>"];
        NSArray *arr = [dictionary allKeys];
        for(int i=0; i < [arr count]; i++){
            [xml appendString:@"<"];
            [xml appendString:arr[i]];
            [xml appendString:@">"];
            [xml appendString:[dictionary objectForKey:arr[i]]];
            [xml appendString:@"</"];
            [xml appendString:arr[i]];
            [xml appendString:@">"];
        }
    [xml appendString:@"</xml>"];
        return xml;
}
    
- (NSString *)genSign:(NSDictionary *)packageParams{
        NSArray *keys = [packageParams allKeys];
        NSArray *sortedKeys = [keys sortedArrayUsingComparator:^NSComparisonResult(id obj1, id obj2) {
            return [obj1 compare:obj2 options:NSNumericSearch];
        }];
        
        // 生成 packageSign
        NSMutableString *package = [NSMutableString string];
        for (NSString *key in sortedKeys) {
            [package appendString:key];
            [package appendString:@"="];
            [package appendString:[packageParams objectForKey:key]];
            [package appendString:@"&"];
        }
        
        [package appendString:@"key="];
        [package appendString:self.api_key];
        // 进行md5摘要前,params内容为原始内容,未经过url encode处理
        NSString *packageSign = [[WXUtil md5:[package copy]] uppercaseString];
        return packageSign;
}
    
@end