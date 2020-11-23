package com.mistletoe.coroutinewrapper

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext

/**
 * 给flow wrapper一个lifecycle
 * create  lifecycle  safe flow task
 * Created by mistletoe
 * on 2020/11/17
 **/
class FlowObserver<T>(lifecycleOwner: LifecycleOwner, private val flow: Flow<T>) : DefaultLifecycleObserver, FlowDelegate<T> {
    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private var job: Job? = null

    @ObsoleteCoroutinesApi
    private var mFlowScheduler: CoroutineDispatcher = newFixedThreadPoolContext(Runtime.getRuntime().availableProcessors() * 2, "flow")
    private var mConsumeScheduler: CoroutineDispatcher = Dispatchers.Main
    private var taskResult: MutableList<T> = mutableListOf()
    private var mCatchBlock: ((Throwable) -> Unit)? = null
    private var mCollectBlock: (suspend (T) -> Unit)? = null
    private var mCompletionBlock: (suspend (List<T>) -> Unit)? = null

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    override fun start() {
        job = CoroutineScope(mConsumeScheduler).launch {
            flow.flowOn(mFlowScheduler)
                .catch { throwable ->
                    mCatchBlock?.invoke(throwable)
                }.onCompletion {
                    mCompletionBlock?.invoke(taskResult)
                }.collect {
                    taskResult.add(it)
                    mCollectBlock?.invoke(it)
                }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        taskResult.clear()
    }

    override fun catch(block: (Throwable) -> Unit): FlowDelegate<T> {
        mCatchBlock = block
        return this
    }

    override fun collect(block: suspend (T) -> Unit): FlowDelegate<T> {
        mCollectBlock = block
        return this
    }

    override fun onCompletion(block: suspend (List<T>) -> Unit): FlowDelegate<T> {
        mCompletionBlock = block
        return this
    }

    @ObsoleteCoroutinesApi
    override fun flowOn(dispatcher: CoroutineDispatcher): FlowDelegate<T> {
        mFlowScheduler = dispatcher
        return this
    }

    override fun consumeOn(dispatcher: CoroutineDispatcher): FlowDelegate<T> {
        mConsumeScheduler = dispatcher
        return this
    }
}

interface FlowDelegate<T> {
    fun start()
    fun consumeOn(dispatcher: CoroutineDispatcher): FlowDelegate<T>
    fun flowOn(dispatcher: CoroutineDispatcher): FlowDelegate<T>
    fun catch(block: (Throwable) -> Unit): FlowDelegate<T>
    fun collect(block: suspend (T) -> Unit): FlowDelegate<T>
    fun onCompletion(block: suspend (List<T>) -> Unit): FlowDelegate<T>
}

/**
 * 一个合并请求的例子
 * zip(mService.getPopularVideoListAsync(0, param), mService.getCarVideoViewAsync(0, 0))
 *     .bindLifecycle(activity).onCompletion {
 *            val resultList = it.awaitAll()
 *            //ensure get all result
 *            if(resultList.size == 2){
 *           Log.d("Mistletoe",resultList[0].convertTo<ListVideoResponseModel>()?.data.toString() +"\n"+
 *            resultList[0].convertTo<ViewResponseModel>()?.data.toString())
 *            }}
 *       .catch {
 *        Log.e("Mistletoe",it.message)
 *        }
 *
 */

//zip retrofit tasks
inline fun <reified T> zip(vararg tasks: Deferred<T>): Flow<Deferred<T>> = tasks.asFlow()

//bind flow lifecycle
inline fun <reified T> Flow<T>.bindLifecycle(lifecycleOwner: LifecycleOwner) = FlowObserver(lifecycleOwner, this)

// get origin result list in onCompletion
suspend fun <T> List<Deferred<T>>.awaitAll(): List<T> = map { it.await() }