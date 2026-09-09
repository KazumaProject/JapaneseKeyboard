#import "ContinuousTouch.h"
// Diagnostic XCTest runner only. See Appium's input-events guide for these runtime interfaces.
// No private API is linked into the reference application or Android product.
@interface NSObject (ContinuousTouchRuntime)
- (id)initForTouchAtPoint:(CGPoint)point offset:(double)offset;
- (void)moveToPoint:(CGPoint)point atOffset:(double)offset;
- (void)liftUpAtOffset:(double)offset;
- (id)initWithName:(NSString *)name interfaceOrientation:(NSInteger)orientation;
- (void)addPointerEventPath:(id)path;
+ (id)sharedSession;
- (void)synthesizeEvent:(id)event completion:(void (^)(BOOL, NSError *))completion;
@end
@implementation ContinuousTouch
+ (void)sendPoints:(NSArray<NSValue *> *)points offsets:(NSArray<NSNumber *> *)offsets
       orientation:(UIInterfaceOrientation)orientation completion:(void (^)(NSError *))completion {
    NSParameterAssert(points.count > 1 && points.count == offsets.count);
    Class pathClass=NSClassFromString(@"XCPointerEventPath");
    Class recordClass=NSClassFromString(@"XCSynthesizedEventRecord");
    Class sessionClass=NSClassFromString(@"XCTRunnerDaemonSession");
    NSAssert(pathClass && recordClass && sessionClass, @"Required XCTest input interfaces unavailable");
    id path=[[pathClass alloc] initForTouchAtPoint:points[0].CGPointValue offset:0];
    for (NSUInteger i=1;i<points.count;i++) {
        NSAssert(offsets[i].doubleValue > offsets[i-1].doubleValue, @"Offsets must increase");
        [path moveToPoint:points[i].CGPointValue atOffset:offsets[i].doubleValue];
    }
    [path liftUpAtOffset:offsets.lastObject.doubleValue+0.25];
    id record=[[recordClass alloc] initWithName:@"continuous-kana-reference" interfaceOrientation:orientation];
    [record addPointerEventPath:path];
    [[sessionClass sharedSession] synthesizeEvent:record completion:^(BOOL success, NSError *error) {
        completion(error ?: (success ? nil : [NSError errorWithDomain:@"ContinuousTouch" code:1 userInfo:nil]));
    }];
}
@end
