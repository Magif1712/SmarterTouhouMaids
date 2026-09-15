package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree;

import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;

/**
 * 概念树注册收集事件（附属模组的注册入口，硬约束6 的时序锁）。
 * <p>
 * <b>时序</b>：主模组在 {@code InterModEnqueueEvent} 阶段于 mod 事件总线 post 本事件
 * （此时主模组与各层内置注册已在 {@code FMLCommonSetupEvent} 完成）；附属模组经
 * {@code @Mod.EventBusSubscriber(bus = MOD)} 监听本事件，向 {@link #builder()} 挂自己的
 * Node / Branch。freeze 发生在 {@code FMLLoadCompleteEvent}——收集必然在冻结前完成，
 * freeze 后 builder 拒写（抛 IllegalStateException）。
 * <p>
 * <b>注意</b>：注册代码在事件处理器里<b>同步</b>执行（不要 {@code enqueueWork} 到并行队列，
 * TreeRegistry 非线程安全）。
 * <p>
 * <b>类型级约束（本类为何 {@code implements IModBusEvent}）</b>：本事件在 <b>mod 事件总线</b>上
 * {@code post}，而 Forge 的 {@code EventBus.register} 会拒绝为 mod 总线上的
 * <b>非</b> {@code IModBusEvent} 事件注册监听器——直接抛 {@code IllegalArgumentException}，
 * 在附属模组构造期即崩服（而本类只定义事件、不注册监听器，故自身不报错，问题只在
 * 第一个真正监听它的附属模组上暴露）。
 * <p>
 * 因此「按 {@code @Mod.EventBusSubscriber(bus = MOD)} 监听本事件」这一契约，
 * <b>要求本类必须 implements IModBusEvent</b>。这是「事件所走的线」与「事件的类型」
 * 必须一致这一不变量在类型层面的编码：缺此声明时，下面的示例在文档里成立、在类型上不成立。
 * <p>
 * <b>主模组识别附属的唯一方式</b>：注册图中出现了附属命名空间的 Branch / Node
 * （主模组不 import 附属类、不硬编码附属 modid）。
 * <p>
 * 附属模组注册示例（可复制 {@code urana_process_original/.../BnnRegistration} 的样板结构，
 * 只需把监听事件换成本事件）：
 * <pre>{@code
 * @Mod.EventBusSubscriber(modid = "your_mod", bus = Mod.EventBusSubscriber.Bus.MOD)
 * public class YourRegistration {
 *     @SubscribeEvent
 *     public static void onCollect(RegistryCollectEvent e) {
 *         Node<NnFactory> nn = e.builder().node(YourNodeKeys.YOUR_NN);
 *         nn.addBranch(...);
 *     }
 * }
 * }</pre>
 */
public class RegistryCollectEvent extends Event implements IModBusEvent {
    private final TreeRegistry builder;

    public RegistryCollectEvent(TreeRegistry builder) {
        this.builder = builder;
    }

    /** 构建期注册表（freeze 后拒写）。 */
    public TreeRegistry builder() {
        return builder;
    }
}
