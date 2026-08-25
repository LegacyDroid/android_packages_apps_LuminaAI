/*
 * PNG to OpenGL texture loader, Android port of the Cubism sample.
 * PNG bytes come in through LAppPal::LoadFileAsBytes.
 */

#include "LAppTextureManager.hpp"

#define STBI_NO_STDIO // memory only
#define STBI_ONLY_PNG
#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#include "LAppPal.hpp"
#include "JniBridgeC.hpp"

namespace {

// textures larger than this get halved, phones do not need more and huge
// uploads are slow or exceed the GL limit entirely
const int kMaxTextureSize = 2048;

// halves src once with a simple 2x2 box filter, caller owns both buffers
unsigned char* HalveTexture(const unsigned char* src, int w, int h, int* outW, int* outH)
{
    const int nw = w / 2;
    const int nh = h / 2;
    unsigned char* dst = new unsigned char[static_cast<size_t>(nw) * nh * 4];

    for (int y = 0; y < nh; y++)
    {
        for (int x = 0; x < nw; x++)
        {
            const int s0x = x * 2;
            const int s0y = y * 2;
            for (int c = 0; c < 4; c++)
            {
                int sum = 0;
                int count = 0;
                for (int dy = 0; dy < 2; dy++)
                {
                    for (int dx = 0; dx < 2; dx++)
                    {
                        const int sx = s0x + dx;
                        const int sy = s0y + dy;
                        if (sx < w && sy < h)
                        {
                            sum += src[static_cast<size_t>(sy) * w * 4 + sx * 4 + c];
                            count++;
                        }
                    }
                }
                dst[static_cast<size_t>(y) * nw * 4 + x * 4 + c] =
                    static_cast<unsigned char>(sum / count);
            }
        }
    }

    *outW = nw;
    *outH = nh;
    return dst;
}

} // namespace

LAppTextureManager::~LAppTextureManager()
{
    ReleaseTextures();
}

LAppTextureManager::DecodedImage LAppTextureManager::DecodePngFile(const std::string& fileName)
{
    DecodedImage result;
    result.fileName = fileName;

    // reading the file is the slow part on big textures
    const double startTime = LAppPal::GetSystemTime();
    Csm::csmSizeInt size = 0;
    unsigned char* png = reinterpret_cast<unsigned char*>(
        LAppPal::LoadFileAsBytes(fileName, &size));
    if (png == nullptr)
    {
        LAppPal::PrintLogLn("[Texture] load failed: %s", fileName.c_str());
        JniBridgeC::DetachThreadEnv(); // worker thread, detach before leaving
        return result;
    }

    int width = 0;
    int height = 0;
    int channels = 0;
    unsigned char* decoded = stbi_load_from_memory(png, static_cast<int>(size),
                                                   &width, &height, &channels, STBI_rgb_alpha);
    LAppPal::ReleaseBytes(png);
    if (decoded == nullptr)
    {
        LAppPal::PrintLogLn("[Texture] PNG decode failed: %s", fileName.c_str());
        JniBridgeC::DetachThreadEnv();
        return result;
    }

    // halve oversized textures so the upload stays cheap on mobile GPUs,
    // the quality loss is invisible at this size
    bool downscaled = false;
    while (width > kMaxTextureSize || height > kMaxTextureSize)
    {
        int newWidth = 0;
        int newHeight = 0;
        unsigned char* halved = HalveTexture(decoded, width, height, &newWidth, &newHeight);
        stbi_image_free(decoded);
        decoded = halved;
        width = newWidth;
        height = newHeight;
        downscaled = true;
    }

    LAppPal::PrintLogLn("[Texture] decoded %s (%dx%d%s) in %.2fs", fileName.c_str(), width,
                        height, downscaled ? ", downscaled" : "",
                        LAppPal::GetSystemTime() - startTime);

    result.pixels = decoded;
    result.width = width;
    result.height = height;
    JniBridgeC::DetachThreadEnv();
    return result;
}

LAppTextureManager::TextureInfo* LAppTextureManager::CreateTextureFromDecoded(DecodedImage& image)
{
    if (image.pixels == nullptr || image.width <= 0 || image.height <= 0)
    {
        return nullptr;
    }

    GLuint textureId = 0;
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width, image.height, 0, GL_RGBA,
                 GL_UNSIGNED_BYTE, image.pixels);
    glGenerateMipmap(GL_TEXTURE_2D);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glBindTexture(GL_TEXTURE_2D, 0);

    // upload done, the pixel buffer can go
    stbi_image_free(image.pixels);
    image.pixels = nullptr;

    TextureInfo* info = new TextureInfo();
    info->id = textureId;
    info->width = image.width;
    info->height = image.height;
    info->fileName = image.fileName;
    _texturesInfo.PushBack(info);
    return info;
}

void LAppTextureManager::ReleaseTexturesInfo()
{
    for (Csm::csmUint32 i = 0; i < _texturesInfo.GetSize(); i++)
    {
        delete _texturesInfo[i];
    }
    _texturesInfo.Clear();
}

void LAppTextureManager::ReleaseTextures()
{
    for (Csm::csmUint32 i = 0; i < _texturesInfo.GetSize(); i++)
    {
        glDeleteTextures(1, &(_texturesInfo[i]->id));
    }
    ReleaseTexturesInfo();
}