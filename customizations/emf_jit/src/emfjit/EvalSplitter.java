/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.objectweb.asm.ClassReader
 *  org.objectweb.asm.ClassVisitor
 *  org.objectweb.asm.ClassWriter
 *  org.objectweb.asm.tree.AbstractInsnNode
 *  org.objectweb.asm.tree.ClassNode
 *  org.objectweb.asm.tree.InsnList
 *  org.objectweb.asm.tree.InsnNode
 *  org.objectweb.asm.tree.JumpInsnNode
 *  org.objectweb.asm.tree.LabelNode
 *  org.objectweb.asm.tree.LookupSwitchInsnNode
 *  org.objectweb.asm.tree.MethodInsnNode
 *  org.objectweb.asm.tree.MethodNode
 *  org.objectweb.asm.tree.TableSwitchInsnNode
 *  org.objectweb.asm.tree.VarInsnNode
 *  org.objectweb.asm.tree.analysis.Analyzer
 *  org.objectweb.asm.tree.analysis.BasicInterpreter
 *  org.objectweb.asm.tree.analysis.BasicVerifier
 *  org.objectweb.asm.tree.analysis.Frame
 *  org.objectweb.asm.tree.analysis.Interpreter
 */
package emfjit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;

public final class EvalSplitter {
    public static final int HUGE_METHOD_LIMIT = 8000;

    private EvalSplitter() {
    }

    /** Split only at empty-stack array-store boundaries outside every branch span.
     * Adopted maintainable source, recovered from this pack's local addon.
     */
    public static Result split(byte[] original, int budget) {
        try {
            Integer originalSize = codeSizes(original).get("eval([F[Z)V");
            if (originalSize == null) return new Result(original, false, -1, 0, 0, "no eval method");
            if (originalSize < HUGE_METHOD_LIMIT) return new Result(original, false, originalSize, 1, originalSize, "already small");
            ClassNode owner = new ClassNode();
            new ClassReader(original).accept(owner, ClassReader.SKIP_FRAMES);
            MethodNode eval = null;
            for (MethodNode candidate : owner.methods)
                if (candidate.name.equals("eval") && candidate.desc.equals("([F[Z)V") && (candidate.access & 8) != 0) eval = candidate;
            if (eval == null) return new Result(original, false, originalSize, 0, 0, "eval not static");
            if (!eval.tryCatchBlocks.isEmpty()) return new Result(original, false, originalSize, 0, 0, "try/catch present");
            for (MethodNode candidate : owner.methods)
                if (candidate.name.startsWith("eval$")) return new Result(original, false, originalSize, 0, 0, "already split");
            int locals = eval.maxLocals, stack = eval.maxStack;
            AbstractInsnNode[] instructions = eval.instructions.toArray();
            Frame[] frames = new Analyzer(new BasicInterpreter()).analyze(owner.name, eval);
            HashSet<Integer> boundaries = new HashSet<>();
            int previous = -1;
            for (int i = 0; i < instructions.length; i++) {
                if (instructions[i].getOpcode() < 0) continue;
                if (previous >= 0 && (instructions[previous].getOpcode() == 81 || instructions[previous].getOpcode() == 84)
                    && frames[i] != null && frames[i].getStackSize() == 0) boundaries.add(i);
                previous = i;
            }
            IdentityHashMap<AbstractInsnNode,Integer> labels = new IdentityHashMap<>();
            for (int i = 0; i < instructions.length; i++) if (instructions[i] instanceof LabelNode) labels.put(instructions[i], i);
            int[] spans = new int[instructions.length + 2];
            for (int i = 0; i < instructions.length; i++) {
                ArrayList<LabelNode> destinations = new ArrayList<>();
                if (instructions[i] instanceof JumpInsnNode jump) destinations.add(jump.label);
                else if (instructions[i] instanceof TableSwitchInsnNode table) { destinations.add(table.dflt); destinations.addAll(table.labels); }
                else if (instructions[i] instanceof LookupSwitchInsnNode lookup) { destinations.add(lookup.dflt); destinations.addAll(lookup.labels); }
                for (LabelNode destination : destinations) {
                    Integer target = labels.get(destination);
                    if (target == null) return new Result(original, false, originalSize, 0, 0, "unknown label");
                    spans[Math.min(i,target) + 1]++;
                    spans[Math.max(i,target) + 1]--;
                }
            }
            int[] crossing = new int[instructions.length + 1];
            int active = 0;
            for (int i = 0; i <= instructions.length; i++) crossing[i] = active += spans[i];
            int last = instructions.length - 1;
            while (last >= 0 && instructions[last].getOpcode() < 0) last--;
            if (last < 0 || instructions[last].getOpcode() != 177) return new Result(original, false, originalSize, 0, 0, "no trailing return");
            for (int i = 0; i < instructions.length; i++)
                if (i != last && instructions[i].getOpcode() == 177) return new Result(original, false, originalSize, 0, 0, "early return");
            ArrayList<int[]> ranges = new ArrayList<>();
            int start = 0, estimated = 0, safeCut = -1, estimatedAtCut = 0;
            for (int i = 0; i < last; i++) {
                if (boundaries.contains(i) && crossing[i] == 0 && i > start) { safeCut = i; estimatedAtCut = estimated; }
                estimated += estimate(instructions[i]);
                if (estimated > budget && safeCut > start) { ranges.add(new int[]{start,safeCut}); start=safeCut; estimated-=estimatedAtCut; safeCut=-1; estimatedAtCut=0; }
            }
            ranges.add(new int[]{start,last});
            if (ranges.size() < 2) return new Result(original, false, originalSize, 1, originalSize, "no safe cut points");
            ArrayList<MethodNode> chunks = new ArrayList<>();
            InsnList calls = new InsnList();
            for (int i = 0; i < ranges.size(); i++) {
                MethodNode chunk = new MethodNode(4106, "eval$" + i, "([F[Z)V", null, null);
                for (int j = ranges.get(i)[0]; j < ranges.get(i)[1]; j++) { eval.instructions.remove(instructions[j]); chunk.instructions.add(instructions[j]); }
                chunk.instructions.add(new InsnNode(177)); chunk.maxLocals=locals; chunk.maxStack=stack;
                chunks.add(chunk);
                calls.add(new VarInsnNode(25,0)); calls.add(new VarInsnNode(25,1));
                calls.add(new MethodInsnNode(184,owner.name,chunk.name,chunk.desc,(owner.access & 0x200) != 0));
            }
            eval.instructions.clear(); eval.instructions.add(calls); eval.instructions.add(new InsnNode(177));
            eval.localVariables=null; eval.maxLocals=2; eval.maxStack=2; owner.methods.addAll(chunks);
            for (MethodNode candidate : owner.methods)
                if (candidate.instructions.size() > 0) new Analyzer(new BasicVerifier()).analyze(owner.name,candidate);
            ClassWriter writer = new ClassWriter(3) {
                @Override protected String getCommonSuperClass(String a,String b) { return "java/lang/Object"; }
            };
            owner.accept(writer); byte[] transformed=writer.toByteArray();
            int largest=0;
            for (Map.Entry<String,Integer> entry : codeSizes(transformed).entrySet())
                if (entry.getKey().startsWith("eval")) largest=Math.max(largest,entry.getValue());
            if (largest >= HUGE_METHOD_LIMIT) return new Result(original,false,originalSize,ranges.size(),largest,"chunk still huge");
            return new Result(transformed,true,originalSize,ranges.size(),largest,"ok");
        } catch (Throwable failure) {
            return new Result(original,false,-1,0,0,"error: " + failure);
        }
    }

