from urana_process.fittable_mapper.nn.original_cnn.abstract_cnn_neural_network import AbstractCnnNeuralNetwork
from urana_process.fittable_mapper.nn.original_cnn.containers.cnn_network_data import CnnNetworkData


class CnnNeuralNetwork(AbstractCnnNeuralNetwork):
    """朴素 CNN 神经网络（叶子）：AbstractCnnNeuralNetwork 的直系子类，无额外行为。

    两个构造路径：(inputSize, outputSize, networkData)（networkData 可 None），
    与便利构造 (inputSize, outputSize)（等价于 networkData=None）。

    loadFromFile：从加载的 hp 反推 inputSize=sizeA0、outputSize=sizeA1。
    """

    def __init__(self, inputSize, outputSize, networkData=None):
        super().__init__(inputSize, outputSize, networkData)

    @staticmethod
    def loadFromFile(folderPath):
        net = CnnNetworkData.loadFromFile(folderPath)
        hp = net.getHyperparameters()
        return CnnNeuralNetwork(hp.getSizeA0(), hp.getSizeA1(), net)