package com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.original_mapper;

import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.FittableMapper;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.FittableMapperFactory;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.nn.INeuralNetwork;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.io.InputVectorDomain;
import com.github.magif1712.smarter_touhou_maids.features.smarter.modes.mapper.io.OutputVectorDomain;

/**
 * Urana 映射器工厂（叶子）：{@code createMapped(...)} 直接新建 {@link OriginalMapper}。
 * nn 的组装（provider→profile→domain→实例）由 {@link FittableMapperFactory} 的 default 方法承载。
 */
public class OriginalMapperFactory implements FittableMapperFactory {

    @Override
    public FittableMapper createMapped(INeuralNetwork nn, InputVectorDomain inputDomain, OutputVectorDomain outputDomain) {
        return new OriginalMapper(nn, inputDomain, outputDomain);
    }
}
