package com.zatek.locationbasedar.utils

import io.reactivex.Observable
import io.reactivex.ObservableSource

data class NTuple2<T1, T2>(val t1: T1, val t2: T2)

data class NTuple3<T1, T2, T3>(val t1: T1, val t2: T2, val t3: T3)

data class NTuple4<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

data class NTuple5<T1, T2, T3, T4, T5>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5)

data class NTuple6<T1, T2, T3, T4, T5, T6>(val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5, val t6: T6)

infix fun <T1, T2> T1.then(t2: T2): NTuple2<T1, T2>
{
    return NTuple2(this, t2)
}

infix fun <T1, T2, T3> NTuple2<T1, T2>.then(t3: T3): NTuple3<T1, T2, T3>
{
    return NTuple3(this.t1, this.t2, t3)
}

infix fun <T1, T2, T3, T4> NTuple3<T1, T2, T3>.then(t4: T4): NTuple4<T1, T2, T3, T4>
{
    return NTuple4(this.t1, this.t2, this.t3, t4)
}

infix fun <T1, T2, T3, T4, T5> NTuple4<T1, T2, T3, T4>.then(t5: T5): NTuple5<T1, T2, T3, T4, T5>
{
    return NTuple5(this.t1, this.t2, this.t3, this.t4, t5)
}

infix fun <T1, T2, T3, T4, T5, T6> NTuple5<T1, T2, T3, T4, T5>.then(t6: T6): NTuple6<T1, T2, T3, T4, T5, T6>
{
    return NTuple6(this.t1, this.t2, this.t3, this.t4, this.t5, t6)
}



fun doOn(vararg observables: Observable<*>) =
    Observable.merge(observables.toList())


fun <T, T1 : Any> Observable<T>.with(source1: Observable<T1>): Observable<T1> =
    withLatestFrom(arrayOf(source1)){ a ->
        a[1] as T1
    }
fun <T, T1 : Any, T2 : Any> Observable<T>.with(source1: Observable<T1>, source2: Observable<T2>): Observable<NTuple2<T1, T2>> =
    withLatestFrom(arrayOf(source1, source2)){ a ->
        a[1] as T1 then a[2] as T2
    }

fun <T, T1 : Any, T2 : Any, T3 : Any> Observable<T>.with(source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>): Observable<NTuple3<T1, T2, T3>> =
    withLatestFrom(arrayOf(source1, source2, source3)){ a ->
        a[1] as T1 then a[2] as T2 then a[3] as T3
    }

fun <T, T1 : Any, T2 : Any, T3 : Any, T4 : Any> Observable<T>.with(source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>, source4: Observable<T4>): Observable<NTuple4<T1, T2, T3, T4>> =
    withLatestFrom(arrayOf(source1, source2, source3, source4)){ a ->
        a[1] as T1 then a[2] as T2 then a[3] as T3 then a[4] as T4
    }

fun <T, T1 : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any> Observable<T>.with(source1: Observable<T1>, source2: Observable<T2>, source3: Observable<T3>, source4: Observable<T4>, source5: Observable<T5>): Observable<NTuple5<T1, T2, T3, T4, T5>> =
    withLatestFrom(arrayOf(source1, source2, source3, source4, source5)){ a ->
        a[1] as T1 then a[2] as T2 then a[3] as T3 then a[4] as T4 then a[5] as T5
    }
