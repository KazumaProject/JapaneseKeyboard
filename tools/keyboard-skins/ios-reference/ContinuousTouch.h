#import <UIKit/UIKit.h>
#import <XCTest/XCTest.h>
NS_ASSUME_NONNULL_BEGIN
@interface ContinuousTouch : NSObject
+ (void)sendPoints:(NSArray<NSValue *> *)points offsets:(NSArray<NSNumber *> *)offsets
       orientation:(UIInterfaceOrientation)orientation completion:(void (^)(NSError * _Nullable))completion;
@end
NS_ASSUME_NONNULL_END
