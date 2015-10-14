#import <Cordova/CDV.h>
#import "WXApi.h"

enum  CDVWeixinSharingType {
    CDVWXSharingTypeApp = 1,
    CDVWXSharingTypeEmotion,
    CDVWXSharingTypeFile,
    CDVWXSharingTypeImage,
    CDVWXSharingTypeMusic,
    CDVWXSharingTypeVideo,
    CDVWXSharingTypeWebPage
};

@interface CDVWeixin:CDVPlugin <WXApiDelegate>

@property (nonatomic, strong) NSString *currentCallbackId;
@property (nonatomic, strong) NSString *app_id;
@property (nonatomic, strong) NSString *partner_key;
@property (nonatomic, strong) NSString *partner_id;
@property (nonatomic, strong) NSString *app_secret;
@property (nonatomic, strong) NSString *app_key;
@property (nonatomic, strong) NSMutableDictionary* payOrderList;


/**
 partner_key = webView.getProperty("partner_key", "");
 partner_id = webView.getProperty("partner_id", "");
 app_secret = webView.getProperty("app_secret", "");
 app_key = webView.getProperty("app_key", "");
 **/

- (void)share:(CDVInvokedUrlCommand *)command;
- (void)getAccessToken:(CDVInvokedUrlCommand *)command;
- (void)generatePrepayId:(CDVInvokedUrlCommand *)command;
- (void)sendPayReq:(CDVInvokedUrlCommand *)command;

@end
