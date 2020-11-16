package com.mistletoe.coroutinewrapper

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.Keep
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

/**
 * Created by mistletoe
 * on 2020/11/16
 **/
@Deprecated("use it in retrofit 2.3 with coroutine factory interceptor")
@SuppressLint("RestrictedApi")
@ObsoleteCoroutinesApi
infix fun LifecycleOwner.doFlowTasks0(blocks:List<() -> Deferred<Any>>): Pair<LifecycleOwner, List<Deferred<Any>>>{
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in doFlowTasks consumed!:${throwable.message}")
    }
    val deferredList = mutableListOf<Deferred<Any>>()
    blocks.forEach {
        val deferred = CoroutineScope(Dispatchers.IO+exceptionHandler).async(context = background, start = CoroutineStart.LAZY){
            it.invoke().await()
        }
        // use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
        ArchTaskExecutor.getMainThreadExecutor().execute {
            lifecycle.addObserver(JobLifecycleListener(deferred))
        }
        deferredList.add(deferred)
    }
    return Pair(this, deferredList)
}

@SuppressLint("RestrictedApi")
@ObsoleteCoroutinesApi
infix fun LifecycleOwner.doFlowTasks(blocks:List<suspend () -> Any>): Pair<LifecycleOwner, List<Deferred<Any>>>{
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in doFlowTasks consumed!:${throwable.message}")
    }
    val deferredList = mutableListOf<Deferred<Any>>()
    blocks.forEach {
        val deferred = CoroutineScope(Dispatchers.IO+exceptionHandler).async(context = background, start = CoroutineStart.LAZY){
            it.invoke()
        }
        // use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
        ArchTaskExecutor.getMainThreadExecutor().execute {
            lifecycle.addObserver(JobLifecycleListener(deferred))
        }
        deferredList.add(deferred)
    }
    return Pair(this, deferredList)
}


@SuppressLint("RestrictedApi")
infix  fun  Pair<LifecycleOwner, List<Deferred<Any>>>.collect(block: (List<Any>) -> Unit):List<Deferred<Any>>{
    //这里只收集不处理
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG,"Exception in then consumed!:${throwable.message}")
    }
    val resultList = mutableListOf<Any>()
    this@collect.second.forEach {
        val job = CoroutineScope(Dispatchers.Main+ exceptionHandler).launch {
            resultList.add(it.await())
        }
        //use ArchTaskExecutor.getMainThreadExecutor() the same with paging LivePagedListBuilder create() method
        ArchTaskExecutor.getMainThreadExecutor().execute {
            this@collect.first.lifecycle.addObserver(JobLifecycleListener(job))
        }
    }
    block.invoke(resultList)
    return this@collect.second
}
@SuppressLint("RestrictedApi")
@Keep
infix fun <T> List<Deferred<T>>.onError(block: (hintThrowableList: List<HintThrowable>) -> Unit){
    val errorList = mutableListOf<HintThrowable>()
    var pointer = -1
    forEach {
            deferred->
        pointer++
        deferred.invokeOnCompletion {
            errorList.add(HintThrowable(pointer,it))
        }
    }
    ArchTaskExecutor.getMainThreadExecutor().execute{
        block.invoke(errorList)
    }
}
data class HintThrowable(
    var index:Int = -1,
    var value:Throwable? = null
)