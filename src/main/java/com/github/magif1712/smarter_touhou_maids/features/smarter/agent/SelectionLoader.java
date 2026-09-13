package com.github.magif1712.smarter_touhou_maids.features.smarter.agent;

import java.util.LinkedHashMap;
import java.util.Map;

import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Node;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.RegistrySnapshot;
import com.github.magif1712.smarter_touhou_maids.features.smarter.agent.tree.Selection;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Selection 加载器（推论1 的落地：Selection 是分解选择 d 的<b>唯一显式入参</b>，
 * 本类是它从持久化/pending 到运行期的<b>唯一收口</b>——禁止第二读路径）。
 * <p>
 * 加载规则（旧存档/附属卸载安全）：
 * <ol>
 *   <li>数据源合并：NBT（{@code AiModes}）打底，pending（已发未确认）覆盖。</li>
 *   <li>过滤：指向不存在插槽或不存在分支的条目直接忽略——解析时自然回退默认，
 *       回退发生在 {@code Resolver}（不在加载时替换），保证冻结后的图语义一致。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class SelectionLoader {
    private SelectionLoader() {
    }

    /**
     * 构造 maid 的当前有效 Selection（NBT ∪ pending，pending 优先；未知条目过滤）。
     */
    public static Selection effectiveSelection(RegistrySnapshot snapshot, EntityMaid maid) {
        if (maid == null || snapshot == null) {
            return Selection.empty();
        }
        Map<ResourceLocation, ResourceLocation> merged = SmarterClientState.INSTANCE.getAllModes(maid);
        Map<ResourceLocation, ResourceLocation> filtered = new LinkedHashMap<>();
        merged.forEach((nodeId, branchId) -> {
            Node<?> node = snapshot.node(nodeId);
            if (node != null && node.branch(branchId) != null) {
                filtered.put(nodeId, branchId);
            }
        });
        return new Selection(filtered);
    }
}
