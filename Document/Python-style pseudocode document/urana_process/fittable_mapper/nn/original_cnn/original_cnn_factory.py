from urana_process.fittable_mapper.nn.nn_factory import NnFactory
from urana_process.fittable_mapper.nn.original_cnn.abstract_cnn_neural_network import CNN_PROFILE
from urana_process.fittable_mapper.nn.original_cnn.original_cnn import CnnNeuralNetwork


class CnnNnFactory(NnFactory):
    def encodingProfile(self):
        return CNN_PROFILE

    def create(self, slot, inputSize, outputSize):
        return CnnNeuralNetwork(inputSize, outputSize)
