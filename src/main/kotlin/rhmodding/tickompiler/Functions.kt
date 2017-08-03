package rhmodding.tickompiler

import rhmodding.tickompiler.compiler.FunctionCall
import rhmodding.tickompiler.decompiler.DecompilerState
import java.util.*

abstract class Functions {

    val opcode = OpcodeFunction()
    val bytecode = BytecodeFunction()
    abstract val allFunctions: MutableList<Function>

    val byName: MutableMap<String, Function>
        get() = allFunctions.associate { it.name to it }.toMutableMap()

    operator fun get(op: Long): Function {
        allFunctions.forEach { if (it.acceptOp(op)) return@get it }
        return opcode
    }

    operator fun set(op: Long, alias: String) {
        val f = op alias alias
        allFunctions.add(f)
    }

    private fun isNumeric(input: String): Boolean {
        return input.toIntOrNull() != null
    }

    operator fun get(key: String): Function {
        if (key.startsWith("0x") or isNumeric(key)) {
            return opcode
        } else {
            return byName[key] ?: throw MissingFunctionError("Failed to find function $key")
        }
    }

    protected fun alias(opcode: Long, alias: String, argsNeeded: IntRange, indentChange: Int = 0,
                        currentAdjust: Int = 0): AliasedFunction {
        return AliasedFunction(opcode, alias, argsNeeded, indentChange, currentAdjust)
    }

    /**
     * Assumes 0..0b1111 for args needed.
     */
    protected infix fun Long.alias(alias: String): AliasedFunction {
        return alias(this, alias, 0..0b1111)
    }


}

fun createInts(opcode: Long, special: Long, args: LongArray?): LongArray {
    if (args == null)
        return longArrayOf(opcode)

    if (args.size > 0b1111)
        throw IllegalArgumentException("Args size cannot be more than ${0b1111}")
    val firstLong: Long = opcode or ((args.size.toLong() and 0b1111) shl 10) or ((special and 0b111111111111111111) shl 14)

    return longArrayOf(firstLong, *args)
}

object MegamixFunctions : Functions() {
    override val allFunctions = mutableListOf(opcode,
                                              bytecode,
                                              RestFunction(0xE),
                                              SpecialOnlyFunction(0x14, "label"),
                                              SpecialOnlyFunction(0x15, "goto"),
                                              SpecialOnlyFunction(0x1A, "case"),
                                              SpecialOnlyFunction(0xB8, "random"),
                                              SpecificSpecialFunction(0x1, 0, "get_async", 2..2),
                                              SpecificSpecialFunction(0x1, 1, "set_async", 2..2),
                                              SpecificSpecialFunction(0xF, 0, "getrest", 1..1),
                                              SpecificSpecialFunction(0xF, 1, "setrest", 2..2),
                                              SpecificSpecialFunction(0x2A, 0, "game_model",
                                                                                            2..2),
                                              SpecificSpecialFunction(0x2A, 2, "game_cellanim",
                                                                                            2..2),
                                              SpecificSpecialFunction(0x2A, 3, "game_effect",
                                                                                            2..2),
                                              SpecificSpecialFunction(0x2A, 4, "game_layout",
                                                                                            2..2),
                                              SpecificSpecialFunction(0x31, 0, "set_model", 3..3),
                                              SpecificSpecialFunction(0x31, 1, "remove_model",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x31, 2, "has_model", 1..1),
                                              SpecificSpecialFunction(0x35, 0, "set_cellanim",
                                                                                            3..3),
                                              SpecificSpecialFunction(0x35, 1, "cellanim_busy",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x35, 3, "remove_cellanim",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x39, 0, "set_effect",
                                                                                            3..3),
                                              SpecificSpecialFunction(0x39, 1, "effect_busy",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x39, 7, "remove_effect",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x3E, 0, "set_layout",
                                                                                            3..3),
                                              SpecificSpecialFunction(0x3E, 1, "layout_busy",
                                                                                            1..1),
                                              SpecificSpecialFunction(0x3E, 7, "remove_layout",
                                                                                            1..1),
                                              MacroFunction("async_sub"),
                                              MacroFunction("macro"),
                                              alias(0x2, "async_call", 2..2),
                                              alias(0x4, "sub", 1..1),
                                              alias(0x6, "call", 1..1),
                                              alias(0x7, "return", 0..0),
                                              alias(0x8, "stop", 0..0),
                                              alias(0x16, "if", 1..1, 1),
                                              alias(0x17, "else", 0..0, 0,
                                                    -1), // current adjust pushes the else back an indent
                                              alias(0x18, "endif", 0..0, -1, -1), // same here
                                              alias(0x19, "switch", 0..0, 1),
                                              alias(0x1B, "break", 0..0),
                                              alias(0x1C, "default", 0..0),
                                              alias(0x1D, "endswitch", 0..0, -1, -1),
                                              alias(0x24, "speed", 1..1),
                                              alias(0x28, "engine", 1..1),
                                              alias(0x40, "play_sfx", 1..1),
                                              alias(0x5D, "set_sfx", 2..2),
                                              alias(0x5F, "remove_sfx", 1..1),
                                              alias(0x6A, "input", 1..1),
                                              alias(0xAE, "star", 1..1),
                                              0x7DL alias "fade"
                                             )
}

object DSFunctions : Functions() {
    override val allFunctions = mutableListOf<Function>(
            RestFunction(1)
                                                                             )
}

abstract class Function(val opCode: Long, val name: String, val argsNeeded: IntRange) {

