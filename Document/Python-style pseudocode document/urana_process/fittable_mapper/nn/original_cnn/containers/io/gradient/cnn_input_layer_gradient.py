class CnnInputLayerGradient:
    def __init__(self, underlying):
        self.underlying = underlying

    def getVector(self):
        return self.underlying

    def close(self): ...
