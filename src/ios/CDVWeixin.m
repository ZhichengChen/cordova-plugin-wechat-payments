#import "CDVWeixin.h"
#import "WXUtil.h"
#import "WXHttpUtil.h"
#import  "PayOrder.h"

#define BASE_URL @"https://api.weixin.qq.com"

@implementation CDVWeixin



#pragma mark "API"

-(void)pluginInitialize{
    CDVViewController *viewController = (CDVViewController *)self.viewController;
    self.app_id = [viewController.settings objectForKey:@"weixinappid"];
    self.app_key = [viewController.settings objectForKey:@"app_key"];
    self.app_secret = [viewController.settings objectForKey:@"app_secret"];
    self.partner_key = [viewController.settings objectForKey:@"partner_key"];
    self.partner_id = [viewController.settings objectForKey:@"partner_id"];
    
    self.payOrderList = [NSMutableDictionary dictionary];
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

- (void)getAccessToken:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    [self.commandDelegate runInBackground:^{
        NSString *tokenUrl = @"cgi-bin/token";
        NSDictionary *param = @{@"grant_type":@"client_credential", @"appid":self.app_id, @"secret":self.app_secret};
        [WXHttpUtil doGetWithUrl:BASE_URL
                            path:tokenUrl
                          params:param
                        callback:^(BOOL isSuccessed, NSDictionary *result){
                            if(isSuccessed){
                                NSString *accessToken = result[@"access_token"];
                                if (accessToken != nil) {
                                    CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString:accessToken];
                                    [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                                }else{
                                    NSString *errcode = result[@"errcode"];
                                    if(errcode == nil){
                                        errcode = @"获取accessToken失败";
                                    }
                                    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errcode];
                                    [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                                }
                            }else{
                                NSString *errcode = result[@"errcode"];
                                CDVPluginResult* result = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString:errcode];
                                [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                            }
                            [self endForExec];
                        }];
    }];
};


- (void)generatePrepayId:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *prepayInfo = [self checkArgs:command];
    if(prepayInfo == nil){
        return;
    }
    NSString *accessToken = prepayInfo[@"accessToken"];
    NSString *prepayIdUrl = [NSString stringWithFormat:@"pay/genprepay?access_token=%@", accessToken];
    
    // 拼接详细的订单数据
    NSDictionary *postDict = [self getProductArgs:prepayInfo];
    
    [WXHttpUtil doPostWithUrl:BASE_URL
                       path:prepayIdUrl
                     params:postDict
                   callback:^(BOOL isSuccessed, NSDictionary *result){
                       if(isSuccessed){
                           
                           NSString *prePayId = result[@"prepayid"];
                           if(prePayId != nil){
                               CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:prePayId];
                               PayOrder *payOrder = [[PayOrder alloc] init];
                               [payOrder setPrepayId:prePayId];
                               [payOrder setTimeStamp:postDict[@"timestamp"]];
                               [payOrder setNonceStr:postDict[@"noncestr"]];
                               [payOrder setAccessToken:accessToken];
                               [payOrder setPackageValue:postDict[@"package"]];
                               [self.payOrderList setValue:payOrder forKey:prePayId];
                               [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                               
                           }else{
                               NSString *errcode = result[@"errcode"];
                               if(errcode == nil){
                                   errcode = @"生成预支付订单失败";
                               }
                               CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errcode];
                               
                               [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                           }
                           
                       }else{
                           
                           CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"生成预支付订单失败"];
                           [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
                       }
                       [self endForExec];
                   }];
};

- (void)sendPayReq:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *params = [self checkArgs:command];
    if(params == nil){
        return;
    }
    NSString *prepayId = params[@"prepayId"];
    // 获取预支付订单id，调用微信支付sdk
    if (prepayId){
        NSLog(@"--- PrePayId: %@", prepayId);
        PayOrder *payOrder = self.payOrderList[prepayId];
        // 调起微信支付
        PayReq *request   = [[PayReq alloc] init];
        request.partnerId = self.partner_id;
        request.prepayId  = prepayId;
        request.package   = @"Sign=WXPay";
        request.nonceStr  = [payOrder nonceStr];
        request.timeStamp = [[payOrder timeStamp] intValue];
        
        // 构造参数列表
        NSMutableDictionary *params = [NSMutableDictionary dictionary];
        [params setObject:self.app_id forKey:@"appid"];
        [params setObject:self.app_key forKey:@"appkey"];
        [params setObject:request.nonceStr forKey:@"noncestr"];
        [params setObject:request.package forKey:@"package"];
        [params setObject:request.partnerId forKey:@"partnerid"];
        [params setObject:request.prepayId forKey:@"prepayid"];
        [params setObject:[payOrder timeStamp] forKey:@"timestamp"];
        request.sign = [self genSign:params];
        
        // 在支付之前，如果应用没有注册到微信，应该先调用 [WXApi registerApp:appId] 将应用注册到微信
        [WXApi sendReq:request];
    }
};


