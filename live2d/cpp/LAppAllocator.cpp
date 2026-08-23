#include "LAppAllocator.hpp"

#include <stdlib.h>
#include <stddef.h>

void* LAppAllocator::Allocate(const Csm::csmSizeType size)
{
    return malloc(size);
}

void LAppAllocator::Deallocate(void* memory)
{
    free(memory);
}

void* LAppAllocator::AllocateAligned(const Csm::csmSizeType size, const Csm::csmUint32 alignment)
{
    size_t offset;
    size_t shift;
    size_t alignedAddress;
    void* allocation;
    void** preamble;

    offset = alignment - 1 + sizeof(void*);
    allocation = Allocate(size + static_cast<Csm::csmUint32>(offset));

    alignedAddress = reinterpret_cast<size_t>(allocation) + sizeof(void*);
    shift = alignedAddress % alignment;
    if (shift)
    {
        alignedAddress += (alignment - shift);
    }

    // Store the base pointer just before the aligned address so
    // DeallocateAligned can find it again.
    preamble = reinterpret_cast<void**>(alignedAddress);
    preamble[-1] = allocation;

    return reinterpret_cast<void*>(alignedAddress);
}

void LAppAllocator::DeallocateAligned(void* alignedMemory)
{
    void** preamble = static_cast<void**>(alignedMemory);
    Deallocate(preamble[-1]);
}