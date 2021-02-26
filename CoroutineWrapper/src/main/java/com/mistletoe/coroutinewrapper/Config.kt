package com.mistletoe.coroutinewrapper

import kotlinx.coroutines.ObsoleteCoroutinesApi

/**
 * Created by mistletoe
 * on 2020/12/28
 **/
object Config {
    private var enable_debug = false

    //switch for coroutine debug
    fun enableGlobalCoroutineDebug() {
        enable_debug = true
    }

    fun checkDebug(): Boolean = enable_debug
    private const val CORE_POOL_SIZE = 8 // 核心线程池数量
    const val KEEP_ALIVE = 60L //线程保活时间 s
    @ObsoleteCoroutinesApi
    val PDispatcher = newMThreadPoolContext(CORE_POOL_SIZE,"promise")
    @ObsoleteCoroutinesApi
    val FDispatcher = newMThreadPoolContext(CORE_POOL_SIZE,"flow")
}

