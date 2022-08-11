/*
 * YukiHookAPI - An efficient Kotlin version of the Xposed Hook API.
 * Copyright (C) 2019-2022 HighCapable
 * https://github.com/fankes/YukiHookAPI
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * This file is Created by fankes on 2022/2/4.
 */
@file:Suppress("unused", "MemberVisibilityCanBePrivate", "UNCHECKED_CAST", "KotlinConstantConditions")

package com.highcapable.yukihookapi.hook.core.finder

import com.highcapable.yukihookapi.annotation.YukiPrivateApi
import com.highcapable.yukihookapi.hook.bean.VariousClass
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreater
import com.highcapable.yukihookapi.hook.core.finder.base.BaseFinder
import com.highcapable.yukihookapi.hook.core.finder.type.ModifierRules
import com.highcapable.yukihookapi.hook.core.reflex.tools.ReflectionTool
import com.highcapable.yukihookapi.hook.factory.ConstructorCondition
import com.highcapable.yukihookapi.hook.factory.checkingInternal
import com.highcapable.yukihookapi.hook.factory.hasExtends
import com.highcapable.yukihookapi.hook.log.yLoggerW
import com.highcapable.yukihookapi.hook.type.defined.UndefinedType
import com.highcapable.yukihookapi.hook.utils.runBlocking
import java.lang.reflect.Constructor

/**
 * [Constructor] 查找类
 *
 * 可通过指定类型查找指定构造方法或一组构造方法
 * @param hookInstance 当前 Hook 实例 - 填写后将自动设置 [YukiMemberHookCreater.MemberHookCreater.members]
 * @param classSet 当前需要查找的 [Class] 实例
 */
