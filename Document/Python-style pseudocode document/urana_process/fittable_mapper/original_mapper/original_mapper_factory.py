from urana_process.fittable_mapper.fittable_mapper_factory import FittableMapperFactory
from urana_process.fittable_mapper.original_mapper.original_mapper import OriginalMapper


class OriginalMapperFactory(FittableMapperFactory):
    def create(self, nn, inputDomain, outputDomain):
        return OriginalMapper(nn, inputDomain, outputDomain)