    private static int estimate(AbstractInsnNode abstractInsnNode) {
        switch (abstractInsnNode.getType()) {
            case 8: 
            case 14: 
            case 15: {
                return 0;
            }
            case 0: {
                return 1;
            }
            case 1: {
                return 3;
            }
            case 2: {
                return 4;
            }
            case 9: {
                return 3;
            }
            case 7: {
                return 8;
            }
            case 10: {
                return 6;
            }
            case 5: {
                return 5;
            }
            case 11: {
                return 16 + 4 * ((TableSwitchInsnNode)abstractInsnNode).labels.size();
            }
            case 12: {
                return 12 + 8 * ((LookupSwitchInsnNode)abstractInsnNode).labels.size();
            }
        }
        return 5;
    }

    public static Map<String, Integer> codeSizes(byte[] byArray) {
        int n;
        int n2;
        LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
        ClassReader classReader = new ClassReader(byArray);
        char[] cArray = new char[classReader.getMaxStringLength()];
        int n3 = classReader.header;
        int n4 = classReader.readUnsignedShort(n3 += 6);
        int n5 = classReader.readUnsignedShort(n3 += 2 + 2 * n4);
        n3 += 2;
        for (n2 = 0; n2 < n5; ++n2) {
            n = classReader.readUnsignedShort(n3 += 6);
            n3 += 2;
            for (int i = 0; i < n; ++i) {
                n3 += 2;
                n3 += 4 + classReader.readInt(n3);
            }
        }
        n2 = classReader.readUnsignedShort(n3);
        n3 += 2;
        for (n = 0; n < n2; ++n) {
            String string = classReader.readUTF8(n3 + 2, cArray);
            String string2 = classReader.readUTF8(n3 + 4, cArray);
            int n6 = classReader.readUnsignedShort(n3 += 6);
            n3 += 2;
            for (int i = 0; i < n6; ++i) {
                String string3 = classReader.readUTF8(n3, cArray);
                int n7 = classReader.readInt(n3 + 2);
                if ("Code".equals(string3)) {
                    linkedHashMap.put(string + string2, classReader.readInt(n3 + 6 + 4));
                }
                n3 += 6 + n7;
            }
        }
        return linkedHashMap;
    }

    public record Result(byte[] bytes, boolean split, int originalCodeSize, int chunks, int maxChunkCodeSize, String note) {
    }
}

