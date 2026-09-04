from urana_process.fittable_mapper.nn.nn_factory import NnFactory
from urana_process.fittable_mapper.nn.original_cnn.abstract_cnn_neural_network import CNN_PROFILE
from urana_process.fittable_mapper.nn.original_cnn.original_cnn import CnnNeuralNetwork


class CnnNnFactory(NnFactory):
    """朴素 CNN 的 NN 工厂（叶子工厂）：encodingProfile() 返回 CNN_PROFILE，
    create(...) 忽略 slot 直接新建 CnnNeuralNetwork（照搬伪代码，无 load+fallback）。
    """

    def encodingProfile(self):
        return CNN_PROFILE

    def create(self, slot, inputSize, outputSize):
        return CnnNeuralNetwork(inputSize, outputSize)