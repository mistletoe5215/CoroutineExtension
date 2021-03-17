package com.mistletoe.coroutinewrapper

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.CoroutineContext

/**
 * 执行IO密集型操作的Scheduler
 * Corresponding to Dispatchers.IO
 * ForkJoinPool 是根据Divide-and-Conquer Algorithm 分治算法通过一个双端工作队列，对荷满任务窃取执行而设计的优化线程池
 * 每个Work线程都有个自己的双端工作队列，当通过LIFO的方式从自己的队尾将任务执行完后，
 * 将会通过FIFO的方式去其他的Work线程从它们的工作队列队头偷一个任务执行
 * Created by mistletoe
 * on 2020/12/28
 **/
//thread name:aForkJoinWorkerThread
val FJCoroutineDispatcher = FCoroutineDispatcherImpl()
val parallels = Runtime.getRuntime().availableProcessors() - 1

class FCoroutineDispatcherImpl : ExecutorCoroutineDispatcher() {
    @Volatile
    private var pool: Executor? = null
    override val executor: Executor
        get() = pool ?: getOrCreateFJPoolSync()

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

    @SuppressLint("NewThread", "NewApi")
    private fun createPool(): ExecutorService {
        return ForkJoinPool(parallels)
    }

    @Synchronized
    private fun getOrCreateFJPoolSync(): Executor = pool ?: createPool().also { pool = it }

    private fun cancelJobOnRejection(
        context: CoroutineContext,
        exception: RejectedExecutionException
    ) {
        context.cancel(CancellationException("The task was rejected", exception))
    }
}