    open fun acceptOp(op: Long): Boolean {
        val opcode = op and 0x3FF
        val args = (op and 0x3C00) ushr 10
        return opcode == opCode && args in argsNeeded
    }

    fun checkArgsNeeded(functionCall: FunctionCall) {
        val args = functionCall.args.size.toLong()
        if (args !in argsNeeded) {
            throw WrongArgumentsError(args, argsNeeded,
                                                            "function " + functionCall.func + "<" + functionCall.specialArg + ">, got " + functionCall.args)
        }
    }

    abstract fun produceBytecode(funcCall: FunctionCall): LongArray

    abstract fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String

    fun argsToTickflowArgs(args: LongArray, specialArgStrings: Map<Int, String>, radix: Int = 16): String {
        return args.mapIndexed { index, it ->
            if (specialArgStrings.containsKey(index)) {
                specialArgStrings[index]
            } else {
                if (radix == 16) getHex(it) else
                    Integer.toString(it.toInt(), radix).toString()
            }
        }.joinToString(separator = ", ")
    }

    fun addSpecialArg(specialArg: Long): String {
        return (if (specialArg != 0L) "<${getHex(specialArg)}>" else "")
    }

    fun getHex(num: Long): String {
        return if (Math.abs(num.toInt()) < 10)
            Integer.toString(num.toInt(), 16).toString().toUpperCase(Locale.ROOT)
        else
            (if (num.toInt() < 0) "-" else "") + "0x" + Integer.toString(Math.abs(num.toInt()),
                                                                         16).toString().toUpperCase(Locale.ROOT)
    }

}

class BytecodeFunction : Function(-1, "bytecode", 1..1) {
    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        throw NotImplementedError()
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        return longArrayOf(funcCall.args.first())
    }

}

class OpcodeFunction : Function(-1, "opcode", 0..Integer.MAX_VALUE) {
    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        return getHex(opcode) +
                addSpecialArg(specialArg) +
                " " + argsToTickflowArgs(args, specialArgStrings)
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        val opcode = if (funcCall.func.startsWith("0x"))
            funcCall.func.substring(2).toLong(16)
        else
            funcCall.func.toLong()

        return createInts(opcode, funcCall.specialArg, funcCall.args.toLongArray())
    }

}

class MacroFunction(alias: String) : AliasedFunction(0x0, alias, 1..3) {

    override fun acceptOp(op: Long): Boolean {
        val opcode = op and 0x3FF
        val args = (op and 0x3C00) ushr 10
        return opcode == opCode && args == 3L
    }

    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        val newArgs = args.toMutableList()
        if (newArgs.size > 2 && newArgs[2] == 0x7D0L) {
            newArgs.removeAt(2)
            if (newArgs[1] == 0L) {
                newArgs.removeAt(1)
            }
        }
        return super.produceTickflow(state, opcode, specialArg, newArgs.toLongArray(), comments, specialArgStrings)
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        return super.produceBytecode(
                FunctionCall(funcCall.func, funcCall.specialArg, listOf(
                        funcCall.args.first(),
                        if (funcCall.args.size > 1) funcCall.args[1] else 0x0,
                        if (funcCall.args.size > 2) funcCall.args[2] else 0x7D0)))
    }

}

open class SpecialOnlyFunction(opcode: Long, alias: String) : Function(opcode, alias, 1..1) {
    override fun acceptOp(op: Long): Boolean {
        val opcode = op and 0x3FF
        val args = (op and 0x3C00) ushr 10
        return opcode == opCode && args == 0L
    }

    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        return "${this.name} 0x${Integer.toUnsignedString(specialArg.toInt(), 16)}"
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        val spec: Long = funcCall.args.first()
        if (spec !in 0..0b111111111111111111)
            throw IllegalArgumentException(
                    "Special argument out of range: got $spec, needs to be ${0..0b111111111111111111}")

        return longArrayOf((this.opCode or (spec shl 14)))
    }
}

open class SpecificSpecialFunction(opcode: Long, val special: Long, alias: String,
                                   argsNeeded: IntRange = 0..0b1111) : AliasedFunction(opcode, alias, argsNeeded) {
    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        return super.produceTickflow(state, opcode, 0, args, comments, specialArgStrings)
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        return createInts(opCode, special, funcCall.args.toLongArray())
    }

    override fun acceptOp(op: Long): Boolean {
        val opcode = op and 0x3FF
        val args = (op and 0x3C00) ushr 10
        val special = op ushr 14
        return opcode == opCode && special == this.special && args in argsNeeded
    }

}

open class RestFunction(opcode: Long) : SpecialOnlyFunction(opcode, "rest") {
    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        return "rest " + getHex(specialArg) + if (comments) "\t// ${specialArg / 48f} beats" else ""
    }
}

open class AliasedFunction(opcode: Long, alias: String, argsNeeded: IntRange, val indentChange: Int = 0,
                           val currentAdjust: Int = 0) : Function(opcode, alias, argsNeeded) {
    override fun produceTickflow(state: DecompilerState, opcode: Long, specialArg: Long, args: LongArray,
                                 comments: Boolean, specialArgStrings: Map<Int, String>): String {
        state.nextIndentLevel += indentChange
        state.currentAdjust = currentAdjust
        return this.name + addSpecialArg(specialArg) + " " + argsToTickflowArgs(args, specialArgStrings)
    }

    override fun produceBytecode(funcCall: FunctionCall): LongArray {
        return createInts(this.opCode, funcCall.specialArg, funcCall.args.toLongArray())
    }
}