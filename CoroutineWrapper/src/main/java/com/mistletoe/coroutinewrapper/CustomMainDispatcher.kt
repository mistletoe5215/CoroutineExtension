package com.mistletoe.coroutinewrapper

import android.annotation.SuppressLint
import androidx.arch.core.executor.ArchTaskExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.CoroutineContext

/**
 * 作为1.3.2 获取主线程调度器Dispatchers.Main crash的备用调度器
 * Created by mistletoe
 * on 2021/3/2
 **/
@Deprecated("use it in coroutine version below 1.3.2")
val CustomMainDispatcher = CustomMainDispatcherImpl()

class CustomMainDispatcherImpl : ExecutorCoroutineDispatcher() {
    @Volatile
    private var mInternalExecutor: Executor? = null
    override val executor: Executor
        get() = mInternalExecutor ?: getOrCreateMainExecutorSync()

    override fun close() {
        executor.asCoroutineDispatcher().cancel()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        try {
            executor.asCoroutineDispatcher().dispatch(context,block)
        } catch (e: RejectedExecutionException) {
            cancelJobOnRejection(context, e)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun createMainExecutor(): Executor {
        return ArchTaskExecutor.getMainThreadExecutor()
    }

    @Synchronized
    private fun getOrCreateMainExecutorSync(): Executor = mInternalExecutor
        ?: createMainExecutor().also { mInternalExecutor = it }

    private fun cancelJobOnRejection(context: CoroutineContext, exception: RejectedExecutionException) {
        context.cancel(CancellationException("The task was rejected", exception))
    }
}