from urana_process.fittable_mapper.nn.original_cnn.containers.cnn_hyperparameters import CnnHyperparameters


class CnnNetworkData:
    """CNN 网络数据：把"网络 = 超参"这个不实在约束，实在化为一个对象（真善美第4条）。

    两个构造路径：按 (sizeA0, sizeA1) 新建（PCG 随机初始化 p/q/l/r/b），
    或直接注入已加载的 CnnHyperparameters。save 委托超参保存语义权重，
    loadFromFile 委托超参加载（idx/w 由调用方后续 cnnRefreshCache 重算）。
    close() 真实释放超参（含派生缓存 idx/w）。
    """

    def __init__(self, sizeA0=None, sizeA1=None, hyperparameters=None):
        if hyperparameters is not None:
            self.hyperparameters = hyperparameters
        else:
            self.hyperparameters = CnnHyperparameters(sizeA0, sizeA1)

    def save(self, folderPath):
        self.hyperparameters.save(folderPath)

    @staticmethod
    def loadFromFile(folderPath):
        hp = CnnHyperparameters.loadFromFile(folderPath)
        return CnnNetworkData(hyperparameters=hp)

    def getHyperparameters(self):
        return self.hyperparameters

    def close(self): ...
