/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "HeapObject.hpp"

#include "gtest/gtest.h"
#include "gmock/gmock.h"

#include "Natives.h"
#include "Types.h"

using namespace kotlin;

namespace {

struct HeapHeader {
    int32_t flags;
};

using HeapArray = alloc::HeapArray<HeapHeader>;
using HeapObject = alloc::HeapObject<HeapHeader>;

inline constexpr uint32_t kArraySize = 11;

} // namespace

TEST(HeapObjectTest, Array) {
    auto descriptor = HeapArray::descriptorFrom(theArrayTypeInfo, kArraySize);
    ArrayHeader fakeArray;
    fakeArray.typeInfoOrMeta_ = const_cast<TypeInfo*>(theArrayTypeInfo);
    fakeArray.count_ = kArraySize;
    auto& heapArray = HeapArray::from(&fakeArray);
    EXPECT_THAT(heapArray.array(), &fakeArray);
    EXPECT_THAT(static_cast<HeapObject&>(heapArray).object()->array(), &fakeArray);
    EXPECT_THAT(
            reinterpret_cast<uint8_t*>(ArrayAddressOfElementAt(heapArray.array(), kArraySize)),
            testing::Le(reinterpret_cast<uint8_t*>(&heapArray) + descriptor.size()));
}

TEST(HeapObjectTest, ByteArray) {
    auto descriptor = HeapArray::descriptorFrom(theByteArrayTypeInfo, kArraySize);
    ArrayHeader fakeArray;
    fakeArray.typeInfoOrMeta_ = const_cast<TypeInfo*>(theByteArrayTypeInfo);
    fakeArray.count_ = kArraySize;
    auto& heapArray = HeapArray::from(&fakeArray);
    EXPECT_THAT(heapArray.array(), &fakeArray);
    EXPECT_THAT(static_cast<HeapObject&>(heapArray).object()->array(), &fakeArray);
    EXPECT_THAT(
            reinterpret_cast<uint8_t*>(ByteArrayAddressOfElementAt(heapArray.array(), kArraySize)),
            testing::Le(reinterpret_cast<uint8_t*>(&heapArray) + descriptor.size()));
}

TEST(HeapObjectTest, LongArray) {
    auto descriptor = HeapArray::descriptorFrom(theLongArrayTypeInfo, kArraySize);
    ArrayHeader fakeArray;
    fakeArray.typeInfoOrMeta_ = const_cast<TypeInfo*>(theLongArrayTypeInfo);
    fakeArray.count_ = kArraySize;
    auto& heapArray = HeapArray::from(&fakeArray);
    EXPECT_THAT(heapArray.array(), &fakeArray);
    EXPECT_THAT(static_cast<HeapObject&>(heapArray).object()->array(), &fakeArray);
    EXPECT_THAT(
            reinterpret_cast<uint8_t*>(LongArrayAddressOfElementAt(heapArray.array(), kArraySize)),
            testing::Le(reinterpret_cast<uint8_t*>(&heapArray) + descriptor.size()));
}