class ConstructorFinder @PublishedApi internal constructor(
    @property:YukiPrivateApi
    override val hookInstance: YukiMemberHookCreater.MemberHookCreater? = null,
    @property:YukiPrivateApi
    override val classSet: Class<*>? = null
) : BaseFinder(tag = "Constructor", hookInstance, classSet) {

    /** 当前使用的 [classSet] */
    private var usedClassSet = classSet

    /** 是否在未找到后继续在当前 [classSet] 的父类中查找 */
    private var isFindInSuperClass = false

    /** 当前重查找结果回调 */
    private var remedyPlansCallback: (() -> Unit)? = null

    /** [Constructor] 参数数组 */
    private var paramTypes: Array<out Class<*>>? = null

    /** [Constructor] 参数个数范围 */
    private var paramCountRange = IntRange.EMPTY

    /** [ModifierRules] 实例 */
    @PublishedApi
    internal var modifiers: ModifierRules? = null

    /**
     * 设置 [Constructor] 参数个数
     *
     * 你可以不使用 [param] 指定参数类型而是仅使用此变量指定参数个数
     *
     * 若参数个数小于零则忽略并使用 [param]
     */
    var paramCount = -1

    /**
     * 设置 [Constructor] 标识符筛选条件
     *
     * - ❗存在多个 [BaseFinder.IndexTypeCondition] 时除了 [order] 只会生效最后一个
     * @param initiate 方法体
     * @return [BaseFinder.IndexTypeCondition]
     */
    inline fun modifiers(initiate: ModifierRules.() -> Unit): IndexTypeCondition {
        modifiers = ModifierRules().apply(initiate)
        return IndexTypeCondition(IndexConfigType.MATCH)
    }

    /**
     * 设置 [Constructor] 空参数、无参数
     *
     * @return [BaseFinder.IndexTypeCondition]
     */
    fun emptyParam() = paramCount(num = 0)

    /**
     * 设置 [Constructor] 参数
     *
     * 如果同时使用了 [paramCount] 则 [paramTypes] 的数量必须与 [paramCount] 完全匹配
     *
     * - ❗无参 [Constructor] 请使用 [emptyParam] 设置查询条件
     *
     * - ❗有参 [Constructor] 必须使用此方法设定参数或使用 [paramCount] 指定个数
     *
     * - ❗存在多个 [BaseFinder.IndexTypeCondition] 时除了 [order] 只会生效最后一个
     * @param paramType 参数类型数组 - ❗只能是 [Class]、[String]、[VariousClass]
     * @return [BaseFinder.IndexTypeCondition]
     */
    fun param(vararg paramType: Any): IndexTypeCondition {
        if (paramType.isEmpty()) error("paramTypes is empty, please use emptyParam() instead")
        paramTypes = ArrayList<Class<*>>().apply { paramType.forEach { add(it.compat() ?: UndefinedType) } }.toTypedArray()
        return IndexTypeCondition(IndexConfigType.MATCH)
    }

    /**
     * 顺序筛选字节码的下标
     * @return [BaseFinder.IndexTypeCondition]
     */
    fun order() = IndexTypeCondition(IndexConfigType.ORDER)

    /**
     * 设置 [Constructor] 参数个数
     *
     * 你可以不使用 [param] 指定参数类型而是仅使用此方法指定参数个数
     *
     * 若参数个数小于零则忽略并使用 [param]
     *
     * - ❗存在多个 [BaseFinder.IndexTypeCondition] 时除了 [order] 只会生效最后一个
     * @param num 个数
     * @return [BaseFinder.IndexTypeCondition]
     */
    fun paramCount(num: Int): IndexTypeCondition {
        paramCount = num
        return IndexTypeCondition(IndexConfigType.MATCH)
    }

    /**
     * 设置 [Constructor] 参数个数范围
     *
     * 你可以不使用 [param] 指定参数类型而是仅使用此方法指定参数个数范围
     *
     * - ❗存在多个 [BaseFinder.IndexTypeCondition] 时除了 [order] 只会生效最后一个
     * @param numRange 个数范围
     * @return [BaseFinder.IndexTypeCondition]
     */
    fun paramCount(numRange: IntRange): IndexTypeCondition {
        paramCountRange = numRange
        return IndexTypeCondition(IndexConfigType.MATCH)
    }

    /**
     * 设置在 [classSet] 的所有父类中查找当前 [Constructor]
     *
     * - ❗若当前 [classSet] 的父类较多可能会耗时 - API 会自动循环到父类继承是 [Any] 前的最后一个类
     * @param isOnlySuperClass 是否仅在当前 [classSet] 的父类中查找 - 若父类是 [Any] 则不会生效
     */
    fun superClass(isOnlySuperClass: Boolean = false) {
        isFindInSuperClass = true
        if (isOnlySuperClass && classSet?.hasExtends == true) usedClassSet = classSet.superclass
    }

    /**
     * 得到构造方法或一组构造方法
     * @return [HashSet]<[Constructor]>
     * @throws NoSuchMethodError 如果找不到构造方法
     */
    private val result
        get() = ReflectionTool.findConstructors(
            usedClassSet, orderIndex, matchIndex, modifiers,
            paramCount, paramCountRange, paramTypes, isFindInSuperClass
        )

    /**
     * 设置实例
     * @param isBind 是否将结果设置到目标 [YukiMemberHookCreater.MemberHookCreater]
     * @param constructors 当前找到的 [Constructor] 数组
     */
    private fun setInstance(isBind: Boolean, constructors: HashSet<Constructor<*>>) {
        memberInstances.clear()
        val result = constructors.takeIf { it.isNotEmpty() }?.onEach { memberInstances.add(it) }?.first()
        if (isBind) hookInstance?.members?.apply {
            clear()
            result?.also { add(it) }
        }
    }

    /**
     * 得到 [Constructor] 结果
     *
     * - ❗此功能交由方法体自动完成 - 你不应该手动调用此方法
     * @param isBind 是否将结果设置到目标 [YukiMemberHookCreater.MemberHookCreater]
     * @return [Result]
     */
    @YukiPrivateApi
    override fun build(isBind: Boolean) = try {
        if (classSet != null) {
            classSet.checkingInternal()
            runBlocking {
                isBindToHooker = isBind
                setInstance(isBind, result)
            }.result { ms ->
                memberInstances.takeIf { it.isNotEmpty() }?.forEach { onHookLogMsg(msg = "Find Constructor [$it] takes ${ms}ms [${hookTag}]") }
            }
            Result()
        } else Result(isNoSuch = true, Throwable("classSet is null"))
    } catch (e: Throwable) {
        onFailureMsg(throwable = e)
        Result(isNoSuch = true, e)
    }

    /**
     * 创建一个异常结果
     *
     * - ❗此功能交由方法体自动完成 - 你不应该手动调用此方法
     * @param throwable 异常
     * @return [Result]
     */
    @YukiPrivateApi
    override fun failure(throwable: Throwable?) = Result(isNoSuch = true, throwable)

    /**
     * [Constructor] 重查找实现类
     *
     * 可累计失败次数直到查找成功
     */
    inner class RemedyPlan @PublishedApi internal constructor() {

        /** 失败尝试次数数组 */
        @PublishedApi
        internal val remedyPlans = HashSet<Pair<ConstructorFinder, Result>>()

        /**
         * 创建需要重新查找的 [Constructor]
         *
         * 你可以添加多个备选 [Constructor] - 直到成功为止
         *
         * 若最后依然失败 - 将停止查找并输出错误日志
         * @param initiate 方法体
         */
        inline fun constructor(initiate: ConstructorCondition) =
            Result().apply { remedyPlans.add(Pair(ConstructorFinder(hookInstance, classSet).apply(initiate), this)) }

        /** 开始重查找 */
        @PublishedApi
        internal fun build() {
            if (classSet == null) return
            if (remedyPlans.isNotEmpty()) run {
                var isFindSuccess = false
                var lastError: Throwable? = null
                remedyPlans.forEachIndexed { p, it ->
                    runCatching {
                        runBlocking {
                            setInstance(isBindToHooker, it.first.result)
                        }.result { ms ->
                            memberInstances.takeIf { it.isNotEmpty() }
                                ?.forEach { onHookLogMsg(msg = "Find Constructor [$it] takes ${ms}ms [${hookTag}]") }
                        }
                        isFindSuccess = true
                        it.second.onFindCallback?.invoke(memberInstances.constructors())
                        remedyPlansCallback?.invoke()
                        memberInstances.takeIf { it.isNotEmpty() }
                            ?.forEach { onHookLogMsg(msg = "Constructor [$it] trying ${p + 1} times success by RemedyPlan [${hookTag}]") }
                        return@run
                    }.onFailure {
                        lastError = it
                        onFailureMsg(msg = "Trying ${p + 1} times by RemedyPlan --> $it", isAlwaysPrint = true)
                    }
                }
                if (isFindSuccess.not()) {
                    onFailureMsg(
                        msg = "Trying ${remedyPlans.size} times and all failure by RemedyPlan",
                        throwable = lastError,
                        isAlwaysPrint = true
                    )
                    remedyPlans.clear()
                }
            } else yLoggerW(msg = "RemedyPlan is empty, forgot it? [${hookTag}]")
        }

        /**
         * [RemedyPlan] 结果实现类
         *
         * 可在这里处理是否成功的回调
         */
        inner class Result @PublishedApi internal constructor() {

            /** 找到结果时的回调 */
            internal var onFindCallback: (HashSet<Constructor<*>>.() -> Unit)? = null

            /**
             * 当找到结果时
             * @param initiate 回调
             */
            fun onFind(initiate: HashSet<Constructor<*>>.() -> Unit) {
                onFindCallback = initiate
            }
        }
    }

    /**
     * [Constructor] 查找结果实现类
     * @param isNoSuch 是否没有找到构造方法 - 默认否
     * @param throwable 错误信息
     */
    inner class Result internal constructor(
        @PublishedApi internal val isNoSuch: Boolean = false,
        @PublishedApi internal val throwable: Throwable? = null
    ) : BaseResult {

        /**
         * 创建监听结果事件方法体
         * @param initiate 方法体
         * @return [Result] 可继续向下监听
         */
        inline fun result(initiate: Result.() -> Unit) = apply(initiate)

        /**
         * 获得 [Constructor] 实例处理类
         *
         * - 若有多个 [Constructor] 结果只会返回第一个
         *
         * - ❗在 [memberInstances] 结果为空时使用此方法将无法获得对象
         *
         * - ❗若你设置了 [remedys] 请使用 [wait] 回调结果方法
         * @return [Instance]
         */
        fun get() = Instance(give())

        /**
         * 获得 [Constructor] 实例处理类数组
         *
         * - 返回全部查询条件匹配的多个 [Constructor] 实例结果并在 [isBindToHooker] 时设置到 [hookInstance]
         *
         * - ❗在 [memberInstances] 结果为空时使用此方法将无法获得对象
         *
         * - ❗若你设置了 [remedys] 请使用 [waitAll] 回调结果方法
         * @return [ArrayList]<[Instance]>
         */
        fun all(): ArrayList<Instance> {
            if (isBindToHooker) memberInstances.takeIf { it.isNotEmpty() }?.apply {
                hookInstance?.members?.clear()
                forEach { hookInstance?.members?.add(it) }
            }
            return arrayListOf<Instance>().apply { giveAll().takeIf { it.isNotEmpty() }?.forEach { add(Instance(it)) } }
        }

        /**
         * 得到 [Constructor] 本身
         *
         * - 若有多个 [Constructor] 结果只会返回第一个
         *
         * - 在查询条件找不到任何结果的时候将返回 null
         * @return [Constructor] or null
         */
        fun give() = giveAll().takeIf { it.isNotEmpty() }?.first()

        /**
         * 得到 [Constructor] 本身数组
         *
         * - 返回全部查询条件匹配的多个 [Constructor] 实例
         *
         * - 在查询条件找不到任何结果的时候将返回空的 [HashSet]
         * @return [HashSet]<[Constructor]>
         */
        fun giveAll() = memberInstances.takeIf { it.isNotEmpty() }?.constructors() ?: HashSet()

        /**
         * 获得 [Constructor] 实例处理类
         *
         * - 若有多个 [Constructor] 结果只会返回第一个
         *
         * - ❗若你设置了 [remedys] 必须使用此方法才能获得结果
         *
         * - ❗若你没有设置 [remedys] 此方法将不会被回调
         * @param initiate 回调 [Instance]
         */
        fun wait(initiate: Instance.() -> Unit) {
            if (memberInstances.isNotEmpty()) initiate(get())
            else remedyPlansCallback = { initiate(get()) }
        }

        /**
         * 获得 [Constructor] 实例处理类数组
         *
         * - 返回全部查询条件匹配的多个 [Constructor] 实例结果
         *
         * - ❗若你设置了 [remedys] 必须使用此方法才能获得结果
         *
         * - ❗若你没有设置 [remedys] 此方法将不会被回调
         * @param initiate 回调 [ArrayList]<[Instance]>
         */
        fun waitAll(initiate: ArrayList<Instance>.() -> Unit) {
            if (memberInstances.isNotEmpty()) initiate(all())
            else remedyPlansCallback = { initiate(all()) }
        }

        /**
         * 创建 [Constructor] 重查找功能
         *
         * 当你遇到一种 [Constructor] 可能存在不同形式的存在时
         *
         * 可以使用 [RemedyPlan] 重新查找它 - 而没有必要使用 [onNoSuchConstructor] 捕获异常二次查找 [Constructor]
         * @param initiate 方法体
         * @return [Result] 可继续向下监听
         */
        inline fun remedys(initiate: RemedyPlan.() -> Unit): Result {
            isUsingRemedyPlan = true
            if (isNoSuch) RemedyPlan().apply(initiate).build()
            return this
        }

        /**
         * 监听找不到 [Constructor] 时
         *
         * - 只会返回第一次的错误信息 - 不会返回 [RemedyPlan] 的错误信息
         * @param result 回调错误
         * @return [Result] 可继续向下监听
         */
        inline fun onNoSuchConstructor(result: (Throwable) -> Unit): Result {
            if (isNoSuch) result(throwable ?: Throwable("Initialization Error"))
            return this
        }

        /**
         * 忽略异常并停止打印任何错误日志
         *
         * - 若 [isNotIgnoredNoSuchMemberFailure] 为 false 则自动忽略
         *
         * - ❗此时若要监听异常结果 - 你需要手动实现 [onNoSuchConstructor] 方法
         * @return [Result] 可继续向下监听
         */
        fun ignored(): Result {
            isShutErrorPrinting = true
            return this
        }

        /**
         * 忽略异常并停止打印任何错误日志
         *
         * - ❗此方法已弃用 - 在之后的版本中将直接被删除
         *
         * - ❗请现在转移到 [ignored]
         * @return [Result] 可继续向下监听
         */
        @Deprecated(message = "请使用新的命名方法", ReplaceWith(expression = "ignored()"))
        fun ignoredError() = ignored()

        /**
         * [Constructor] 实例处理类
         *
         * 调用与创建目标实例类对象
         *
         * - ❗请使用 [get]、[wait]、[all]、[waitAll] 方法来获取 [Instance]
         * @param constructor 当前 [Constructor] 实例对象
         */
        inner class Instance internal constructor(private val constructor: Constructor<*>?) {

            /**
             * 执行 [Constructor] 创建目标实例
             * @param param 构造方法参数
             * @return [Any] or null
             */
            private fun baseCall(vararg param: Any?) = constructor?.newInstance(*param)

            /**
             * 执行 [Constructor] 创建目标实例 - 不指定目标实例类型
             * @param param 构造方法参数
             * @return [Any] or null
             */
            fun call(vararg param: Any?) = baseCall(*param)

            /**
             * 执行 [Constructor] 创建目标实例 - 指定 [T] 目标实例类型
             * @param param 构造方法参数
             * @return [T] or null
             */
            fun <T> newInstance(vararg param: Any?) = baseCall(*param) as? T?

            override fun toString() = "[${constructor?.name ?: "<empty>"}]"
        }
    }
}