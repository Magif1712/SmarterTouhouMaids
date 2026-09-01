from core import Span


class FeelingSpan(Span):
    def __init__(self, offset, length):
        super().__init__(offset, length)