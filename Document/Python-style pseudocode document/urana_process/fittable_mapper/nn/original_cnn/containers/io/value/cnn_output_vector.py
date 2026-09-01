class CnnOutputVector:
    def __init__(self, size_or_vector):
        if isinstance(size_or_vector, int):
            self.underlying = FloatVector(size_or_vector)
        else:
            self.underlying = size_or_vector

    def getVector(self):
        return self.underlying

    def close(self): ...


from core import FloatVector
