#pragma once

/*
 * LAppAllocator.hpp
 *
 * Allocator handed to CubismFramework::StartUp. Plain malloc and free with
 * aligned allocation on top.
 */

#include <CubismFramework.hpp>

class LAppAllocator : public Csm::ICubismAllocator
{
public:
    void* Allocate(const Csm::csmSizeType size) override;
    void Deallocate(void* memory) override;
    void* AllocateAligned(const Csm::csmSizeType size, const Csm::csmUint32 alignment) override;
    void DeallocateAligned(void* alignedMemory) override;
};