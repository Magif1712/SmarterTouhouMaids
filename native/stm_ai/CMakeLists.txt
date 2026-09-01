cmake_minimum_required(VERSION 3.20)

project(stm_ai LANGUAGES CXX CUDA)

find_package(CUDAToolkit REQUIRED)
find_package(OpenGL REQUIRED)

# CUDA 目标架构。需要覆盖 cache 中残留的旧值（曾只用 sm_75），
# 否则 Blackwell (RTX 50 系) 上只能靠 PTX JIT，运行时易抛 C++ 异常导致 JVM 崩溃。
# - sm_75:  Turing (RTX 20/GTX 16)
# - sm_120: Blackwell GB202/GB203 (RTX 5090/5080/5070 Ti)
# - sm_121: Blackwell GB205+ (RTX 5070/5060 Ti)
set(CMAKE_CUDA_ARCHITECTURES "75;120;121" CACHE STRING "CUDA target architectures" FORCE)

find_package(JNI)
if(NOT JNI_FOUND)
  set(STM_JAVA_HOME "" CACHE PATH "Path to a JDK (must contain include/jni.h)")
  if(STM_JAVA_HOME STREQUAL "" AND DEFINED ENV{JAVA_HOME})
    set(STM_JAVA_HOME "$ENV{JAVA_HOME}")
  endif()
  if(STM_JAVA_HOME STREQUAL "")
    message(FATAL_ERROR "Could NOT find JNI. Set JAVA_HOME to a JDK, or pass -DSTM_JAVA_HOME=path/to/jdk")
  endif()

  file(TO_CMAKE_PATH "${STM_JAVA_HOME}" _JAVA_HOME_CMAKE)
  set(JNI_INCLUDE_DIRS
    "${_JAVA_HOME_CMAKE}/include"
    "${_JAVA_HOME_CMAKE}/include/win32"
  )

  find_file(_JVM_LIB
    NAMES jvm.lib
    PATHS
      "${_JAVA_HOME_CMAKE}/lib"
      "${_JAVA_HOME_CMAKE}/lib/server"
      "${_JAVA_HOME_CMAKE}/jre/lib"
      "${_JAVA_HOME_CMAKE}/jre/bin/server"
    NO_DEFAULT_PATH
  )
  if(NOT _JVM_LIB)
    message(FATAL_ERROR "Could NOT find JNI jvm.lib under: ${_JAVA_HOME_CMAKE}. Pass a JDK root (one that contains lib/jvm.lib or lib/server/jvm.lib).")
  endif()

  find_file(_JAWT_LIB
    NAMES jawt.lib
    PATHS
      "${_JAVA_HOME_CMAKE}/lib"
      "${_JAVA_HOME_CMAKE}/lib/server"
      "${_JAVA_HOME_CMAKE}/jre/lib"
    NO_DEFAULT_PATH
  )

  set(JNI_LIBRARIES "${_JVM_LIB}")
  if(_JAWT_LIB)
    list(APPEND JNI_LIBRARIES "${_JAWT_LIB}")
  endif()
endif()

