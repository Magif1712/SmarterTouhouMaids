from core import Span


class TargetTimeOrientationSpan(Span):
    def __init__(self, offset, length):
        super().__init__(offset, length)