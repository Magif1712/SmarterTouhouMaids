#include <cuda_runtime.h>

#include <cstddef>
#include <cstdint>

struct TlmCudaEngine {
  int width;
  int height;
  std::uint8_t* d_rgb;
  int* d_out_control_value;
};

static __global__ void tlm_ai_center_pixel_kernel(const std::uint8_t* rgb, int width, int height, int* out_control_value) {
  const int cx = width / 2;
  const int cy = height / 2;
  const std::size_t idx = (static_cast<std::size_t>(cy) * static_cast<std::size_t>(width) + static_cast<std::size_t>(cx)) * 3u;
  const int r = static_cast<int>(rgb[idx]);

  int action = 0;
  if (r < 64) {
    action = 0;
  } else if (r < 128) {
    action = 1;
  } else if (r < 192) {
    action = 2;
  } else {
    action = 3;
  }

  int control = 0;
  switch (action) {
    case 0:
      control = 0;
      break;
    case 1:
      control = 100;
      break;
    case 2:
      control = 200;
      break;
    case 3:
      control = 255;
      break;
    default:
      control = 255;
      break;
  }

  *out_control_value = control;
}

extern "C" {
TlmCudaEngine* tlm_cuda_create(int width, int height) {
  if (width <= 0 || height <= 0) {
    return nullptr;
  }

  auto* e = new TlmCudaEngine{};
  e->width = width;
  e->height = height;
  e->d_rgb = nullptr;
  e->d_out_control_value = nullptr;

  const std::size_t rgb_bytes = static_cast<std::size_t>(width) * static_cast<std::size_t>(height) * 3u;

  if (cudaMalloc(reinterpret_cast<void**>(&e->d_rgb), rgb_bytes) != cudaSuccess) {
    delete e;
    return nullptr;
  }
  if (cudaMalloc(reinterpret_cast<void**>(&e->d_out_control_value), sizeof(int)) != cudaSuccess) {
    cudaFree(e->d_rgb);
    delete e;
    return nullptr;
  }
  return e;
}

void tlm_cuda_destroy(TlmCudaEngine* e) {
  if (e == nullptr) {
    return;
  }
  if (e->d_rgb != nullptr) {
    cudaFree(e->d_rgb);
  }
  if (e->d_out_control_value != nullptr) {
    cudaFree(e->d_out_control_value);
  }
  delete e;
}

bool tlm_cuda_upload_rgb(TlmCudaEngine* e, const std::uint8_t* rgb, std::size_t len) {
  if (e == nullptr || rgb == nullptr) {
    return false;
  }
  const std::size_t expected = static_cast<std::size_t>(e->width) * static_cast<std::size_t>(e->height) * 3u;
  const std::size_t copy_len = len < expected ? len : expected;
  return cudaMemcpy(e->d_rgb, rgb, copy_len, cudaMemcpyHostToDevice) == cudaSuccess;
}

int tlm_cuda_step(TlmCudaEngine* e) {
  if (e == nullptr) {
    return -1;
  }
  tlm_ai_center_pixel_kernel<<<1, 1>>>(e->d_rgb, e->width, e->height, e->d_out_control_value);
  if (cudaDeviceSynchronize() != cudaSuccess) {
    return -1;
  }
  int out = 0;
  if (cudaMemcpy(&out, e->d_out_control_value, sizeof(int), cudaMemcpyDeviceToHost) != cudaSuccess) {
    return -1;
  }
  return out;
}
}
