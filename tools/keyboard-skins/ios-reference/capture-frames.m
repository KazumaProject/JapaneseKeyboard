#import <Foundation/Foundation.h>
#import <IOSurface/IOSurface.h>
#import <QuartzCore/QuartzCore.h>
#import <dlfcn.h>
#import <signal.h>
#import <zlib.h>
static volatile sig_atomic_t stopped=0;
static void stopCapture(int value){stopped=1;}
@interface NSObject (SimProbe)
+ (id)sharedServiceContextForDeveloperDir:(id)p error:(NSError **)e;
- (id)defaultDeviceSetWithError:(NSError **)e;
- (NSArray *)availableDevices;
- (NSUUID *)UDID;
- (id)io;
- (NSArray *)ioPorts;
- (id)descriptor;
- (id)state;
- (NSUInteger)displayClass;
- (id)framebufferSurface;
- (void)registerScreenCallbacksWithUUID:(NSUUID *)token callbackQueue:(dispatch_queue_t)queue frameCallback:(void(^)(void))frame surfacesChangedCallback:(void(^)(id,id))surfaces propertiesChangedCallback:(void(^)(id))properties;
- (void)unregisterScreenCallbacksWithUUID:(NSUUID *)token;
@end
// Measurement-only host tool. Uses the Xcode CoreSimulator display interface described
// by https://github.com/facebook/idb/blob/main/PrivateHeaders/CoreSimDeviceIO/SimScreen-Protocol.h.
// No private framework or captured glyph asset is linked into the Android product.
int main(int argc,const char **argv){@autoreleasepool{
 if(argc!=3){fprintf(stderr,"Usage: capture-frames SIMULATOR_UDID OUTPUT.gz\n");return 64;}
 signal(SIGINT,stopCapture);signal(SIGTERM,stopCapture);
 gzFile output=gzopen(argv[2],"wb1");if(!output)return 65;
 dispatch_queue_t writer=dispatch_queue_create("skin.raw-frame-writer",DISPATCH_QUEUE_SERIAL);
 // RGB samples preserve the exact reference ROIs, without video encoding. Row-major,
 // stride 3, with a fixed binary header for direct host-monotonic callback timestamps.
 static const int boxes[][4]={{30,1788,185,1825},{250,1788,415,1825},{1138,1788,1290,1825},
  {531,2154,789,2322},{272,2154,530,2322},{789,2154,1048,2322},
  {531,1956,789,2124},{531,2353,789,2521},{320,2000,480,2120},
  {272,1940,1048,2540},{220,1510,610,2300}};
 const int regionCount=sizeof(boxes)/sizeof(boxes[0]);
 NSMutableArray *regions=[NSMutableArray array];size_t sampleBytes=0;
 for(int i=0;i<regionCount;i++){int w=(boxes[i][2]-boxes[i][0]+2)/3,h=(boxes[i][3]-boxes[i][1]+2)/3;
  [regions addObject:@{@"box":@[@(boxes[i][0]),@(boxes[i][1]),@(boxes[i][2]),@(boxes[i][3])],@"width":@(w),@"height":@(h)}];sampleBytes+=w*h*3;}
 NSData *meta=[NSJSONSerialization dataWithJSONObject:@{@"version":@1,@"regions":regions,@"stride":@3,@"sampleBytes":@(sampleBytes),@"header":@"little-endian <ddIIII: callbackTime, sampleEndTime, counter, frameSerial, tickMs, eventSerial"} options:0 error:nil];
 uint32_t length=(uint32_t)meta.length;
 if(gzwrite(output,&length,4)!=4||gzwrite(output,meta.bytes,(unsigned)meta.length)!=(int)meta.length){gzclose(output);return 65;}

 dlopen("/Library/Developer/PrivateFrameworks/CoreSimulator.framework/CoreSimulator",RTLD_NOW);
 NSError *error=nil;
 id context=[NSClassFromString(@"SimServiceContext") sharedServiceContextForDeveloperDir:@"/Applications/Xcode.app/Contents/Developer" error:&error];
 id set=[context defaultDeviceSetWithError:&error];id chosen=nil;
 for(id device in [set availableDevices])if([[[device UDID] UUIDString]isEqualToString:[NSString stringWithUTF8String:argv[1]]])chosen=device;
 if(!chosen){NSLog(@"no device %@",error);gzclose(output);return 1;}
 id screen=nil;
 for(id port in [[chosen io]ioPorts]){NSObject *d=[port descriptor];@try{if([d respondsToSelector:@selector(framebufferSurface)]&&[[d state]displayClass]==0){screen=d;break;}}@catch(NSException *e){}}
 if(!screen){NSLog(@"no screen");gzclose(output);return 2;}
 __block IOSurfaceRef surface=NULL;__block int count=0;__block BOOL invalidFormat=NO;__block BOOL writeFailed=NO;
 dispatch_queue_t queue=dispatch_queue_create("skin.raw-frame-probe",dispatch_queue_attr_make_with_qos_class(DISPATCH_QUEUE_SERIAL,QOS_CLASS_USER_INTERACTIVE,0));
 NSUUID *token=[NSUUID UUID];
 [screen registerScreenCallbacksWithUUID:token callbackQueue:queue frameCallback:^{
   double now=CACurrentMediaTime();if(!surface)return;
   IOSurfaceLock(surface,kIOSurfaceLockReadOnly,NULL);
   size_t w=IOSurfaceGetWidth(surface),h=IOSurfaceGetHeight(surface),stride=IOSurfaceGetBytesPerRow(surface);
   uint8_t *base=IOSurfaceGetBaseAddress(surface);
   if(w!=1320||h!=2868||IOSurfaceGetPixelFormat(surface)!=1111970369){fprintf(stderr,"Unexpected screen format\n");invalidFormat=YES;stopped=1;IOSurfaceUnlock(surface,kIOSurfaceLockReadOnly,NULL);return;}
   NSMutableData *record=[NSMutableData dataWithLength:32+sampleBytes];uint8_t *bytes=record.mutableBytes;
   uint32_t values[4]={(uint32_t)++count,0,0,0};
   for(int row=0;row<3;row++)for(int bit=0;bit<32;bit++){
    uint8_t *pixel=base+(975+30*row)*stride+(75+30*bit)*4;
    if(pixel[0]+pixel[1]+pixel[2]>384)values[1+row]|=1u<<bit;
   }
   uint8_t *dest=bytes+32;
   for(int region=0;region<regionCount;region++)for(int y=boxes[region][1];y<boxes[region][3];y+=3)for(int x=boxes[region][0];x<boxes[region][2];x+=3){
    uint8_t *pixel=base+y*stride+x*4;*dest++=pixel[2];*dest++=pixel[1];*dest++=pixel[0];
   }
   IOSurfaceUnlock(surface,kIOSurfaceLockReadOnly,NULL);
   double end=CACurrentMediaTime();memcpy(bytes,&now,8);memcpy(bytes+8,&end,8);memcpy(bytes+16,values,16);
   dispatch_async(writer,^{if(gzwrite(output,record.bytes,(unsigned)record.length)!=(int)record.length){writeFailed=YES;stopped=1;}});

 } surfacesChangedCallback:^(id first,id second){if(surface)CFRelease(surface);surface=first?(IOSurfaceRef)CFRetain((__bridge CFTypeRef)first):NULL;} propertiesChangedCallback:^(id state){}];
 fprintf(stderr,"Raw frame capture ready\n");while(!stopped)[[NSRunLoop currentRunLoop]runUntilDate:[NSDate dateWithTimeIntervalSinceNow:.1]];
 [screen unregisterScreenCallbacksWithUUID:token];
 dispatch_sync(queue,^{if(surface){CFRelease(surface);surface=NULL;}});dispatch_sync(writer,^{if(gzclose(output)!=Z_OK)writeFailed=YES;});
 fprintf(stderr,"Captured %d frames\n",count);
 return (invalidFormat||writeFailed||count==0)?1:0;
}}
