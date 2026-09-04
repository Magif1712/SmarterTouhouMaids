/**
 * 此包用于定义 IO 向量内的各个子区域（Sub-span）。
 *
 * <p>设计决策：
 * 尽管可以通过一个通用的 {@code Subspan} 类并创建多个实例来表示不同的子区域，
 * 但本包选择为每一个子区域（如 Feeling, Behavior 等）创建一个独立的类。
 *
 * <p>设计理由：
 * 这种方法将“子域”的概念从一个易变的“变量名”提升到了一个稳定的“类型名”。
 * 通过使用类名（例如 {@code FeelingSpan}）来固化子域的定义，可以有效防止在代码重构或修改过程中
 * 无意间更改变量名而破坏领域模型的完整性。这使得代码结构更清晰，意图更明确，也更健壮。
 */
package com.github.magif1712.smarter_touhou_maids.features.smarter.agent.reflex_arc_system_agent_original.ai.process_ai.process.urana_process_original.semantics.containers.io.subspan;