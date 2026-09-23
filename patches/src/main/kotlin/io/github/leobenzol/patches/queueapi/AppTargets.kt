package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.instructionsOrNull
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.util.toPublicAccessFlags
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.util.MethodUtil

/** An app method and the opcode that calls it. */
internal class InvokeTarget(reference: MethodReference, val opcode: String) {
    val reference: MethodReference = ImmutableMethodReference.of(reference)

    override fun toString() = "$opcode $reference"
}

/**
 * The app's PlaybackQueueManager and the PlaybackQueue interface it delegates to.
 *
 * Queue sections: 0 is the queue shown in "Up next", 1 is autoplay.
 */
internal class QueueTargets(
    val managerType: String,
    val queueField: FieldReference,
    val size: InvokeTarget,
    val currentIndex: InvokeTarget,
    val item: InvokeTarget,
    val move: InvokeTarget,
    val remove: InvokeTarget,
    val setCurrentIndex: InvokeTarget,
    val itemType: String,
    val itemVideoId: InvokeTarget,
)

/** Title and subtitle getters of the music queue item type. */
internal class MetadataTargets(val type: String, val title: InvokeTarget, val artist: InvokeTarget)

internal fun BytecodePatchContext.findQueueTargets(): QueueTargets {
    val managerClass = QueueManagerFingerprint.originalClassDef

    // PlaybackQueueManager.getCurrentIndex(): return this.queue.getCurrentIndex();
    var queueField: FieldReference? = null
    var currentIndex: InvokeTarget? = null
    for (method in managerClass.methods) {
        if (method.parameterTypes.isNotEmpty() || method.returnType != "I" || isStatic(method)) continue
        val instructions = method.instructionsOrNull?.toList() ?: continue
        if (instructions.size != 4) continue
        val field = (instructions[0].takeIf { it.opcode == Opcode.IGET_OBJECT } as? ReferenceInstruction)
            ?.reference as? FieldReference ?: continue
        val call = instructions[1].takeIf { it.isInvoke() } as? ReferenceInstruction ?: continue
        val reference = call.reference as MethodReference
        if (reference.definingClass != field.type || reference.parameterTypes.isNotEmpty() || reference.returnType != "I") continue
        if (instructions[2].opcode != Opcode.MOVE_RESULT || instructions[3].opcode != Opcode.RETURN) continue
        // The queue interface is the one with a move(fromSection, from, toSection, to) method.
        if (allMethods(field.type).none { it.hasSignature(listOf("I", "I", "I", "I"), "V") }) continue
        queueField = ImmutableFieldReference.of(field)
        currentIndex = InvokeTarget(reference, (call as Instruction).opcode.name)
        break
    }
    if (queueField == null || currentIndex == null) fail("PlaybackQueueManager.getCurrentIndex() in ${managerClass.type}")

    val item = queueMethod(queueField.type, "get(section, index)", listOf("I", "I"), null)
    val itemType = item.reference.returnType
    val videoIds = allMethods(itemType).filter { it.hasSignature(emptyList(), "Ljava/lang/String;") }
    if (videoIds.size != 1) fail("queue item getVideoId() in $itemType (${videoIds.size} candidates)")

    return QueueTargets(
        managerType = managerClass.type,
        queueField = queueField,
        size = queueMethod(queueField.type, "size(section)", listOf("I"), "I"),
        currentIndex = currentIndex,
        item = item,
        move = queueMethod(queueField.type, "move(fromSection, from, toSection, to)", listOf("I", "I", "I", "I"), "V"),
        remove = queueMethod(queueField.type, "remove(section, start, count)", listOf("I", "I", "I"), "V"),
        setCurrentIndex = queueMethod(queueField.type, "setCurrentIndex(index)", listOf("I"), "V"),
        itemType = itemType,
        itemVideoId = invokeTarget(videoIds.single()),
    )
}

/** A PlaybackQueue method with exactly these parameters, and this return type or any object if null. */
internal fun BytecodePatchContext.queueMethod(
    queueType: String,
    description: String,
    parameters: List<String>,
    returnType: String?,
): InvokeTarget {
    val matches = allMethods(queueType).filter {
        it.parameterTypes.map(CharSequence::toString) == parameters &&
            (if (returnType == null) it.returnType.startsWith("L") else it.returnType == returnType)
    }
    if (matches.size != 1) fail("PlaybackQueue.$description in $queueType (${matches.size} candidates)")
    return invokeTarget(matches.single())
}

/**
 * The MediaSession queue builder casts each queue item to the music item type and reads the
 * title, then the subtitle (artist). Null if the shape changed, since titles are optional.
 */
