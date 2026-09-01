class CnnTargetVector:
    def __init__(self, size):
        self.underlying = FloatVector(size)

    def getVector(self):
        return self.underlying

    def close(self): ...


from core import FloatVector