add_library(stm_ai SHARED
  src/core/containers/vector/interop/vector_jni.cpp
  src/core/containers/vector/interop/vector_bridge.cu
  src/core/containers/vector/interop/vector_bridge.h
  src/core/containers/vector/VectorMapping.cu
  src/core/containers/vector/VectorMapping.h
  src/core/containers/vector/Vector.cu
  src/core/execution/stream/interop/stream_ops_jni.cpp
  src/core/execution/stream/interop/stream_ops_bridge.cu
  src/core/execution/stream/interop/stream_ops_bridge.h
  src/core/execution/event/interop/event_ops_jni.cpp
  src/core/execution/event/interop/event_ops_bridge.cu
  src/core/execution/event/interop/event_ops_bridge.h
  src/core/execution/counter/interop/counter_ops_jni.cpp
  src/core/execution/counter/interop/counter_ops_bridge.cu
  src/core/execution/counter/interop/counter_ops_bridge.h
  src/core/interop/jni_helper.h

  # Diagnostics Component (显存监测)
  src/core/diagnostics/mem_info_jni.cpp

  # utils
  src/core/utils/CudaBitUtils.cu
  src/core/utils/CudaBitUtils.h

  # Bnn 家族核心 mechanics（nn/bnn/mapping/，original_bnn 与 standard_bnn 共享）
  # Gradient Ops Component (两阶段 BPTT 反向)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/gradient/interop/gradient_ops_jni.cpp
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/gradient/interop/gradient_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/gradient/interop/gradient_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/gradient/gradient_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/gradient/gradient_ops.h

  # Inference Ops Component (前向)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/inference/interop/inference_ops_jni.cpp
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/inference/interop/inference_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/inference/interop/inference_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/inference/inference_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/inference/inference_ops.h

  # Bnn Ops Component (BNN 特定: negateAndBinarize 梯度→输入转换)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/generic/interop/bnn_ops_jni.cpp
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/generic/interop/bnn_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/generic/interop/bnn_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/generic/bnn_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/mapping/generic/bnn_ops.h

  # Standard Bnn Leaf (输入变化门控重连 = 惊跳反射，standard_bnn 独有)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/standard_bnn/mapping/elastic_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/standard_bnn/mapping/elastic_ops.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/standard_bnn/mapping/interop/elastic_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/standard_bnn/mapping/interop/elastic_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process_original/nn/bnn/standard_bnn/mapping/interop/elastic_ops_jni.cpp

  # CNN 家族（nn/cnn/mapping/，fittable_mapper 对应）

  # CNN Inference Ops Component (前向 + refreshCache)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/inference/interop/inference_ops_jni.cpp
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/inference/interop/inference_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/inference/interop/inference_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/inference/inference_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/inference/inference_ops.h

  # CNN Gradient Ops Component (两阶段反向: δ + 参数梯度 + 输入梯度 + 权重更新 + 缓存刷新)
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/gradient/interop/gradient_ops_jni.cpp
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/gradient/interop/gradient_ops_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/gradient/interop/gradient_ops_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/gradient/gradient_ops.cu
  src/features/smarter/agent/reflex_arc_system_agent/ai/process_ai/process/urana_process/nn/cnn/mapping/gradient/gradient_ops.h

  # Vision Component
  src/features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/cuda_gl_vision.cu
  src/features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/cuda_gl_vision.h
  src/features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/interop/vision_bridge.cu
  src/features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/interop/vision_bridge.h
  src/features/smarter/agent/reflex_arc_system_agent/sensor/possession_sensor/vision/interop/vision_jni.cpp

  # Texture Component
  src/core/containers/texture/Texture.cu
  src/core/containers/texture/Texture.h
  src/core/containers/texture/fbo_extensions/FboExtensions.cu
  src/core/containers/texture/fbo_extensions/FboExtensions.h
  src/core/containers/texture/interop/texture_jni.cpp
  src/core/containers/texture/interop/texture_bridge.cu
  src/core/containers/texture/interop/texture_bridge.h
)

target_compile_features(stm_ai PRIVATE cxx_std_17)
target_include_directories(stm_ai PRIVATE ${JNI_INCLUDE_DIRS} ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(stm_ai PRIVATE ${JNI_LIBRARIES})
target_link_libraries(stm_ai PRIVATE OpenGL::GL)
target_compile_options(stm_ai PRIVATE
  $<$<COMPILE_LANGUAGE:CXX>:/utf-8>
  $<$<COMPILE_LANGUAGE:CXX>:/EHa>
  $<$<COMPILE_LANGUAGE:CUDA>:-Xcompiler=/utf-8>
  $<$<COMPILE_LANGUAGE:CUDA>:-Xcompiler=/EHa>
)

target_compile_definitions(stm_ai PRIVATE STM_AI_USE_CUDA=1)
set_target_properties(stm_ai PROPERTIES CUDA_STANDARD 17 CUDA_STANDARD_REQUIRED YES)
target_link_libraries(stm_ai PRIVATE CUDA::cudart)

set_target_properties(stm_ai PROPERTIES OUTPUT_NAME "stm_ai")