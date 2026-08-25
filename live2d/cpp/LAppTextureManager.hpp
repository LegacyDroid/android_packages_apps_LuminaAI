#pragma once

/*
 * LAppTextureManager.hpp
 *
 * Loads PNG model textures and uploads them to OpenGL, ported from the
 * Cubism sample. Decoding uses stb_image.
 */

#include <string>

#include <CubismFramework.hpp>
#include <Type/csmMap.hpp>
#include <Type/csmVector.hpp>
#include <GLES2/gl2.h>

class LAppTextureManager
{
public:
    struct TextureInfo
    {
        GLuint id;            // GL texture name
        int width;            // width in px
        int height;           // height in px
        std::string fileName; // asset path it came from
    };

    /** Decoded image sitting in CPU memory, waiting for the GPU upload. */
    struct DecodedImage
    {
        unsigned char* pixels = nullptr; // RGBA data, free with stbi_image_free
        int width = 0;
        int height = 0;
        std::string fileName;            // asset path it came from
    };

    LAppTextureManager() = default;
    ~LAppTextureManager();

    LAppTextureManager(const LAppTextureManager&) = delete;
    LAppTextureManager& operator=(const LAppTextureManager&) = delete;

    /**
     * Decodes a PNG and downscales it if it is huge. CPU only, safe from
     * worker threads. pixels is null on failure.
     */
    DecodedImage DecodePngFile(const std::string& fileName);

    /**
     * Uploads a decoded image, GL thread only. Frees the pixel buffer
     * afterwards. Returns null on failure.
     */
    TextureInfo* CreateTextureFromDecoded(DecodedImage& image);

    /** Drops the cache bookkeeping, call when the context is lost. */
    void ReleaseTexturesInfo();

    /** Deletes the GL textures of everything cached. */
    void ReleaseTextures();

private:
    Csm::csmVector<TextureInfo*> _texturesInfo; // loaded textures
};