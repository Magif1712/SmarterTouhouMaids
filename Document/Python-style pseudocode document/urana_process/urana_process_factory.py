from urana_process.urana_system import UranaSystem
from urana_process.semantics.containers.io.io_domain import IODomain
from urana_process.fittable_mapper.nn.nn_registry import NnRegistry
from urana_process.fittable_mapper.fittable_mapper_registry import FittableMapperRegistry


class UranaProcessFactory:
    """组合根（对应正式项目 UranaProcessFactory）：装配时序——选型先于构造。

    时序（每一步的输入都来自上一步的输出，不可调换）：
      ① NnRegistry.resolve(config["nn"]) → NnFactory          按名解析 NN 工厂
      ② FittableMapperRegistry.resolve(config["mapper"])      按名解析映射器工厂
      ③ nnFactory.encodingProfile()                           问 NN：你的载体怎么编码各语义对象？
      ④ IODomain(profile)                                      据此推导 span 布局（编码方案）
      ⑤ nnFactory.create(slot, inLen, outLen)                 用推导出的总长创建 NN 实例
      ⑥ mapperFactory.create(nn, inputDomain, outputDomain)   装配可拟合映射器
      ⑦ UranaSystem(mapper, fastMinDtMillis, slowMinDtMillis) 流程系统——只认映射器

    本类经注册表拿具体工厂，包内零具体实现 import。
    换 NN：config["nn"] 换名字（工厂需已注册）。
    换映射器：config["mapper"] 换名字。
    流程系统零改动。
    """

    def create(self, config, slot):
        nnFactory = NnRegistry.resolve(config["nn"])
        mapperFactory = FittableMapperRegistry.resolve(config["mapper"])

        profile = nnFactory.encodingProfile()
        ioDomain = IODomain(profile)
        inputDomain = ioDomain.getInputDomain()
        outputDomain = ioDomain.getOutputDomain()

        nn = nnFactory.create(slot, inputDomain.totalLength(), outputDomain.totalLength())

        mapper = mapperFactory.create(nn, inputDomain, outputDomain)

        system = UranaSystem(
            mapper,
            config.get("fastMinDtMillis", 0),
            config.get("slowMinDtMillis", 0))
        return system
