from core import Span, FloatVector
from urana_process.fittable_mapper.nn.i_neural_network import INeuralNetwork
from urana_process.fittable_mapper.nn.nn_encoding_profile import NnEncodingProfile
from urana_process.fittable_mapper.nn.original_cnn.containers.cnn_network_data import CnnNetworkData
from urana_process.fittable_mapper.nn.original_cnn.containers.io.cnn_io import CnnIO
from urana_process.fittable_mapper.nn.original_cnn.containers.io.cnn_io_layer_gradients import CnnIOLayerGradients
from urana_process.fittable_mapper.nn.original_cnn.containers.io.value.cnn_target_vector import CnnTargetVector
from urana_process.fittable_mapper.nn.original_cnn.containers.cnn_fw_trace_for_bw import CnnFwTraceForBw
from urana_process.fittable_mapper.nn.original_cnn.mapping.inference.cnn_inference_ops import cnnForwardLayer, cnnRefreshCache
from urana_process.fittable_mapper.nn.original_cnn.mapping.training.cnn_training_ops import cnnBackwardLayer


CNN_FEELING_LENGTH = 1920 * 1080 * 3
CNN_BEHAVIOR_LENGTH = 256
CNN_DT_LENGTH = 1
CNN_TIME_ORIENTATION_UNIT = 4

CNN_PROFILE = NnEncodingProfile(
    CNN_FEELING_LENGTH, CNN_BEHAVIOR_LENGTH, CNN_DT_LENGTH, CNN_TIME_ORIENTATION_UNIT)

# CNN 训练学习率（浮点权重梯度下降步长）。
# BNN 的 bit 权重用 sign 函数固定步长更新，无此参数；CNN 浮点权重需 lr 缩放梯度。
CNN_LEARNING_RATE = 0.01


def _fullSpan(v):
    return Span(0, v.size())


class AbstractCnnNeuralNetwork(INeuralNetwork):
    def __init__(self, inputSize, outputSize, networkData=None):
        self.inputSize = inputSize
        self.outputSize = outputSize
        if networkData is not None:
            self.networkData = networkData
        else:
            self.networkData = CnnNetworkData(inputSize, outputSize)
        self.io = CnnIO(inputSize, outputSize)
        self.gradients = CnnIOLayerGradients(inputSize, outputSize)
        self.target = CnnTargetVector(outputSize)
        # 构造期一次性刷新 idx/w（非热路径，stream 0 + 同步）。
        # 新建（PCG 随机 p）与 loadFromFile 路径都需要：idx/w 是 p 的派生缓存，未刷新则前向读垃圾值。
        cnnRefreshCache(self.networkData.getHyperparameters(), 0)

    def encodingProfile(self):
        return CNN_PROFILE

    def copyToInput(self, _: "<-", region, src, stream):
        inputVec = self.io.getInput().getVector()
        inputVec.setRegion("<-", region, src, stream)

    def copyFromInput(self, region, stream, _: "->", dst):
        inputVec = self.io.getInput().getVector()
        dst.copyRegionFrom("<-", inputVec, region, _fullSpan(dst), stream)

    def copyFromOutput(self, region, stream, _: "->", dst):
        outputVec = self.io.getOutput().getVector()
        dst.copyRegionFrom("<-", outputVec, region, _fullSpan(dst), stream)

    def copyToInputFromHost(self, _: "<-", region, src, stream): ...

    def copyToInputFromLong(self, _: "<-", region, value, stream): ...

    def forward(self, x, stream, _: "->", y, fw_trace_for_bw):
        cnnForwardLayer(
            self.io.getA0(), self.networkData.getHyperparameters(), stream, "->",
            self.io.getA1(), fw_trace_for_bw)

    def backward(self, fw_trace_for_bw, t, stream, _: "->", buf_tC, buf_hp):
        hp = self.networkData.getHyperparameters()
        cnnBackwardLayer(
            fw_trace_for_bw, hp, self.target.getVector(),
            stream, "->",
            self.io.getA0(), self.gradients.getDzWorkspace(), self.gradients.getInputLayerGradient().getVector(),
            buf_tC, buf_hp, CNN_LEARNING_RATE)

    def getHyperparameters(self):
        return self.networkData.getHyperparameters()

    def setTarget(self, _: "<-", region, src, stream):
        self.target.getVector().setRegion("<-", region, src, stream)

    def computeOutputGradient(self, _: "<-", currentOutput, region, stream): ...

    def injectOutputGradient(self, _: "<-", region, gradC, stream):
        outputGradVec = self.gradients.getOutputLayerGradient().getVector()
        outputGradVec.setRegion("<-", region, gradC, stream)

    def copyFromInputGradient(self, region, stream, _: "->", dst):
        inputGradVec = self.gradients.getInputLayerGradient().getVector()
        dst.copyRegionFrom("<-", inputGradVec, region, _fullSpan(dst), stream)

    def gradientToInput(self, _: "<-", gradC, inputC, stream): ...

    def injectOutputGradientFromInputGradient(self, _: "<-", region, stream):
        outputGradVec = self.gradients.getOutputLayerGradient().getVector()
        inputGradVec = self.gradients.getInputLayerGradient().getVector()
        outputGradVec.copyRegionFrom("<-", inputGradVec, region, region, stream)

    def gradientToInputFromInternal(self, _: "<-", region, inputC, stream): ...

    def zeroGradient(self, stream, _: "->", gradVec):
        gradVec.multiplyByScalar("<-", 0, stream)

    def zeroVector(self, stream, _: "->", vec):
        vec.multiplyByScalar("<-", 0, stream)

    def createVector(self, size):
        return FloatVector(size)

    def createGradientVector(self, size):
        return FloatVector(size)

    def createFwTraceForBw(self):
        # z/y 同 sizeA1=outputSize：前向写 z（pre-activation）与 y（σ(z)），反向读 y 算 δ。
        return CnnFwTraceForBw(FloatVector(self.outputSize), FloatVector(self.outputSize))

    def save(self, folderPath):
        self.networkData.save(folderPath)

    def loadVector(self, path):
        return FloatVector.loadFromFile(path)

    def loadGradientVector(self, path):
        return FloatVector.loadFromFile(path)

    def close(self): ...
