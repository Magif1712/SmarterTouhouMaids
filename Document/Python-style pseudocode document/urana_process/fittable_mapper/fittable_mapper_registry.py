class FittableMapperRegistry:
    """可拟合映射器工厂注册表：按名字解析 FittableMapperFactory，包内代码不 import 具体工厂。

    具体工厂由外部（组合根/宿主环境）注册，UranaProcessFactory 经本表按名解析。
    """
    _factories = {}

    @staticmethod
    def register(name, mapperFactory):
        FittableMapperRegistry._factories[name] = mapperFactory

    @staticmethod
    def resolve(name):
        return FittableMapperRegistry._factories[name]
