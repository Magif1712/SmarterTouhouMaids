from urana_process.fittable_mapper.nn.original_cnn.abstract_cnn_neural_network import AbstractCnnNeuralNetwork
from urana_process.fittable_mapper.nn.original_cnn.containers.cnn_network_data import CnnNetworkData


class CnnNeuralNetwork(AbstractCnnNeuralNetwork):
    def __init__(self, inputSize, outputSize, networkData=None):
        if networkData is not None:
            super().__init__(inputSize, outputSize, networkData)
        else:
            super().__init__(inputSize, outputSize)

    @staticmethod
    def loadFromFile(folderPath):
        net = CnnNetworkData.loadFromFile(folderPath)
        hp = net.getHyperparameters()
        return CnnNeuralNetwork(hp.getSizeA0(), hp.getSizeA1(), net)
