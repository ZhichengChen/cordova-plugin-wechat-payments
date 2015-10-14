#import "WXHttpUtil.h"
#import "AFNetworking.h"
#import "JSONKit.h"

@implementation WXHttpUtil

+ (void)doGetWithUrl:(NSString *)url path:(NSString *)path params:(NSDictionary *)params callback:(WXHttpCallback) callback
{
    AFHTTPClient *httpClient = [[AFHTTPClient alloc] initWithBaseURL:[NSURL URLWithString:url]];
    [httpClient getPath:path
             parameters:params
                success:^(AFHTTPRequestOperation *operation, id responseObject){
                    
                    NSString *responseJson = [[NSString alloc] initWithData:responseObject encoding:NSUTF8StringEncoding];
                    NSLog(@"responseJSON:%@",responseJson);
                    if (responseJson)
                    {
                        NSDictionary *result = [responseJson objectFromJSONString];
                        callback(YES, result);
                    }
                    else
                    {
                        callback(NO, nil);
                    }
                }
                failure:^(AFHTTPRequestOperation *operation, NSError *error){
                    callback(NO, nil);
                }];
}

+ (void)doPostWithUrl:(NSString *)url path:(NSString *)path params:(NSDictionary *)params callback:(WXHttpCallback)callback
{
    AFHTTPClient *httpClient = [[AFHTTPClient alloc] initWithBaseURL:[NSURL URLWithString:url]];
    httpClient.parameterEncoding = AFJSONParameterEncoding;
    [httpClient postPath:path
              parameters:params
                 success:^(AFHTTPRequestOperation *operation, id responseObject){
                    
                     NSString *responseJson = [[NSString alloc] initWithData:responseObject encoding:NSUTF8StringEncoding];
                     NSLog(@"responseJSON:%@",responseJson);
                     if (responseJson){
                         NSDictionary *result = [responseJson objectFromJSONString];
                         callback(YES, result);
                     }
                     else
                     {
                         callback(NO, nil);
                     }
                 }
                 failure:^(AFHTTPRequestOperation *operation, NSError *error){
                    callback(NO, nil);
                }];
}

+ (void)getImageWithUrl:(NSString *)url callback:(WXHttpCallback)callback
{
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url]];
    AFImageRequestOperation *requestOperation = [[AFImageRequestOperation alloc] initWithRequest:request];
    
    [requestOperation setCompletionBlockWithSuccess:^(AFHTTPRequestOperation *operation, id responseObject) {
        
        UIImage *image = responseObject;
        NSDictionary *result = @{@"image":image};
        callback(YES, result);
    } failure:^(AFHTTPRequestOperation *operation, NSError *error) {
        callback(NO, nil);
    }];
    [requestOperation start];
}


//http 请求
+(NSData *) httpSend:(NSString *)url method:(NSString *)method data:(NSString *)data
{
    NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:[NSURL URLWithString:url] cachePolicy:NSURLRequestReloadIgnoringLocalCacheData timeoutInterval:5];
    //设置提交方式
    [request setHTTPMethod:method];
    //设置数据类型
    [request addValue:@"text/xml" forHTTPHeaderField:@"Content-Type"];
    //设置编码
    [request setValue:@"UTF-8" forHTTPHeaderField:@"charset"];
    //如果是POST
    [request setHTTPBody:[data dataUsingEncoding:NSUTF8StringEncoding]];
    
    NSError *error;
    //将请求的url数据放到NSData对象中
    NSData *response = [NSURLConnection sendSynchronousRequest:request returningResponse:nil error:&error];
    return response;
    //return [[NSString alloc] initWithData:response encoding:NSUTF8StringEncoding];
}

@end
