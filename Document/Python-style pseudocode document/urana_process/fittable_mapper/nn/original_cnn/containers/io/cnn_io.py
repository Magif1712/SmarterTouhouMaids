from core import FloatVector
from urana_process.fittable_mapper.nn.original_cnn.containers.io.value.cnn_input_vector import CnnInputVector
from urana_process.fittable_mapper.nn.original_cnn.containers.io.value.cnn_output_vector import CnnOutputVector


class CnnIO:
    def __init__(self, sizeA0, sizeA1):
        self.input = CnnInputVector(sizeA0)
        self.output = CnnOutputVector(sizeA1)

    def getInput(self):
        return self.input

    def getOutput(self):
        return self.output

    def getA0(self):
        return self.input.getVector()

    def getA1(self):
        return self.output.getVector()

    def close(self): ...
