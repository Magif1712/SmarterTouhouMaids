class NnRegistry:
    """NN 工厂注册表：按名字解析 NnFactory，包内代码不 import 具体工厂。

    具体工厂由外部（组合根/宿主环境）注册，UranaProcessFactory 经本表按名解析。
    """
    _factories = {}

    @staticmethod
    def register(name, nnFactory):
        NnRegistry._factories[name] = nnFactory

    @staticmethod
    def resolve(name):
        return NnRegistry._factories[name]
