
### 使用JitPack快速依赖

  - [![](https://jitpack.io/v/mistletoe5215/CoroutineExtension.svg)](https://jitpack.io/#mistletoe5215/CoroutineExtension)
  
### How To 
  ```

 allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
 
dependencies {
	        implementation 'com.github.mistletoe5215:CoroutineExtension:v1.0.0'
	}
 ```  
 ### UseAge
  
  #### [使用promise then 实现流式请求](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/CoroutineExtension.kt)
  - 项目使用retrofit v2.6 时
   
  ```kotlin
    data class ResponseDataModel(
          var code:Int = 0,
          var message:String?=null,
          var data:String?=null 
    )
 ```
    
   ```kotlin
  @BaseUrl("www.github.com")
    interface ApiService {
     @GET("getListData")
     suspend fun foo(@Query param:String):ResponseDataModel
     }
  ```

```kotlin
     lifecycleOwner.promise{
            //请求接口
            mApiService.foo(param)
       }.then({
           mResponseDataModel ->
            //返回结果，更新UI
      }){ e ->
           //捕获异常并处理 
         }
```
  - 项目使用retrofit v2.3 时

```kotlin
  @BaseUrl("www.github.com")
    interface ApiService {
     @GET("getListData")
     fun foo(@Query param:String):Deferred<ResponseDataModel>
     }
  ```  
  
```kotlin
     lifecycleOwner.promise{
            //请求接口
            mApiService.foo(param)
       }.then({
           mResponseDataModel ->
            //返回结果，更新UI
      }){ e ->
           //捕获异常并处理 
         }
```  
 #### [使用setTimeOut 执行延时任务](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/CoroutineExtension.kt)
 
 ```kotlin

    val handle = setTimeOut(time = 2000L){
          //do delay task
    }
    // cancel task
    handle.cancel()
```
 #### [使用setInterval 执行定时循环周期任务](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/CoroutineExtension.kt)
 
 ```kotlin
    val handle = setInterval(Dispatchers.IO, 3000L){
       //do loop task
    }
    // cancel task
    handle.cancel()
```
 
 #### [使用FlowCountDownTimer实现一个可随时暂停恢复的倒计时](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/FlowCountDownTimer.kt)
 
 ```kotlin
  //实现一个3s的倒计时
 
  val  mFlowCountDownTimer = FlowCountDownTimer.Builder().from(3)
                  .to(0)
                  .interval(1000L)
                  .withUnit(TimeUnit.SECONDS)
                  .create()
              mFlowCountDownTimer?.apply {
                          watchStart {
                              //观察开始动作
                          }
                          watchCountDown {
                              currentCount ->
                             //观察倒计时
                          }
                          watchComplete {
                             //观察倒计时结束
                          }
                      }?.start()
   //希望暂停时， 
   mFlowCountDownTimer?.pause()
   //希望恢复倒计时时，
   mFlowCountDownTimer?.resume()

``` 

### 更新

   #### v1.0.1 
   
 - 现在支持中缀表达式进行[promise then catch 的流式调用](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/CoroutineExtension.kt)
 
```kotlin
        lifecycleOwner promise{
                //请求接口
                mApiService.foo(param)
           } then {
               mResponseDataModel ->
                //返回结果，更新UI
          } catch { e ->
               //捕获异常并在主线程处理异常
          }
```
  #### v1.1.0

  -  支持使用zip表达式进行[多个retrofit请求合并](https://github.com/mistletoe5215/CoroutineExtension/blob/master/CoroutineWrapper/src/main/java/com/mistletoe/coroutinewrapper/FlowExtension.kt)

```kotlin
 data class ResponseDataModel1(
          var code:Int = 0,
          var message:String?=null,
          var data:String?=null
    )

 data class ResponseDataModel2(
          var code:Int = 0,
          var message:String?=null,
          var data:String?=null
    )
 @BaseUrl("www.github.com")
 interface ApiService {
         @GET("getListData")
         fun foo1(@Query param:String):Deferred<ResponseDataModel1>
         @GET("getImageData")
         fun foo2(@Query param:String):Deferred<ResponseDataModel2>
  }
 zip(mService.foo1(param), mService.foo2(param))
                .bindLifecycle(activity)
                .onCompletion {
                    val resultList = it.awaitAll()
                    //ensure get all result
                    if(resultList.size == 2){
                        Log.d("Mistletoe",(resultList[0] as ResponseDataModel1)?.data.toString() +"\n"+
                            (resultList[1] as ResponseDataModel2)?.data.toString()
                        )
                    }
                }.catch {
                    Log.e("Mistletoe",it.message)
                }


```
