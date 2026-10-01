/**
 * 附身：smarter 域的<b>玩法支撑物</b>（玩家以女仆的视角观察与操作）。
 * <p>
 * <b>它为什么住在骨架面里</b>（W48）：附身<b>不挂在装配图上</b>——既不定义插槽，也不是任一插槽的候选；
 * 它与执行（{@code execution}）、网络（{@code network}）、状态（{@code state}）同属"让模式存在并运转"的
 * 支撑物，故与骨架同属<b>一个面</b>（{@code infrastructure}），面内按机制种类平级分类。
 * <p>
 * 旧 javadoc 写的是"放在 smarter 层，与 agent/ 并列"——那描述的是 W43 之前已不存在的结构；
 * 按原则4（目录里只留与实物相符的文字）重写。
 * <p>
 * 本包功能类似旁观者模式：玩家不会真正控制实体，只是以实体的视角观察，但附带给额外的区块更新等功能；
 * 附身的玩家是车万小女仆的工具。
 */
package com.github.magif1712.smarter_touhou_maids.features.smarter.infrastructure.possession;