class CnnOutputLayerGradient:
    def __init__(self, gradient):
        self.gradient = gradient

    def getVector(self):
        return self.gradient

    def close(self): ...
