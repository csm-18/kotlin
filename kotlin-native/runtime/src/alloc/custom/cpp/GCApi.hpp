/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_GCAPI_HPP_
#define CUSTOM_ALLOC_CPP_GCAPI_HPP_

#include <cinttypes>
#include <cstdint>
#include <cstdlib>
#include <limits>

#include "Alignment.hpp"
#include "AtomicStack.hpp"
#include "ExtraObjectData.hpp"
#include "ExtraObjectPage.hpp"
#include "GC.hpp"
#include "GCStatistics.hpp"
#include "Memory.h"
#include "TypeLayout.hpp"

namespace kotlin::alloc {

struct HeapObject {
    using descriptor = type_layout::Composite<HeapObject, gc::GC::ObjectData, ObjectBody>;

    static descriptor make_descriptor(const TypeInfo* typeInfo) noexcept {
        return descriptor{{}, type_layout::descriptor_t<ObjectBody>{typeInfo}};
    }

    static HeapObject& from(gc::GC::ObjectData& objectData) noexcept {
        return *make_descriptor(nullptr).fromField<0>(&objectData);
    }

    static HeapObject& from(ObjHeader* object) noexcept {
        RuntimeAssert(object->heap(), "Object %p does not reside in the heap", object);
        return *make_descriptor(nullptr).fromField<1>(ObjectBody::from(object));
    }

    gc::GC::ObjectData& objectData() noexcept { return *make_descriptor(nullptr).field<0>(this).second; }

    ObjHeader* object() noexcept { return make_descriptor(nullptr).field<1>(this).second->header(); }

private:
    HeapObject() = delete;
    ~HeapObject() = delete;
};

// Needs to be kept compatible with `HeapObject` just like `ArrayHeader` is compatible
// with `ObjHeader`: the former can always be casted to the other.
struct HeapArray {
    using descriptor = type_layout::Composite<HeapArray, gc::GC::ObjectData, ArrayBody>;

    static descriptor make_descriptor(const TypeInfo* typeInfo, uint32_t size) noexcept {
        return descriptor{{}, type_layout::descriptor_t<ArrayBody>{typeInfo, size}};
    }

    static HeapArray& from(gc::GC::ObjectData& objectData) noexcept {
        return *make_descriptor(nullptr, 0).fromField<0>(&objectData);
    }

    static HeapArray& from(ArrayHeader* array) noexcept {
        RuntimeAssert(array->obj()->heap(), "Array %p does not reside in the heap", array);
        return *make_descriptor(nullptr, 0).fromField<1>(ArrayBody::from(array));
    }

    gc::GC::ObjectData& objectData() noexcept { return *make_descriptor(nullptr, 0).field<0>(this).second; }

    ArrayHeader* array() noexcept { return make_descriptor(nullptr, 0).field<1>(this).second->header(); }

private:
    HeapArray() = delete;
    ~HeapArray() = delete;
};

// Returns `true` if the `object` must be kept alive still.
bool SweepObject(uint8_t* object, FinalizerQueue& finalizerQueue, gc::GCHandle::GCSweepScope& sweepScope) noexcept;

// Returns `true` if the `extraObject` must be kept alive still
bool SweepExtraObject(mm::ExtraObjectData* extraObject, gc::GCHandle::GCSweepExtraObjectsScope& sweepScope) noexcept;

void* SafeAlloc(uint64_t size) noexcept;

void Free(void* ptr, size_t size) noexcept;

size_t GetAllocatedBytes() noexcept;

} // namespace kotlin::alloc

#endif
