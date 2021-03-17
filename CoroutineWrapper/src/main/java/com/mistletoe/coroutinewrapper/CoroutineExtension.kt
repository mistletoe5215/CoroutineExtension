package com.mistletoe.coroutinewrapper

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.mistletoe.coroutinewrapper.Config.PDispatcher
import kotlinx.coroutines.*
import java.lang.IllegalStateException
import kotlin.jvm.Throws

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
fun <T> LifecycleOwner.promise(p: suspend () -> T): Pair<LifecycleOwner, Deferred<T>> {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception in promise consumed!:${throwable.message}")
    }
    val deferred = CoroutineScope(PDispatcher + exceptionHandler).async(
        context = PDispatcher,
        start = CoroutineStart.LAZY
    ) {
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
        Log.e(TAG, "Exception in promise consumed!:${throwable.message}")
    }
    val lifecycleScope = CoroutineScope(PDispatcher + exceptionHandler)
    val deferred = lifecycleScope.async(start = CoroutineStart.LAZY) {
        p.invoke().await()
    }
    val observer = lifecycleScope.coroutineContext[Job]?.let { JobLifecycleListener(it) }
    observer?.let {
        // add lifecycle observer
        ArchTaskExecutor.getMainThreadExecutor().execute {
            lifecycle.addObserver(it)
        }
        // remove lifecycle observer  to avoid useless memory increase
        deferred.invokeOnCompletion { _ ->
            ArchTaskExecutor.getMainThreadExecutor().execute {
                lifecycle.removeObserver(it)
            }
        }
    }

    return this to deferred
}

//协程实现js风格的then
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> Pair<LifecycleOwner, Deferred<T>>.then(block: (T) -> Unit): Deferred<T> {
    //这里只收集不处理
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception in then consumed!:${throwable.message}")
    }
    CoroutineScope(Dispatchers.Main + exceptionHandler).launch {
        block.invoke(this@then.second.await())
    }
    return this.second
}

//协程实现js风格的catch
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> Deferred<T>.catch(block: (throwable: Throwable) -> Unit): Deferred<T> {
    invokeOnCompletion {
        it?.let {
            //处理异常
            ArchTaskExecutor.getMainThreadExecutor().execute {
                block.invoke(it)
            }
        }
    }
    return this
}

var globalTimeOutScope: CoroutineScope? = null

//协程实现js风格的setTimeOut
@Keep
fun setTimeOut(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    time: Long,
    block: () -> Unit
): CoroutineScope? {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception in setTimeOut consumed!:${throwable.message}")
    }
    if (globalTimeOutScope == null) {
        globalTimeOutScope = CoroutineScope(dispatcher + exceptionHandler)
    }
    globalTimeOutScope?.launch {
        delay(time)
        block.invoke()
    }
    return globalTimeOutScope
}

var globalIntervalScope: CoroutineScope? = null

//协程实现js风格的setInterval
@Keep
fun setInterval(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    time: Long,
    block: () -> Unit
): CoroutineScope? {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception in setInterval consumed!:${throwable.message}")
    }
    if (globalIntervalScope == null) {
        globalIntervalScope = CoroutineScope(dispatcher + exceptionHandler)
    }
    globalIntervalScope?.launch {
        while (true) {
            delay(time)
            block.invoke()
        }
    }
    return globalIntervalScope
}

internal class CoroutineAttachStateChangeListener(private val job: Job?) :
    View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View?) {
        //do nothing
    }

    override fun onViewDetachedFromWindow(v: View?) {
        job?.cancel()
        v?.removeOnAttachStateChangeListener(this)
    }
}

//在View层级处获得一个协程域，当它从窗口移除时该协程取消
@MainThread
fun View.acquireObservableScope(): CoroutineScope {
    val viewObservableScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    val mJob = viewObservableScope.coroutineContext[Job]
    val l = CoroutineAttachStateChangeListener(mJob)
    addOnAttachStateChangeListener(l)
    return viewObservableScope
}

@SuppressLint("RestrictedApi")
@Throws(IllegalStateException::class)
@MainThread
fun Context.acquireLifecycleScope(): CoroutineScope {
    lifecycle()?.let {
        val lifecycleScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        val mJob = lifecycleScope.coroutineContext[Job]!! // no way to be null
        val l = JobLifecycleListener(mJob)
        mJob.invokeOnCompletion { _ ->
            ArchTaskExecutor.getMainThreadExecutor().execute {
                it.removeObserver(l)
            }
        }
        it.addObserver(l)
        return lifecycleScope
    } ?: throw IllegalStateException("error,can not get lifecycle here")
}


internal fun Context.lifecycle(): Lifecycle? {
    var c = this as? ContextWrapper
    while (c != null) {
        if (c is LifecycleOwner) {
            return c.lifecycle
        }
        c = c.baseContext as? ContextWrapper
    }
    return null
}

@Keep
fun <T> doCancelableJob(task: () -> Deferred<T>) {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Exception in doBackgroundTask consumed!:${throwable.message}")
    }
    val scope = CoroutineScope(Dispatchers.IO + exceptionHandler)
    scope.launch {
        val result = task.invoke().await()
        Log.d(TAG, "task result:$result")
    }
    scope.coroutineContext[Job]?.invokeOnCompletion {
        scope.cancel()
        Log.d(TAG, "task dispose self")
    }
}

