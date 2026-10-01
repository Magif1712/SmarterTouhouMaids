package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.bnn_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.io.OutputVectorDomain;

/**
 * BNN 映射器工厂（叶子）：{@code createMapped(...)} 直接新建 {@link BnnMapper}。
 * <p>
 * 与 {@link OriginalMapperFactory} 对称——唯一差异是产出 {@link BnnMapper}（BoolVector 载体）
 * 而非 OriginalMapper（FloatVector 载体）。
 */
public class BnnMapperFactory implements FittableMapperFactory {

    @Override
    public FittableMapper createMapped(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        return new BnnMapper(nn, inputDomain, outputDomain);
    }
}
