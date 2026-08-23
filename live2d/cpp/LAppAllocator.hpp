#pragma once

/**
 * @file LAppAllocator.hpp
 *
 * Memory allocator handed to CubismFramework::StartUp. Simple malloc/free
 * wrapper with aligned allocation support (aligned pointers store the base
 * pointer one slot before the aligned address).
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