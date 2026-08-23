#pragma once

/**
 * @file LAppTextureManager.hpp
 *
 * Loads PNG model textures and uploads them to OpenGL. Ported from the
 * official Cubism Android sample; uses stb_image (vendored in the SDK's
 * Samples/OpenGL/thirdParty) for PNG decoding.
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
        GLuint id;          ///< OpenGL texture name
        int width;          ///< texture width in px
        int height;         ///< texture height in px
        std::string fileName; ///< asset path it was loaded from
    };

    /** A CPU-side decoded image, ready to be uploaded to the GPU. */
    struct DecodedImage
    {
        unsigned char* pixels = nullptr; ///< RGBA data (free with stbi_image_free)
        int width = 0;                   ///< image width in px
        int height = 0;                  ///< image height in px
        std::string fileName;            ///< asset path it was decoded from
    };

    LAppTextureManager() = default;
    ~LAppTextureManager();

    LAppTextureManager(const LAppTextureManager&) = delete;
    LAppTextureManager& operator=(const LAppTextureManager&) = delete;

    /**
     * Decodes a PNG (read + decrypted through LAppPal, so encrypted textures
     * work transparently) and downscales oversized images. CPU-only, safe to
     * call from worker threads (the JNI thread is attached on demand).
     *
     * @return decoded image (pixels == nullptr on failure)
     */
    DecodedImage DecodePngFile(const std::string& fileName);

    /**
     * Uploads a decoded image to the GPU. Must run on the GL thread.
     * Frees the image's pixel buffer when the upload is done.
     *
     * @return texture info (owned by this manager) or nullptr on failure
     */
    TextureInfo* CreateTextureFromDecoded(DecodedImage& image);

    /** Deletes the cached bookkeeping (call when the GL context is lost). */
    void ReleaseTexturesInfo();

    /** Frees the GL textures of all cached entries (GL context teardown). */
    void ReleaseTextures();

private:
    Csm::csmVector<TextureInfo*> _texturesInfo; ///< cache of loaded textures
};