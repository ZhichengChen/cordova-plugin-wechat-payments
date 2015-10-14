#import <Cordova/CDV.h>
#import "WXApi.h"

enum  CDVWeixinSharingTypeV3 {
    CDVWXSharingTypeApp = 1,
    CDVWXSharingTypeEmotion,
    CDVWXSharingTypeFile,
    CDVWXSharingTypeImage,
    CDVWXSharingTypeMusic,
    CDVWXSharingTypeVideo,
    CDVWXSharingTypeWebPage
};

@interface CDVWeixinV3:CDVPlugin <WXApiDelegate>

@property (nonatomic, strong) NSString *currentCallbackId;
@property (nonatomic, strong) NSString *app_id;
@property (nonatomic, strong) NSString *partner_id;
@property (nonatomic, strong) NSString *api_key;
@property (nonatomic, strong) NSString *prepayUrl;



/**
 partner_key = webView.getProperty("partner_key", "");
 partner_id = webView.getProperty("partner_id", "");
 app_secret = webView.getProperty("app_secret", "");
 app_key = webView.getProperty("app_key", "");
 **/

- (void)share:(CDVInvokedUrlCommand *)command;
- (WXMediaMessage *)buildSharingMessage:(NSDictionary *)message;
- (NSString *)genNonceStr;
- (NSString *)genTimeStamp;
- (NSString *)genSign:(NSDictionary *)packageParams;
- (NSString *)getProductArgs:(NSDictionary *)payInfo;
- (NSString*)toXml:(NSDictionary*)dictionary;

- (void)generatePrepayId:(CDVInvokedUrlCommand *)command;
- (void)sendPayReq:(CDVInvokedUrlCommand *)command;

@end
