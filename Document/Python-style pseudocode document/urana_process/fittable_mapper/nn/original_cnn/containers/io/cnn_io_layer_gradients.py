from core import FloatVector
from urana_process.fittable_mapper.nn.original_cnn.containers.io.gradient.cnn_input_layer_gradient import CnnInputLayerGradient
from urana_process.fittable_mapper.nn.original_cnn.containers.io.gradient.cnn_output_layer_gradient import CnnOutputLayerGradient


class CnnIOLayerGradients:
    def __init__(self, inputSize, outputSize):
        self.inputLayerGradient = CnnInputLayerGradient(FloatVector(inputSize))
        self.outputLayerGradient = CnnOutputLayerGradient(FloatVector(outputSize))
        self.dzWorkspace = FloatVector(outputSize)

    def getInputLayerGradient(self):
        return self.inputLayerGradient

    def getOutputLayerGradient(self):
        return self.outputLayerGradient

    def getDzWorkspace(self):
        return self.dzWorkspace

    def close(self): ...
