package com.mistletoe.coroutinewrapper

/**
 * Created by mistletoe
 * on 2021/3/5
 **/
import android.annotation.SuppressLint
import android.util.Log
import com.mistletoe.coroutinewrapper.Config.KEEP_ALIVE
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * 网络请求使用，用完超时销毁
 * create recycle thread pool dispatcher
 * Created by mistletoe
 * on 2020/12/28
 **/
fun newMThreadPoolContext(nThreads: Int, name: String): ExecutorCoroutineDispatcher {
    require(nThreads >= 1) { "Expected at least one thread, but $nThreads specified" }
    return MCoroutineDispatcher(nThreads, name)
}

internal class MCoroutineDispatcher(private val nThreads: Int, private val name: String) :
    ExecutorCoroutineDispatcher() {
    @Volatile
    private var pool: Executor? = null
    override val executor: Executor
        get() = pool ?: getOrCreatePoolSync()

    override fun close() {
        executor.asCoroutineDispatcher().cancel()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            executor.asCoroutineDispatcher().dispatch(context, block)
        } catch (e: RejectedExecutionException) {
            cancelJobOnRejection(context, e)
            Log.e(TAG, "error execute coroutine task")
        }
    }

    @SuppressLint("NewThread")
    private fun createPool(): ExecutorService {
        val threadPoolExecutor = ThreadPoolExecutor(nThreads,
            Int.MAX_VALUE,
            KEEP_ALIVE,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            object : ThreadFactory {
                private val mCount = AtomicInteger(1)
                override fun newThread(r: java.lang.Runnable): Thread {
                    return Thread(r, name + "#" + mCount.getAndIncrement())
                }
            })
        //允许超时回收核心线程
        threadPoolExecutor.allowCoreThreadTimeOut(true)
        return threadPoolExecutor
    }

    @Synchronized
    private fun getOrCreatePoolSync(): Executor = pool ?: createPool().also { pool = it }

    private fun cancelJobOnRejection(
        context: CoroutineContext,
        exception: RejectedExecutionException
    ) {
        context.cancel(CancellationException("The task was rejected", exception))
    }
}


