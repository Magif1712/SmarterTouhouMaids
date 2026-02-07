cmake_minimum_required(VERSION 3.20)

project(tlm_ai LANGUAGES CXX CUDA)

find_package(CUDAToolkit REQUIRED)

find_package(JNI)
if(NOT JNI_FOUND)
  set(TLM_JAVA_HOME "" CACHE PATH "Path to a JDK (must contain include/jni.h)")
  if(TLM_JAVA_HOME STREQUAL "" AND DEFINED ENV{JAVA_HOME})
    set(TLM_JAVA_HOME "$ENV{JAVA_HOME}")
  endif()
  if(TLM_JAVA_HOME STREQUAL "")
    message(FATAL_ERROR "Could NOT find JNI. Set JAVA_HOME to a JDK, or pass -DTLM_JAVA_HOME=path/to/jdk")
  endif()

  file(TO_CMAKE_PATH "${TLM_JAVA_HOME}" _JAVA_HOME_CMAKE)
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

add_library(tlm_ai SHARED
  src/tlm_ai.cpp
  src/tlm_ai_cuda.cu
  src/InterweavedBinaryTree/InterweavedBinaryTree.cpp
  src/InterweavedBinaryTree/InterweavedBinaryTree.cu
)

target_compile_features(tlm_ai PRIVATE cxx_std_17)
target_include_directories(tlm_ai PRIVATE ${JNI_INCLUDE_DIRS})
target_link_libraries(tlm_ai PRIVATE ${JNI_LIBRARIES})

target_compile_definitions(tlm_ai PRIVATE TLM_AI_USE_CUDA=1)
set_target_properties(tlm_ai PROPERTIES CUDA_STANDARD 17 CUDA_STANDARD_REQUIRED YES)
target_link_libraries(tlm_ai PRIVATE CUDA::cudart)

set_target_properties(tlm_ai PROPERTIES OUTPUT_NAME "tlm_ai")
