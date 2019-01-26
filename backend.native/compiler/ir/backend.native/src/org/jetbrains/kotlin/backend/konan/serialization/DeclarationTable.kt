package org.jetbrains.kotlin.backend.konan.serialization

import org.jetbrains.kotlin.backend.konan.llvm.*
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns

fun <K, V> MutableMap<K, V>.putOnce(k:K, v: () -> V): Unit {

    assert(!this.containsKey(k)/* || this[k] == v*/) {
        "adding ${v()} for $k, but it is already ${this[k]} for $k"
    }
    this.put(k, v())
}

class DescriptorTable {
    private val descriptors = mutableMapOf<DeclarationDescriptor, Long>()
    fun put(descriptor: DeclarationDescriptor, uniqId: UniqId) {
        descriptors.getOrPut(descriptor) { uniqId.index }
    }
    fun get(descriptor: DeclarationDescriptor) = descriptors[descriptor]
}

// TODO: We don't manage id clashes anyhow now.
class DeclarationTable(val builtIns: IrBuiltIns, val descriptorTable: DescriptorTable) {

    private val table = mutableMapOf<IrDeclaration, UniqId>()
    val debugIndex = mutableMapOf<UniqId, String>()
    val descriptors = descriptorTable
    private var currentIndex = 0L

    init {
        builtIns.knownBuiltins.forEach {
            table.put(it, UniqId(currentIndex++, false))
        }
    }

    fun uniqIdByDeclaration(value: IrDeclaration): UniqId = table.getOrPut(value) {
        val index = if (value.origin == IrDeclarationOrigin.FAKE_OVERRIDE ||
            !value.isExported()
            || value is IrVariable
            || value is IrTypeParameter
            || value is IrValueParameter
            || value is IrAnonymousInitializerImpl
        ) {

            UniqId(currentIndex++, true)
        } else {
            UniqId(value.uniqIdIndex, false)
        }

        debugIndex.putOnce(index) {
            "${if (index.isLocal) "" else value.uniqSymbolName()} descriptor = ${value.descriptor}"
        }

        index
    }
}

// This is what we pre-populate tables with
val IrBuiltIns.knownBuiltins
    get() = irBuiltInsExternalPackageFragment.declarations