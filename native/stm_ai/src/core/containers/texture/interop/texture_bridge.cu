#include "texture_bridge.h"
#include "../Texture.h"
#include <iostream>
#include <stdexcept>

// 设计说明：
// 所有 bridge 函数在捕获异常后，先打印诊断信息到 stderr，然后 **重新抛出** 异常。
// 这样 JNI 层的 try-catch (JNI_CATCH_TRANSLATE) 能将 C++ 异常转换为 Java RuntimeException，
// 让 Java 调用方感知失败，而不是让 CUDA/OpenGL 错误残留导致后续操作雪崩式失败。
// 唯一不重抛的是 Destroy：不应抛异常（类似析构）。
// 
// 实现模式（对标 vector_bridge.cu）：
// - Create/Delete 操作 Texture 对象的生命周期
// - Getters 读取 Texture 对象的状态
// - SnapshotFrom 调用 Texture 对象的行为方法

extern "C"
{

    Texture* TextureCreate(int width, int height)
    {
        try
        {
            return new Texture(width, height);
        }
        catch (const std::exception& e)
        {
            std::cerr << "Error in TextureCreate: " << e.what() << std::endl;
            return nullptr;
        }
    }

    void TextureDestroy(Texture* texture)
    {
        delete texture;
    }

    unsigned int TextureGetId(const Texture* texture)
    {
        if (!texture)
        {
            std::cerr << "Error in TextureGetId: null texture pointer" << std::endl;
            return 0;
        }
        return texture->getTextureId();
    }

    intptr_t TextureGetCudaResourceHandle(const Texture* texture)
    {
        if (!texture)
        {
            std::cerr << "Error in TextureGetCudaResourceHandle: null texture pointer" << std::endl;
            return 0;
        }
        return reinterpret_cast<intptr_t>(texture->getCudaResource());
    }

    int TextureGetWidth(const Texture* texture)
    {
        if (!texture)
        {
            std::cerr << "Error in TextureGetWidth: null texture pointer" << std::endl;
            return 0;
        }
        return texture->getWidth();
    }

    int TextureGetHeight(const Texture* texture)
    {
        if (!texture)
        {
            std::cerr << "Error in TextureGetHeight: null texture pointer" << std::endl;
            return 0;
        }
        return texture->getHeight();
    }

    void TextureSnapshotFrom(Texture* texture, unsigned int srcTextureId, int srcWidth, int srcHeight)
    {
        if (!texture)
        {
            std::cerr << "Error in TextureSnapshotFrom: null texture pointer" << std::endl;
            throw std::invalid_argument("Null texture pointer");
        }

        try
        {
            texture->snapshotFrom(srcTextureId, srcWidth, srcHeight);
        }
        catch (const std::exception& e)
        {
            std::cerr << "Error in TextureSnapshotFrom: " << e.what() << std::endl;
            throw;
        }
    }

}