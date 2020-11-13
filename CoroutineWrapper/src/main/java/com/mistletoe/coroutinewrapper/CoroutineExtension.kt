package com.mistletoe.coroutinewrapper

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.Keep
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * Created by mistletoe
 * on 2020/8/27
 **/
const val TAG = "CoroutineExtension"
@Keep
internal class JobLifecycleListener(private val job: Job) : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun cancelCoroutine() {
        if (!job.isCancelled) {
            job.cancel()
        }
    }
}
@ObsoleteCoroutinesApi
val background = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors() * 2, "promise")
//协程实现js风格的promise

// 如果是 retrofit 网络请求 v2.6 调用该方法
/**
 *  for example:
 *  in retrofit
 *  @BaseUrl("www.github.com")
 *  v2.6
 *  interface ApiService {
 *      @GET("getListData")
 *      suspend fun foo(@Query param:String):ResponseDataModel
 *  }
 *  lifecycleOwner.promise{
 *       mApiService.foo(param)
 *  }.then{
 *      mResponseDataModel ->
 *      //do ui update
 *  }catch {
 *  // ui thread  handle  exception
 *  }
 */
@ObsoleteCoroutinesApi
@SuppressLint("RestrictedApi")
fun <T> LifecycleOwner.promise(p: suspend  () -> T): Pair<LifecycleOwner, Deferred<T>> {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in promise consumed!:${throwable.message}")
    }
    val deferred = CoroutineScope(Dispatchers.IO+exceptionHandler).async(context = background, start = CoroutineStart.LAZY){
        p.invoke()
    }
    // use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
    ArchTaskExecutor.getMainThreadExecutor().execute {
        lifecycle.addObserver(JobLifecycleListener(deferred))
    }
    return Pair(this, deferred)
}

/**
 *  v2.3
 *  interface ApiService {
 *      @GET("getListData")
 *      fun foo(@Query param:String):Deferred<ResponseDataModel>
 *  }
 *  lifecycleOwner promise{
 *       mApiService.foo(param)
 *  } then{
 *      mResponseDataModel ->
 *      //do ui update
 *  } catch {
 *     // ui thread  handle  exception
 *  }
 */

@ObsoleteCoroutinesApi
@Deprecated("use it in retrofit 2.3 with coroutine factory interceptor")
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> LifecycleOwner.promise(p: () -> Deferred<T>): Pair<LifecycleOwner, Deferred<T>> {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in promise consumed!:${throwable.message}")
    }
    val deferred = CoroutineScope(Dispatchers.IO+exceptionHandler).async(context = background, start = CoroutineStart.LAZY){
        p.invoke().await()
    }
    // use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
    ArchTaskExecutor.getMainThreadExecutor().execute {
        lifecycle.addObserver(JobLifecycleListener(deferred))
    }
    return Pair(this, deferred)
}

//协程实现js风格的then
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> Pair<LifecycleOwner, Deferred<T>>.then(block: (T) -> Unit) {
    //这里只收集不处理
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in then consumed!:${throwable.message}")
    }
    val job = CoroutineScope(Dispatchers.Main+ exceptionHandler).launch {
        block.invoke(this@then.second.await())
    }
    //use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
    ArchTaskExecutor.getMainThreadExecutor().execute {
        this@then.first.lifecycle.addObserver(JobLifecycleListener(job))
    }
}
//协程实现js风格的catch
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> Deferred<T>.catch(block: (throwable: Throwable) -> Unit){
    invokeOnCompletion {
        it?.let {
            //处理异常
            ArchTaskExecutor.getMainThreadExecutor().execute{
                block.invoke(it)
            }
        }
    }
}
//协程实现js风格的setTimeOut
@Keep
fun setTimeOut(dispatcher: CoroutineDispatcher = Dispatchers.Main, time: Long, block: () -> Unit): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in setTimeOut consumed!:${throwable.message}")
    }
    return CoroutineScope(dispatcher+exceptionHandler).launch {
        delay(time)
        block.invoke()
    }
}

//协程实现js风格的setInterval
@Keep
fun setInterval(dispatcher: CoroutineDispatcher = Dispatchers.IO, time: Long, block: () -> Unit): Job {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in setInterval consumed!:${throwable.message}")
    }
    return CoroutineScope(dispatcher + exceptionHandler).launch {
        while (true) {
            delay(time)
            block.invoke()
        }
    }
}

//do task in background,don't care result
@Keep
internal var bgJob: Job? = null

@Keep
fun <T> doBackgroundTask(task: () -> Deferred<T>) {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in doBackgroundTask consumed!:${throwable.message}")
    }
    bgJob = CoroutineScope(Dispatchers.IO+exceptionHandler).launch {
        val result = task.invoke().await()
        Log.d(TAG, "BackgroundTask result:$result")
        bgJob?.cancel()
        bgJob = null
    }
}