package com.mistletoe.coroutinewrapper

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

/**
 * Created by mistletoe
 * on 2020/11/16
 **/
fun example(lifecycleOwner: LifecycleOwner){

    lifecycleOwner.doFlowTasks(mutableListOf(
        ::doJob1,
        ::doJob2,
        ::doJob3
    )) collect {

    } onError {

    }

}
suspend fun doJob1(){
    delay(300)
    print("doJob1")
}
suspend fun doJob2(){
    delay(1000)
    print("doJob2")
}
suspend fun doJob3(){
    delay(700)
    print("doJob3")
}