- (NSString *)genTimeStamp
{
    return [NSString stringWithFormat:@"%.0f", [[NSDate date] timeIntervalSince1970]];
}

- (NSString *)genNonceStr
{
    return [WXUtil md5:[NSString stringWithFormat:@"%d", arc4random() % 10000]];
}

- (NSString *)genPackage:(NSDictionary *)packageParams{
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
    [package appendString:self.partner_key]; // 注意:不能hardcode在客户端,建议genPackage这个过程都由服务器端完成
    
    // 进行md5摘要前,params内容为原始内容,未经过url encode处理
    NSString *packageSign = [[WXUtil md5:[package copy]] uppercaseString];
    package = nil;
    
    // 生成 packageParamsString
    NSString *value = nil;
    package = [NSMutableString string];
    for (NSString *key in sortedKeys)
    {
        [package appendString:key];
        [package appendString:@"="];
        value = [packageParams objectForKey:key];
        
        // 对所有键值对中的 value 进行 urlencode 转码
        value = (NSString *)CFBridgingRelease(CFURLCreateStringByAddingPercentEscapes(kCFAllocatorDefault, (CFStringRef)value, nil, (CFStringRef)@"!*'&=();:@+$,/?%#[]", kCFStringEncodingUTF8));
        
        [package appendString:value];
        [package appendString:@"&"];
    }
    NSString *packageParamsString = [package substringWithRange:NSMakeRange(0, package.length - 1)];
    
    NSString *result = [NSString stringWithFormat:@"%@&sign=%@", packageParamsString, packageSign];
    
    NSLog(@"--- Package: %@", result);
    
    return result;
}

// 签名
- (NSString *)genSign:(NSDictionary *)signParams
{
    // 排序
    NSArray *keys = [signParams allKeys];
    NSArray *sortedKeys = [keys sortedArrayUsingComparator:^NSComparisonResult(id obj1, id obj2) {
        return [obj1 compare:obj2 options:NSNumericSearch];
    }];
    
    // 生成
    NSMutableString *sign = [NSMutableString string];
    for (NSString *key in sortedKeys) {
        [sign appendString:key];
        [sign appendString:@"="];
        [sign appendString:[signParams objectForKey:key]];
        [sign appendString:@"&"];
    }
    NSString *signString = [[sign copy] substringWithRange:NSMakeRange(0, sign.length - 1)];
    
    NSString *result = [WXUtil sha1:signString];
    NSLog(@"--- Gen sign: %@", result);
    return result;
}

// 构造订单参数列表
- (NSDictionary *)getProductArgs:(NSDictionary *)payInfo{
    NSString *timeStamp = [self genTimeStamp];
    NSString *nonceStr = [self genNonceStr];
    
    
    NSMutableDictionary *params = [NSMutableDictionary dictionary];
    [params setObject:self.app_id forKey:@"appid"];
    [params setObject:self.app_key forKey:@"appkey"];
    [params setObject:nonceStr forKey:@"noncestr"];
    [params setObject:timeStamp forKey:@"timestamp"];
    [params setObject:payInfo[@"traceId"] forKey:@"traceid"];
    // 构造订单参数列表
    NSMutableDictionary *packageParams = [NSMutableDictionary dictionary];
    [packageParams setObject:@"WX" forKey:@"bank_type"];
    [packageParams setObject:payInfo[@"body"] forKey:@"body"];
    [packageParams setObject:payInfo[@"feeType"] forKey:@"fee_type"];
    [packageParams setObject:@"UTF-8" forKey:@"input_charset"];
    [packageParams setObject:payInfo[@"notifyUrl"] forKey:@"notify_url"];
    [packageParams setObject:payInfo[@"tradeNo"] forKey:@"out_trade_no"];
    [packageParams setObject:self.partner_id forKey:@"partner"];
    [packageParams setObject:[WXUtil getIPAddress:YES] forKey:@"spbill_create_ip"];
    [packageParams setObject:payInfo[@"totalFee"] forKey:@"total_fee"];    // 1 =＝ ¥0.01
    [params setObject:[self genPackage:packageParams] forKey:@"package"];
    [params setObject:[self genSign:params] forKey:@"app_signature"];
    [params setObject:@"sha1" forKey:@"sign_method"];
    return params;
}

#pragma mark - 支付结果
- (void)getOrderPayResult:(NSNotification *)notification
{
    if ([notification.object isEqualToString:@"success"])
    {
        NSLog(@"success: 支付成功");
    }
    else
    {
        NSLog(@"fail: 支付失败");
    }
}


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

@end
