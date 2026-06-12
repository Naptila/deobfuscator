package com.javadeobfuscator.deobfuscator.transformers.special;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = NPManagerTransformerConfig.class)
public class NPManagerTransformer extends Transformer<NPManagerTransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        System.out.println("[Special] [NPManagerTransformer] Starting");

        Map<String, ProxyClass> proxyClasses = new HashMap<>();

        for (ClassNode classNode : classNodes()) {
            if (classNode.fields.size() != 1) continue; // 刚好只有一个字段
            if (classNode.methods.size() < 4) continue; // 4个及以上的方法

            FieldNode encKeyField = classNode.fields.get(0);
            if (!encKeyField.desc.equals("I")) continue; // 必须为int
            int encKey = encKeyField.value == null ? 0 : (int) encKeyField.value;
            String realDecryptor = "", fakeDecryptor = "", hashCodeWrapper = "", guardIntMethod = "";
            int guardInt = 0;

            for (MethodNode method : classNode.methods) {
                switch (method.desc) {
                    case "([SIII)Ljava/lang/String;":
                        realDecryptor = method.name;
                        break;
                    case "(Ljava/lang/String;)Ljava/lang/String;":
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (insn instanceof MethodInsnNode
                                    && ((MethodInsnNode) insn).name.equals("random")) {
                                fakeDecryptor = method.name;
                                break;
                            }
                        }
                        break;
                    case "(Ljava/lang/Object;)I":
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (insn instanceof MethodInsnNode &&
                                    insn.getOpcode() == Opcodes.INVOKEVIRTUAL
                                    && ((MethodInsnNode) insn).name.equals("hashCode")) {
                                hashCodeWrapper = method.name;
                                break;
                            }
                        }
                        break;
                    case "()I":
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (insn.getOpcode() == Opcodes.IXOR && insn.getNext().getOpcode() == Opcodes.IRETURN) {
                                if (!Utils.isInteger(insn.getPrevious()) && !Utils.isInteger(insn.getPrevious().getPrevious())) break;

                                guardIntMethod = method.name;
                                break;
                            }
                        }
                    default:
                        break;
                }
            }

            if (!realDecryptor.isEmpty() && !fakeDecryptor.isEmpty() && !hashCodeWrapper.isEmpty() && !guardIntMethod.isEmpty()) {
                proxyClasses.put(classNode.name, new ProxyClass(encKey, realDecryptor, fakeDecryptor, hashCodeWrapper, guardIntMethod, guardInt));
            }
        }
        System.out.println("[Special] [NPManagerTransformer] found " + proxyClasses.size() + " proxy classes");

        if (getConfig().isNumberObfuscation()) {
            AtomicInteger decNumbers = new AtomicInteger();
            for (ClassNode classNode : classNodes()) {
                for (MethodNode methodNode : classNode.methods) {
                    Map<Integer, ProxyClass> keySlotMap = new HashMap<>();

                    for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                        if (insnNode.getOpcode() == Opcodes.ISTORE) {
                            AbstractInsnNode prev = insnNode.getPrevious();
                            if (prev instanceof FieldInsnNode) {
                                FieldInsnNode getStaticNode = (FieldInsnNode) prev;
                                if (!proxyClasses.containsKey(getStaticNode.owner)) continue;

                                keySlotMap.put(((VarInsnNode) insnNode).var, proxyClasses.get(getStaticNode.owner));
                            }
                            continue;
                        }

                        if (insnNode.getOpcode() == Opcodes.IXOR) {
                            AbstractInsnNode prev1 = insnNode.getPrevious();
                            AbstractInsnNode prev2 = prev1 != null ? prev1.getPrevious() : null;

                            if (prev1 != null && prev2 != null) {
                                AbstractInsnNode loadNode = null, constNode = null;

                                if (prev2.getOpcode() == Opcodes.ILOAD && Utils.isInteger(prev1)) { // ILOAD ^ CONST
                                    loadNode = prev2;
                                    constNode = prev1;
                                } else if (Utils.isInteger(prev2) && prev1.getOpcode() == Opcodes.ILOAD) { // CONST ^ ILOAD
                                    loadNode = prev1;
                                    constNode = prev2;
                                }

                                if (loadNode != null && constNode != null) {
                                    int varIndex = ((VarInsnNode) loadNode).var;
                                    if (keySlotMap.containsKey(varIndex)) {
                                        ProxyClass proxyClass = keySlotMap.get(varIndex);
                                        int decVal = Utils.getIntValue(constNode) ^ proxyClass.encKey;

                                        methodNode.instructions.set(loadNode, Utils.getIntInsn(decVal));
                                        methodNode.instructions.remove(constNode);
                                        methodNode.instructions.remove(insnNode);
                                        decNumbers.incrementAndGet();
                                        continue;
                                    }
                                }
                            }
                        }

                        if (insnNode.getOpcode() == Opcodes.GETSTATIC) {
                            FieldInsnNode ain = (FieldInsnNode) insnNode;
                            if (!ain.desc.equals("I") || !proxyClasses.containsKey(ain.owner)) continue;

                            ProxyClass proxyClass = proxyClasses.get(ain.owner);
                            AbstractInsnNode next1 = ain.getNext();
                            boolean isLong = next1 != null && next1.getOpcode() == Opcodes.I2L;

                            AbstractInsnNode i2lNode = isLong ? next1 : null;
                            AbstractInsnNode afterKey = isLong ? i2lNode.getNext() : next1;
                            int targetXorOpcode = isLong ? Opcodes.LXOR : Opcodes.IXOR;

                            AbstractInsnNode constNode = null;
                            AbstractInsnNode xorNode = null;

                            AbstractInsnNode prev = ain.getPrevious();
                            if (Utils.isInteger(prev) || Utils.isLong(prev)) { // CONST ^ ENC
                                if (afterKey != null && afterKey.getOpcode() == targetXorOpcode) {
                                    constNode = prev;
                                    xorNode = afterKey;
                                }
                            } else if (Utils.isInteger(afterKey) || Utils.isLong(afterKey)) { // ENC ^ CONST
                                if (afterKey.getNext() != null && afterKey.getNext().getOpcode() == targetXorOpcode) {
                                    constNode = afterKey;
                                    xorNode = afterKey.getNext();
                                }
                            }

                            if (constNode != null && xorNode != null) {
                                long constVal = isLong ? Utils.getLongValue(constNode) : Utils.getIntValue(constNode);
                                long decVal = constVal ^ proxyClass.encKey;

                                methodNode.instructions.remove(constNode);
                                methodNode.instructions.remove(xorNode);
                                if (isLong) methodNode.instructions.remove(i2lNode);
                                methodNode.instructions.set(ain, isLong ? Utils.getLongInsn(decVal) : Utils.getIntInsn((int) decVal));

                                decNumbers.incrementAndGet();
                            }
                        }
                    }
                }
            }
            System.out.println("[Special] [NPManagerTransformer] Decrypted " + decNumbers.get() + " numbers");
        }

        // 优先度在数字的后面
        if (getConfig().isStringEncryption()) {
            AtomicInteger encStrings = new AtomicInteger();
            for (ClassNode classNode : classNodes()) {
                MethodNode clinitMethod = TransformerHelper.findClinit(classNode);

                if (clinitMethod == null) continue;

                short[] dict = null;

                for (AbstractInsnNode insn : clinitMethod.instructions.toArray()) {
                    // 太长的话，dex2jar会转换成string
                    if (insn instanceof LdcInsnNode) {
                        Object cst = ((LdcInsnNode) insn).cst;
                        if (cst instanceof String) {
                            String hexStr = (String) cst;

                            if (!hexStr.isEmpty() && hexStr.length() % 4 == 0) {
                                AbstractInsnNode nextInsn = insn.getNext();
                                while ((nextInsn instanceof LabelNode ||
                                        nextInsn instanceof LineNumberNode ||
                                        nextInsn instanceof FrameNode)) {
                                    nextInsn = nextInsn.getNext();
                                }

                                if (nextInsn instanceof MethodInsnNode) {
                                    MethodInsnNode nextMethod = (MethodInsnNode) nextInsn;
                                    if (nextMethod.name.contains("$d2j$hex$")) {
                                        dict = decodeDex2JarString(hexStr);
                                        break;
                                    }
                                }
                            }
                        }
                    } else if (insn.getOpcode() == Opcodes.NEWARRAY) {
                        IntInsnNode newArrayNode = (IntInsnNode) insn;

                        if (newArrayNode.operand == Opcodes.T_SHORT) {
                            // 获取数组长度
                            AbstractInsnNode sizeNode = Utils.getPrevious(insn);
                            int size = Utils.getIntValue(sizeNode);
                            dict = new short[size];

                            AbstractInsnNode current = Utils.getNext(insn);
                            while (current != null) {
                                if (current.getOpcode() == Opcodes.SASTORE) {
                                    AbstractInsnNode valueNode = Utils.getPrevious(current); // 值
                                    AbstractInsnNode indexNode = Utils.getPrevious(valueNode); // 下标

                                    int value = Utils.getIntValue(valueNode);
                                    int index = Utils.getIntValue(indexNode);

                                    dict[index] = (short) value;
                                } else if (current.getOpcode() == Opcodes.PUTSTATIC) {
                                    break;
                                }
                                current = Utils.getNext(current);
                            }
                            break;
                        }
                    }
                }

                if (dict == null) continue;

                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode ain = (MethodInsnNode) insn;

                            if (!ain.desc.equals("([SIII)Ljava/lang/String;")
                                    || !proxyClasses.containsKey(ain.owner)
                                    || !proxyClasses.get(ain.owner).realDecryptor.equals(ain.name)) continue;

                            if (!Utils.isInteger(ain.getPrevious())
                                    || !Utils.isInteger(ain.getPrevious().getPrevious())
                                    || !Utils.isInteger(ain.getPrevious().getPrevious().getPrevious()))
                                continue;

                            int n3 = Utils.getIntValue(ain.getPrevious());
                            int n2 = Utils.getIntValue(ain.getPrevious().getPrevious());
                            int n = Utils.getIntValue(ain.getPrevious().getPrevious().getPrevious());

                            String plainText = decryptString(dict, n, n2, n3);

                            method.instructions.remove(ain.getPrevious().getPrevious().getPrevious().getPrevious());
                            method.instructions.remove(ain.getPrevious().getPrevious().getPrevious());
                            method.instructions.remove(ain.getPrevious().getPrevious());
                            method.instructions.remove(ain.getPrevious());

                            method.instructions.set(ain, new LdcInsnNode(plainText));

                            encStrings.incrementAndGet();
                        }
                    }
                }
            }
            System.out.println("[Special] [NPManagerTransformer] Decrypted " + encStrings.get() + " strings");
        }

        return true;
    }

    private String decryptString(final short[] array, final int n, final int n2, final int n3) {
        final char[] value = new char[n2];
        for (int i = 0; i < n2; ++i) {
            value[i] = (char) (array[n + i] ^ n3);
        }
        return new String(value);
    }

    private short[] decodeDex2JarString(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        ShortBuffer shortBuffer = buffer.asShortBuffer();
        short[] result = new short[data.length / 2];
        shortBuffer.get(result);
        return result;
    }

    // 代理类
    private class ProxyClass {
        private final int encKey;
        private final String realDecryptor;
        private final String fakeDecryptor;
        private final String hashCodeWrapper;
        private final String guardIntMethod;
        private final int guardInt;

        public ProxyClass(int encKey, String realDecryptor, String fakeDecryptor, String hashCodeWrapper, String guardIntMethod, int guardInt) {
            this.encKey = encKey;
            this.realDecryptor = realDecryptor;
            this.fakeDecryptor = fakeDecryptor;
            this.hashCodeWrapper = hashCodeWrapper;
            this.guardIntMethod = guardIntMethod;
            this.guardInt = guardInt;
        }

        public int getEncKey() {
            return encKey;
        }

        public String getRealDecryptor() {
            return realDecryptor;
        }

        public String getFakeDecryptor() {
            return fakeDecryptor;
        }

        public String getHashCodeWrapper() {
            return hashCodeWrapper;
        }

        public String getGuardIntMethod() {
            return guardIntMethod;
        }

        public int getGuardInt() {
            return guardInt;
        }
    }
}