internal fun BytecodePatchContext.findMetadataTargets(): MetadataTargets? {
    val instructions = MediaSessionQueueFingerprint.originalMethodOrNull?.instructionsOrNull?.toList() ?: return null
    for ((index, instruction) in instructions.withIndex()) {
        if (instruction.opcode != Opcode.CHECK_CAST) continue
        val type = ((instruction as ReferenceInstruction).reference as TypeReference).type
        val getters = instructions.drop(index + 1).take(12).filter { candidate ->
            if (!candidate.isInvoke()) return@filter false
            val reference = (candidate as ReferenceInstruction).reference as MethodReference
            reference.definingClass == type && reference.parameterTypes.isEmpty() &&
                reference.returnType == "Ljava/lang/String;"
        }
        if (getters.size >= 2) {
            fun target(getter: Instruction) =
                InvokeTarget((getter as ReferenceInstruction).reference as MethodReference, getter.opcode.name)
            return MetadataTargets(type, target(getters[0]), target(getters[1]))
        }
    }
    return null
}

// region Changing the app and the extension

/**
 * Replaces the body of a static placeholder method in the extension.
 *
 * @param locals Registers used by [smali] besides the parameters.
 */
internal fun MutableClass.replaceBody(name: String, locals: Int, smali: String) {
    val stub = methods.singleOrNull { it.name == name }
        ?: throw PatchException("Queue API: extension method $name not found")
    val parameterRegisters = stub.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 as Int }

    methods.remove(stub)
    methods.add(
        ImmutableMethod(
            stub.definingClass,
            stub.name,
            stub.parameters,
            stub.returnType,
            stub.accessFlags,
            null,
            null,
            MutableMethodImplementation(locals + parameterRegisters),
        ).toMutable().apply { addInstructions(0, smali) },
    )
}

/** The extension lives in another package, so every app class and member it uses must be public. */
internal fun BytecodePatchContext.makePublic(type: String) {
    val classDef = classDefByOrNull(type) ?: return // Framework or library class outside the app.
    if (AccessFlags.PUBLIC.isSet(classDef.accessFlags)) return
    mutableClassDefBy(type).setAccessFlags(classDef.accessFlags.toPublicAccessFlags())
}

internal fun BytecodePatchContext.makePublic(reference: MethodReference) {
    makePublic(reference.definingClass)
    val method = classDefByOrNull(reference.definingClass)?.methods
        ?.firstOrNull { MethodUtil.methodSignaturesMatch(it, reference) } ?: return
    if (AccessFlags.PUBLIC.isSet(method.accessFlags)) return
    // Private methods are called with invoke-direct, so making them public would break those calls.
    if (AccessFlags.PRIVATE.isSet(method.accessFlags)) throw PatchException("Queue API: $reference is private")
    mutableClassDefBy(reference.definingClass).methods
        .first { MethodUtil.methodSignaturesMatch(it, reference) }
        .let { it.setAccessFlags(it.accessFlags.toPublicAccessFlags()) }
}

internal fun BytecodePatchContext.makePublic(reference: FieldReference) {
    makePublic(reference.definingClass)
    val field = classDefByOrNull(reference.definingClass)?.fields
        ?.firstOrNull { it.name == reference.name && it.type == reference.type } ?: return
    if (AccessFlags.PUBLIC.isSet(field.accessFlags)) return
    mutableClassDefBy(reference.definingClass).fields
        .first { it.name == reference.name && it.type == reference.type }
        .let { it.setAccessFlags(it.accessFlags.toPublicAccessFlags()) }
}

/** Passes `this` to the extension at the end of every constructor of [type]. */
internal fun BytecodePatchContext.hookConstructors(type: String, extensionMethod: String) {
    mutableClassDefBy(type).methods.filter { MethodUtil.isConstructor(it) }.forEach { constructor ->
        constructor.instructions.withIndex()
            .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_VOID }
            .map { it.index }
            .reversed()
            .forEach { returnIndex ->
                // Range form, since p0 may be above v15 in large constructors.
                constructor.addInstruction(
                    returnIndex,
                    "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->$extensionMethod(Ljava/lang/Object;)V",
                )
            }
    }
}

// endregion

// region Helpers

internal fun fail(what: String): Nothing = throw PatchException("Queue API: could not find $what")

private fun isStatic(method: Method) = AccessFlags.STATIC.isSet(method.accessFlags)

private fun Instruction.isInvoke() = opcode.name.startsWith("invoke-")

internal fun Method.hasSignature(parameters: List<String>, returnType: String) =
    parameterTypes.map(CharSequence::toString) == parameters && this.returnType == returnType

/** Methods of a class or interface and everything it extends or implements, as far as the app defines them. */
internal fun BytecodePatchContext.allMethods(type: String): List<Method> {
    val result = mutableListOf<Method>()
    val seen = mutableSetOf<String>()
    fun visit(current: String) {
        if (!seen.add(current)) return
        val classDef: ClassDef = classDefByOrNull(current) ?: return
        result += classDef.methods
        classDef.superclass?.let(::visit)
        classDef.interfaces.forEach(::visit)
    }
    visit(type)
    return result
}

/** invoke-interface for interfaces, invoke-virtual otherwise. */
internal fun BytecodePatchContext.invokeTarget(method: Method): InvokeTarget {
    val isInterface = classDefByOrNull(method.definingClass)?.let { AccessFlags.INTERFACE.isSet(it.accessFlags) } == true
    return InvokeTarget(method, if (isInterface) "invoke-interface" else "invoke-virtual")
}

// endregion
